(ns cljfmt.main
  "Functionality to apply formatting to a given project."
  (:require [cljfmt.core :as cljfmt]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.tools.cli :as cli]
            [cljfmt.diff :as diff])
  (:gen-class))

(defn- abort [& msg]
  (binding [*out* *err*]
    (when (seq msg)
      (apply println msg))
    (System/exit 1)))

(defn- warn [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn- relative-path [^java.io.File dir ^java.io.File file]
  (-> (.toAbsolutePath (.toPath dir))
      (.relativize (.toAbsolutePath (.toPath file)))
      (.toString)))

(defn- filename-ext [filename]
  (subs filename (inc (str/last-index-of filename "."))))

(defn- grep [re dir]
  (filter #(re-find re (relative-path dir %)) (file-seq (io/file dir))))

(defn- find-files [{:keys [file-pattern]} f]
  (let [f (io/file f)]
    (when-not (.exists f) (abort "No such file:" (str f)))
    (if (.isDirectory f)
      (grep file-pattern f)
      [f])))

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
  (binding [*out* *err*]
    (st/print-stack-trace ex)))

(defn- print-file-status [options status]
  (let [path (project-path options (:file status))]
    (when-let [ex (:exception status)]
      (warn "Failed to format file:" path)
      (print-stack-trace ex))
    (when (:reformatted status)
      (println "Reformatting" path))
    (when-let [diff (:diff status)]
      (warn path "has incorrect formatting")
      (warn diff))))

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
      (println "All source files formatted correctly"))))

(defn- merge-counts
  ([]    zero-counts)
  ([a]   a)
  ([a b] (merge-with + a b)))

(defn check
  "Checks that the Clojure files contained in `paths` follow the formatting
  (as per `options`)."
  ([paths]
   (check paths {}))
  ([paths options]
   (let [map*   (if (:parallel? options) pmap map)
         counts (->> paths
                     (mapcat (partial find-files options))
                     (map* (partial check-one options))
                     (map (fn [status]
                            (print-file-status options status)
                            (:counts status)))
                     (reduce merge-counts))]
     (print-final-count counts)
     (exit counts))))

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

(defn fix
  "Applies the formatting (as per `options`) to the Clojure files
  contained in `paths`."
  ([paths]
   (fix paths {}))
  ([paths options]
   (let [map* (if (:parallel? options) pmap map)]
     (->> paths
          (mapcat (partial find-files options))
          (map* (partial fix-one options))
          (map (partial print-file-status options))
          dorun))))

(defn- cli-file-reader [filepath]
  (let [contents (slurp filepath)]
    (if (= (filename-ext filepath) "clj")
      (read-string contents)
      (edn/read-string contents))))

(def default-options
  (merge cljfmt/default-options
         {:project-root "."
          :file-pattern #"\.clj[csx]?$"
          :ansi?        true
          :parallel?    false}))

(defn merge-default-options [options]
  (-> (merge default-options options)
      (assoc :indents (merge (:indents default-options)
                             (:indents options {})))))

(def default-paths ["src" "test" "project.clj"])

(def ^:private cli-options
  [[nil "--help"]
   [nil "--parallel"
    :id :parallel?]
   [nil "--file-pattern FILE_PATTERN"
    :default (:file-pattern default-options)
    :parse-fn re-pattern]
   [nil "--indents INDENTS_PATH"
    :parse-fn cli-file-reader]
   [nil "--alias-map ALIAS_MAP_PATH"
    :parse-fn cli-file-reader]
   [nil "--project-root PROJECT_ROOT"
    :default (:project-root default-options)]
   [nil "--[no-]ansi"
    :default (:ansi? default-options)
    :id :ansi?]
   [nil "--[no-]indentation"
    :default (:indentation? cljfmt/default-options)
    :id :indentation?]
   [nil "--[no-]remove-multiple-non-indenting-spaces"
    :default (:remove-multiple-non-indenting-spaces? cljfmt/default-options)
    :id :remove-multiple-non-indenting-spaces?]
   [nil "--[no-]remove-surrounding-whitespace"
    :default (:remove-surrounding-whitespace? cljfmt/default-options)
    :id :remove-surrounding-whitespace?]
   [nil "--[no-]remove-trailing-whitespace"
    :default (:remove-trailing-whitespace? cljfmt/default-options)
    :id :remove-trailing-whitespace?]
   [nil "--[no-]insert-missing-whitespace"
    :default (:insert-missing-whitespace? cljfmt/default-options)
    :id :insert-missing-whitespace?]
   [nil "--[no-]remove-consecutive-blank-lines"
    :default (:remove-consecutive-blank-lines? cljfmt/default-options)
    :id :remove-consecutive-blank-lines?]
   [nil "--[no-]split-keypairs-over-multiple-lines"
    :default (:split-keypairs-over-multiple-lines? cljfmt/default-options)
    :id :split-keypairs-over-multiple-lines?]
   [nil "--[no-]sort-ns-references"
    :default (:sort-ns-references? cljfmt/default-options)
    :id :sort-ns-references?]])

(defn- file-exists? [path]
  (.exists (io/as-file path)))

(defn -main [& args]
  (let [parsed-opts   (cli/parse-opts args cli-options)
        [cmd & paths] (:arguments parsed-opts)
        options       (merge-default-options (:options parsed-opts))
        paths         (or (seq paths) (filter file-exists? default-paths))]
    (if (:errors parsed-opts)
      (abort (:errors parsed-opts))
      (if (or (nil? cmd) (:help options))
        (do (println "cljfmt [OPTIONS] COMMAND [PATHS ...]")
            (println (:summary parsed-opts)))
        (do (case cmd
              "check" (check paths options)
              "fix"   (fix paths options)
              (abort "Unknown cljfmt command:" cmd))
            (when (:parallel? options)
              (shutdown-agents)))))))
