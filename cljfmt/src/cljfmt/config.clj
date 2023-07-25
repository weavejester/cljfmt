(ns cljfmt.config
  (:require [cljfmt.core :as cljfmt]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def default-config
  (merge cljfmt/default-options
         {:project-root "."
          :paths        ["src" "test" "project.clj"]
          :file-pattern #"\.clj[csx]?$"
          :ansi?        true
          :parallel?    false}))

(defn merge-configs
  "Merge two or more cljfmt configuration maps together."
  ([a b]
   (-> (merge a b)
       (assoc :indents (merge (:indents a {}) (:indents b)))))
  ([a b & more]
   (reduce merge-configs (merge-configs a b) more)))

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

(defn- parent-dirs [^String root]
  (->> (.getAbsoluteFile (io/file root))
       (iterate #(.getParentFile ^java.io.File %))
       (take-while some?)))

(defn- find-file-in-dir ^java.io.File [^java.io.File dir ^String name]
  (let [f (io/file dir name)]
    (when (.exists f) f)))

(def ^:private valid-config-files
  [".cljfmt.edn" ".cljfmt.clj" "cljfmt.edn" "cljfmt.clj"])

(defn- find-config-file-in-dir ^java.io.File [^java.io.File dir]
  (some #(find-file-in-dir dir %) valid-config-files))

(defn find-config-file
  "Find a configuration file in the current directory or in the first parent
  directory to contain one. Valid configuration file names are:
  - `.cljfmt.edn`
  - `.cljfmt.clj`
  - `cljfmt.edn`
  - `cljfmt.clj`"
  ([] (find-config-file ""))
  ([path] (some->> (parent-dirs path) (some find-config-file-in-dir))))

(defn load-config
  "Load a configuration merged with a map of sensible defaults. See
  [[find-config-file]]."
  ([] (load-config ""))
  ([path]
   (merge-configs default-config
                  (some-> (find-config-file path) read-config))))
