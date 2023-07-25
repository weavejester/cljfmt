(ns cljfmt.main
  "Functionality to apply formatting to a given project."
  (:require [cljfmt.config :as config]
            [cljfmt.io :as io]
            [cljfmt.tool :as tool]
            [clojure.tools.cli :as cli])
  (:gen-class))

(def ^:const VERSION "0.10.6")

(defn- cli-options [defaults]
  [["-h" "--help"]
   [nil "--version"]
   ["-q" "--quiet"
    :id :quiet?]
   ["-v" "--verbose"
    :id :verbose?]
   [nil "--[no-]parallel"
    :id :parallel?
    :default (:parallel? defaults)]
   [nil "--project-root PROJECT_ROOT"
    :default (:project-root defaults)]
   [nil "--file-pattern FILE_PATTERN"
    :default (:file-pattern defaults)
    :parse-fn re-pattern]
   [nil "--indents INDENTS_PATH"
    :parse-fn config/read-config]
   [nil "--alias-map ALIAS_MAP_PATH"
    :parse-fn config/read-config]
   [nil "--[no-]ansi"
    :default (:ansi? defaults)
    :id :ansi?]
   [nil "--[no-]indentation"
    :default (:indentation? defaults)
    :id :indentation?]
   [nil "--[no-]remove-multiple-non-indenting-spaces"
    :default (:remove-multiple-non-indenting-spaces? defaults)
    :id :remove-multiple-non-indenting-spaces?]
   [nil "--[no-]remove-surrounding-whitespace"
    :default (:remove-surrounding-whitespace? defaults)
    :id :remove-surrounding-whitespace?]
   [nil "--[no-]remove-trailing-whitespace"
    :default (:remove-trailing-whitespace? defaults)
    :id :remove-trailing-whitespace?]
   [nil "--[no-]insert-missing-whitespace"
    :default (:insert-missing-whitespace? defaults)
    :id :insert-missing-whitespace?]
   [nil "--[no-]remove-consecutive-blank-lines"
    :default (:remove-consecutive-blank-lines? defaults)
    :id :remove-consecutive-blank-lines?]
   [nil "--[no-]split-keypairs-over-multiple-lines"
    :default (:split-keypairs-over-multiple-lines? defaults)
    :id :split-keypairs-over-multiple-lines?]
   [nil "--[no-]sort-ns-references"
    :default (:sort-ns-references? defaults)
    :id :sort-ns-references?]])

(defn- abort [& msg]
  (binding [*out* *err*]
    (when (seq msg)
      (apply println msg))
    (System/exit 1)))

(defn- file-missing? [path]
  (not (io/exists? (io/file-entity path))))

(defn- abort-if-files-missing [paths]
  (when-some [missing (first (filter file-missing? paths))]
    (abort "No such file:" (str missing))))

(def ^:dynamic *command* "cljfmt")

(defn- print-help [summary]
  (println "Usage:")
  (println (str \tab *command* " (check | fix) [PATHS...]"))
  (when-let [config-file (config/find-config-file)]
    (println "Config:")
    (println (str \tab config-file)))
  (println "Options:")
  (println summary))

(defn -main [& args]
  (let [base-opts     (config/load-config)
        parsed-opts   (cli/parse-opts args (cli-options base-opts))
        [cmd & paths] (:arguments parsed-opts)
        flags         (:options parsed-opts)
        options       (-> (merge base-opts flags)
                          (update :paths #(or (seq paths) %)))]
    (when (:errors parsed-opts)
      (abort (:errors parsed-opts)))
    (cond
      (:version flags)
      (println "cljfmt" VERSION)

      (or (nil? cmd) (:help flags))
      (print-help (:summary parsed-opts))

      :else
      (let [cmdf (case cmd
                   "check" tool/check-no-config
                   "fix"   tool/fix-no-config
                   (abort "Unknown cljfmt command:" cmd))]
        (abort-if-files-missing paths)
        (binding [tool/*no-output* (:quiet? options)
                  tool/*verbose* (:verbose? options)]
          (cmdf options))
        (when (:parallel? options)
          (shutdown-agents))))))
