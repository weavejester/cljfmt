(ns cljfmt.main
  "Functionality to apply formatting to a given project."
  (:gen-class)
  (:require [cljfmt.core :as cljfmt]
            [cljfmt.diff :as diff]
            [cljfmt.files :as files]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(defn- warn [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn- format-diff
  [options project-filename original revised]
  (let [diff (diff/unified-diff project-filename original revised)]
    (if (:ansi? options)
      (diff/colorize-diff diff)
      diff)))

(def ^:private zero-counts {:okay 0, :incorrect 0, :error 0})

(defn- check-one [options {:keys [file project-filename original revised error], :as info}]
  (let [info (assoc info :counts zero-counts)]
    (cond
      error
      (assoc-in info [:counts :error] 1)

      (= original revised)
      (assoc-in info [:counts :okay] 1)

      :else
      (-> info
          (assoc-in [:counts :incorrect] 1)
          (assoc :diff (format-diff options project-filename original revised))))))

(defn- print-file-status [project-filename {:keys [error project-filename diff]}]
  (when error
    (warn "Failed to format file:" project-filename)
    (pprint/pprint (Throwable->map error)))
  (when diff
    (warn project-filename "has incorrect formatting")
    (warn diff)))

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
                 (comp
                  (map (partial check-one options))
                  (map (fn [{:keys [counts], :as info}]
                         (print-file-status options info)
                         counts)))
                 (completing merge-counts)
                 (files/reducible-files paths options))]
     (let [error     (:error counts 0)
           incorrect (:incorrect counts 0)]
       (when-not (zero? error)
         (throw (ex-info (format "%d file(s) could not be parsed for formatting." (:error counts))
                         {:counts counts, ::expected? true})))
       (when-not (zero? incorrect)
           (throw (ex-info (format "%d file(s) formatted incorrectly." (:incorrect counts))
                           {:counts counts, ::expected? true})))
       (println "All source files formatted correctly.")
       :ok))))

(defn- fix-one [{:keys [file project-filename error original revised], :as info} options]
  (when error
    (throw (ex-info (format "Failed to format file %s: %s" project-filename (ex-message error))
                    info
                    error)))
  (when-not (= original revised)
    (println "Reformatting" project-filename)
    (spit file revised)
    1))

(defn fix
  "Applies the formatting (as per `options`) to the Clojure files
  contained in `paths`."
  ([paths]
   (fix paths {}))
  ([paths options]
   (let [fixed-count (transduce
                      (map #(fix-one % options))
                      (completing (fnil + 0 0))
                      0
                      (files/reducible-files paths options))]
     (println (format "Fixed %d files." fixed-count))
     fixed-count)))

(defn- filename-ext [filename]
  (subs filename (inc (str/last-index-of filename "."))))

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

(defn- file-exists? [path]
  (.exists (io/as-file path)))

(defn -main [& args]
  (let [parsed-opts   (cli/parse-opts args cli-options)
        [cmd & paths] (:arguments parsed-opts)
        options       (merge-default-options (:options parsed-opts))
        paths         (or (seq paths) (filter file-exists? default-paths))]
    (when-let [errors (not-empty (:errors parsed-opts))]
      (binding [*out* *err*]
        (println "Errors parsing command-line arguments:")
        (doseq [error errors]
          (println error)))
      (System/exit 1))
    (when (or (nil? cmd) (:help options))
      (println "cljfmt [OPTIONS] COMMAND [PATHS ...]")
      (println (:summary parsed-opts))
      (System/exit 0))
    (let [f (case cmd
              "check" check
              "fix"   fix
              (binding [*out* *err*]
                (println "Unknown cljfmt cmd:" cmd)
                (System/exit 1)))]
      (try
        (f paths options)
        (System/exit 0)
        (catch Throwable e
          (println (ex-message e))
          (when-not (::expected? (ex-data e))
            (pprint/pprint (Throwable->map e)))
          (System/exit 1))))))
