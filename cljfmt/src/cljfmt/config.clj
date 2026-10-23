(ns cljfmt.config
  (:require [cljfmt.core :as cljfmt]
            [cljfmt.report :as report]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def default-config
  (merge cljfmt/default-options
         {:project-root "."
          :paths        ["src" "test" "project.clj"]
          :file-pattern #"\.(?:clj[csx]?|bb)$"
          :ansi?        true
          :parallel?    false}))

(def ^:private clj-config-warning-message
  (str "Warning: .clj config file '%s' detected but --read-clj-config-files is "
       "not set. Ignoring config file."))

(defn- filename-ext [file]
  (let [filename (str file)]
    (subs filename (inc (str/last-index-of filename ".")))))

(defn read-config
  "Read a Clojure or edn configuration file."
  [file]
  (let [contents (slurp file)]
    (case (filename-ext file)
      "clj" (read-string contents)
      "edn" (edn/read-string {:readers {'re re-pattern}} contents))))

(defn- valid-symbol? [sym]
  (and (symbol? sym) (not (str/starts-with? (name sym) "'"))))

(defn- re-pattern? [x]
  (instance? java.util.regex.Pattern x))

(s/def ::one-indent-key (s/or :symbol valid-symbol? :pattern re-pattern?))
(s/def ::vec-indent-key (s/tuple ::one-indent-key ::one-indent-key))
(s/def ::form-key       (s/or :one ::one-indent-key :vec ::vec-indent-key))

(s/def ::indents                (s/map-of ::form-key any?))
(s/def ::extra-indents          (s/map-of ::form-key any?))
(s/def ::blank-line-forms       (s/map-of ::one-indent-key any?))
(s/def ::extra-blank-line-forms (s/map-of ::one-indent-key any?))
(s/def ::aligned-forms          (s/map-of ::one-indent-key any?))
(s/def ::extra-aligned-forms    (s/map-of ::one-indent-key any?))

(s/def ::split-keypairs-over-multiple-lines
  (s/or :mode    #{:all :only-multiline :none}
        :boolean boolean?
        :nil     nil?))

(s/def ::config (s/keys :opt-un [::indents ::extra-indents
                                 ::blank-line-forms ::extra-blank-line-forms
                                 ::aligned-forms ::extra-aligned-forms
                                 ::split-keypairs-over-multiple-lines]))

(s/check-asserts true)

(def ^:private legacy-keypairs-warning-message
  (str "Warning: the ':split-keypairs-over-multiple-lines?' option is "
       "deprecated. Use ':split-keypairs-over-multiple-lines' with a value of "
       ":all or :only-multiline instead."))

(defn- convert-legacy-keypairs [config]
  (report/warn legacy-keypairs-warning-message)
  (-> config
      (cond-> (:split-keypairs-over-multiple-lines? config)
        (assoc :split-keypairs-over-multiple-lines :all))
      (dissoc :split-keypairs-over-multiple-lines?)))

(defn convert-legacy-keys [config]
  (cond-> config
    (:legacy/merge-indents? config)
    (-> (assoc :extra-indents (:indents config))
        (dissoc :legacy/merge-indents?
                :indents))

    (contains? config :split-keypairs-over-multiple-lines?)
    (convert-legacy-keypairs)))

(defn- parent-dirs [^String root]
  (->> (.getAbsoluteFile (io/file root))
       (iterate #(.getParentFile ^java.io.File %))
       (take-while some?)))

(defn- find-file-in-dir ^java.io.File [^java.io.File dir ^String name]
  (let [f (io/file dir name)]
    (when (.exists f) f)))

(def ^:private valid-config-files
  [".cljfmt.edn" ".cljfmt.clj" "cljfmt.edn" "cljfmt.clj"])

(defn- find-config-file-in-dir ^java.io.File [^java.io.File dir safe?]
  (let [^java.io.File file (some #(find-file-in-dir dir %) valid-config-files)]
    (if (and file (not safe?) (str/ends-with? (.getName file) ".clj"))
      (do (report/warn (format clj-config-warning-message (.getName file)))
          (some #(find-file-in-dir dir %)
                (remove #(str/ends-with? % ".clj") valid-config-files)))
      file)))

(defn find-config-file
  "Find a configuration file in the current directory or in the first parent
  directory to contain one. Valid configuration file names are:
  - `.cljfmt.edn`
  - `.cljfmt.clj`
  - `cljfmt.edn`
  - `cljfmt.clj`"
  ([] (find-config-file ""))
  ([path] (find-config-file path {}))
  ([path {safe? :read-clj-config-files?}]
   (some->> (parent-dirs path) (some #(find-config-file-in-dir % safe?)))))

(defn- directory? [path]
  (some-> path io/file .getAbsoluteFile .isDirectory))

(defn load-config
  "Load a configuration merged with a map of sensible defaults. May take
  an path to a config file, or to a directory to search. If no argument
  is supplied, it uses the current directory. See: [[find-config-file]]."
  ([] (load-config ""))
  ([path] (load-config path {}))
  ([path opts]
   (let [path (if (directory? path)
                (find-config-file path opts)
                path)
         custom-config (some-> path read-config)]
     (when custom-config (s/assert ::config custom-config))
     (convert-legacy-keys (merge default-config custom-config)))))
