(ns cljfmt.main
  "Functionality to apply formatting to a given project."
  (:require [cljfmt.core :as cljfmt]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cljfmt.impl.stacktrace :as st]
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
   (let [counts (transduce
                 (comp (mapcat (partial find-files options))
                       (map (partial check-one options))
                       (map (fn [status]
                              (print-file-status options status)
                              (:counts status))))
                 (completing merge-counts)
                 paths)]
     (print-final-count counts)
     (exit counts))))

(defn fix
  "Applies the formatting (as per `options`) to the Clojure files
  contained in `paths`."
  ([paths]
   (fix paths {}))
  ([paths options]
   (let [files (mapcat (partial find-files options) paths)]
     (doseq [^java.io.File f files]
       (let [original (slurp f)]
         (try
           (let [revised (reformat-string options original)]
             (when (not= original revised)
               (println "Reformatting" (project-path options f))
               (spit f revised)))
           (catch Exception e
             (warn "Failed to format file:" (project-path options f))
             (print-stack-trace e))))))))

(defn- cli-file-reader [filepath]
  (let [contents (slurp filepath)]
    (if (= (filename-ext filepath) "clj")
      (read-string contents)
      (edn/read-string contents))))

(def default-options
  {:project-root "."
   :file-pattern #"\.clj[csx]?$"
   :ansi?        true
   :indentation? true
   :insert-missing-whitespace?            true
   :remove-multiple-non-indenting-spaces? false
   :remove-surrounding-whitespace?        true
   :remove-trailing-whitespace?           true
   :remove-consecutive-blank-lines?       true
   :indents   cljfmt/default-indents
   :alias-map {}})

(defn merge-default-options [options]
  (-> (merge default-options options)
      (assoc :indents (merge (:indents default-options)
                             (:indents options {})))))

(def default-paths ["src" "test" "project.clj"])

(def ^:private cli-options
  [[nil "--help"]
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
    :default (:indentation? default-options)
    :id :indentation?]
   [nil "--[no-]remove-multiple-non-indenting-spaces"
    :default (:remove-multiple-non-indenting-spaces? default-options)
    :id :remove-multiple-non-indenting-spaces?]
   [nil "--[no-]remove-surrounding-whitespace"
    :default (:remove-surrounding-whitespace? default-options)
    :id :remove-surrounding-whitespace?]
   [nil "--[no-]remove-trailing-whitespace"
    :default (:remove-trailing-whitespace? default-options)
    :id :remove-trailing-whitespace?]
   [nil "--[no-]insert-missing-whitespace"
    :default (:insert-missing-whitespace? default-options)
    :id :insert-missing-whitespace?]
   [nil "--[no-]remove-consecutive-blank-lines"
    :default (:remove-consecutive-blank-lines? default-options)
    :id :remove-consecutive-blank-lines?]])

(defn- command-name []
  (or (System/getProperty "sun.java.command") "cljfmt"))

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
        (case cmd
          "check" (check paths options)
          "fix"   (fix paths options)
          (abort "Unknown cljfmt command:" cmd))))))
