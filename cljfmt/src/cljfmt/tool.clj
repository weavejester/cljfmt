(ns cljfmt.tool
  (:require [cljfmt.config :as config]
            [cljfmt.core :as cljfmt]
            [cljfmt.diff :as diff]
            [cljfmt.io :as io]
            [clojure.java.io :as cio]
            [clojure.stacktrace :as st])
  (:import (java.io File)))

(def ^:dynamic *no-output* false)
(def ^:dynamic *verbose* false)

(defn- warn [& args]
  (when-not *no-output*
    (binding [*out* *err*]
      (apply println args))))

(defn- trace [& args]
  (when *verbose* (apply warn args)))

(defn- grep [re dir]
  (filter #(re-find re (io/relative-path % dir)) (io/list-files dir)))

(defn- find-files [{:keys [file-pattern]} f]
  (let [f (io/file-entity f)]
    (when (io/exists? f)
      (if (io/directory? f)
        (grep file-pattern f)
        (list f)))))

(defn- reformat-string [options s]
  ((cljfmt/wrap-normalize-newlines #(cljfmt/reformat-string % options)) s))

(defn- project-path [{:keys [project-root]} file]
  (->> (or project-root ".") cio/file (io/relative-path file)))

(defn- format-diff
  ([options file]
   (let [original (io/read-file file)]
     (format-diff options file original (reformat-string options original))))
  ([options file original revised]
   (let [filename (project-path options file)
         diff     (diff/unified-diff filename original revised)]
     (if (:ansi? options)
       (diff/colorize-diff diff)
       diff))))

(def ^:private zero-counts {:okay 0, :incorrect 0, :error 0})

(defn- check-one [options file]
  (trace "Processing file:" (project-path options file))
  (let [original (io/read-file file)
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
      (warn "Reformatted" path))
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
      (warn "All source files formatted correctly"))))

(defn- merge-counts
  ([]    zero-counts)
  ([a]   a)
  ([a b] (merge-with + a b)))

(defn- merge-statuses
  [a b]
  (let [^File file (:file b)
        path       (.getPath file)
        diff       (:diff b)
        exception  (:exception b)]
    (cond-> a
            true (update :counts #(merge-with + % (:counts b)))

            (and file (= 1 (-> b :counts :incorrect)))
            (assoc-in [:incorrect path] "has incorrect formatting")

            (and file diff) (assoc-in [:incorrect path] diff)
            (and file exception) (assoc-in [:error path] exception))))

(defmulti check-report-file (fn [& args] (first args)))
(defmulti check-report-final (fn [& args] (first args)))

(defmethod check-report-file :cli
  [_ options status]
  (print-file-status options status)
  (:counts status))

(defmethod check-report-final :cli
  [_ results]
  (let [counts (reduce merge-counts results)]
    (print-final-count counts)
    (exit counts)))

(defmethod check-report-file :lib
  [_ _ status]
  status)

(defmethod check-report-final :lib
  [_ results]
  (reduce merge-statuses {} results))

(defn check-no-config
  "The same as `check`, but ignores dotfile configuration."
  [{:keys [parallel? paths report/file report/final] :as options}]
  (let [map*        (if parallel? pmap map)
        report-file (partial file options)
        results     (->> paths
                         (mapcat (partial find-files options))
                         (map* (partial check-one options))
                         (map report-file))]
    (final results)))

(defn check
  "Checks that the Clojure paths specified by the :paths option are
  correctly formatted."
  [options]
  (let [opts (merge {:report/file  #(check-report-file :cli %1 %2)
                     :report/final #(check-report-final :cli %)}
                    (config/load-config) options)]
    (check-no-config opts)))

(defn check-paths
  "Runs a check on the provided paths and returns a map detailing the results."
  [paths & [options]]
  (let [opts (merge {:report/file  #(check-report-file :lib %1 %2)
                     :report/final #(check-report-final :lib %)}
                    (config/load-config) options
                    {:paths paths})]
    (check-no-config opts)))

(defn- fix-one [options file]
  (trace "Processing file:" (project-path options file))
  (let [original (io/read-file file)]
    (try
      (let [revised  (reformat-string options original)
            changed? (not= original revised)]
        (io/update-file file revised changed?)
        (if changed?
          {:file file :reformatted true}
          {:file file}))
      (catch Exception e
        {:file file :exception e}))))

(defn- recursively-find-files [{:keys [paths] :as options}]
  (mapcat #(find-files options %) (set paths)))

(defmulti fix-report (fn [& args] (first args)))

(defmethod fix-report :cli
  [_ options status]
  (print-file-status options status))

(defmethod fix-report :lib
  [_ _ status]
  status)

(defn fix-no-config
  "The same as `fix`, but ignores dotfile configuration."
  [{:keys [parallel? report/fix] :as options}]
  (let [files (recursively-find-files options)
        map*  (if parallel? pmap map)
        fix-report (partial fix options)]
    (->> files
         (map* (partial fix-one options))
         (map fix-report)
         dorun)))

(defn fix
  "Fixes the formatting for all files specified by the :paths option."
  [options]
  (let [opts (merge {:report/fix #(fix-report :cli %1 %2)}
                    (config/load-config) options)]
    (fix-no-config opts)))

(defn fix-paths
  "Fixes code in the provided paths."
  [paths & [options]]
  (let [opts (merge {:report/fix #(fix-report :lib %1 %2)}
                    (config/load-config) options
                    {:paths paths})]
    (fix-no-config opts)))
