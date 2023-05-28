(ns cljfmt.tool
  (:require [cljfmt.config :as config]
            [cljfmt.core :as cljfmt]
            [cljfmt.diff :as diff]
            [clojure.java.io :as io]
            [clojure.stacktrace :as st]))

(def ^:dynamic *no-output* false)

(defn- warn [& args]
  (when-not *no-output*
    (binding [*out* *err*]
      (apply println args))))

(defn- info [& args]
  (when-not *no-output*
    (apply println args)))

(defn- relative-path [^java.io.File dir ^java.io.File file]
  (-> (.toAbsolutePath (.toPath dir))
      (.relativize (.toAbsolutePath (.toPath file)))
      (.toString)))

(defn- grep [re dir]
  (filter #(re-find re (relative-path dir %)) (file-seq (io/file dir))))

(defn- find-files [{:keys [file-pattern]} f]
  (let [f (io/file f)]
    (when (.exists f)
      (if (.isDirectory f)
        (grep file-pattern f)
        [f]))))

(defn- reformat-string [options s]
  ((cljfmt/wrap-normalize-newlines #(cljfmt/reformat-string % options)) s))

(defn- project-path [{:keys [project-root]} file]
  (-> project-root (or ".") io/file (relative-path (io/file file))))

(defn- format-diff
  ([options file]
   (let [original (slurp (io/file file))]
     (format-diff options file original (reformat-string options original))))
  ([options file original revised]
   (let [filename (project-path options file)
         diff     (diff/unified-diff filename original revised)]
     (if (:ansi? options)
       (diff/colorize-diff diff)
       diff))))

(def ^:private zero-counts {:okay 0, :incorrect 0, :error 0})

(defn- check-one [options file]
  (let [original (slurp file)
        status   {:counts zero-counts :file file}]
    (try
      (let [revised (reformat-string options original)]
        (if (not= original revised)
          (-> status
              (assoc-in [:counts :incorrect] 1)
              (assoc :diff (format-diff options file original revised)))
          (assoc-in status [:counts :okay] 1)))
      (catch Exception ex
        (-> status
            (assoc-in [:counts :error] 1)
            (assoc :exception ex))))))

(defn- print-stack-trace [ex]
  (when-not *no-output*
    (binding [*out* *err*]
      (st/print-stack-trace ex))))

(defn- print-file-status [options status]
  (let [path (project-path options (:file status))]
    (when-let [ex (:exception status)]
      (warn "Failed to format file:" path)
      (print-stack-trace ex))
    (when (:reformatted status)
      (info "Reformatting" path))
    (when-let [diff (:diff status)]
      (warn path "has incorrect formatting")
      (println diff))))

(defn- exit [counts]
  (when-not (zero? (:error counts 0))
    (System/exit 2))
  (when-not (zero? (:incorrect counts 0))
    (System/exit 1)))

(defn- print-final-count [counts]
  (let [error     (:error counts 0)
        incorrect (:incorrect counts 0)]
    (when-not (zero? error)
      (warn error "file(s) could not be parsed for formatting"))
    (when-not (zero? incorrect)
      (warn incorrect "file(s) formatted incorrectly"))
    (when (and (zero? incorrect) (zero? error))
      (info "All source files formatted correctly"))))

(defn- merge-counts
  ([]    zero-counts)
  ([a]   a)
  ([a b] (merge-with + a b)))

(defn check-no-config
  "The same as `check`, but ignores dotfile configuration."
  [options]
  (let [map*   (if (:parallel? options) pmap map)
        counts (->> (:paths options)
                    (mapcat (partial find-files options))
                    (map* (partial check-one options))
                    (map (fn [status]
                           (print-file-status options status)
                           (:counts status)))
                    (reduce merge-counts))]
    (print-final-count counts)
    (exit counts)))

(defn check
  "Checks that the Clojure paths specified by the :paths option are
  correctly formatted."
  [options]
  (let [opts (config/merge-configs (config/load-config) options)]
    (check-no-config opts)))

(defn- fix-one [options file]
  (let [original (slurp file)]
    (try
      (let [revised (reformat-string options original)]
        (if (not= original revised)
          (do (spit file revised)
              {:file file :reformatted true})
          {:file file}))
      (catch Exception e
        {:file file :exception e}))))

(defn fix-no-config
  "The same as `fix`, but ignores dotfile configuration."
  [options]
  (let [map* (if (:parallel? options) pmap map)]
    (->> (:paths options)
         (mapcat (partial find-files options))
         (map* (partial fix-one options))
         (map (partial print-file-status options))
         dorun)))

(defn fix
  "Fixes the formatting for all files specified by the :paths option."
  [options]
  (let [opts (config/merge-configs (config/load-config) options)]
    (fix-no-config opts)))
