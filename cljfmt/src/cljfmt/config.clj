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
  "Merge two cljfmt configuration maps together."
  [a b]
  (-> (merge a b)
      (assoc :indents (merge (:indents a {}) (:indents b)))))

(defn- filename-ext [file]
  (let [filename (str file)]
    (subs filename (inc (str/last-index-of filename ".")))))

(defn read-config
  "Read a Clojure or edn configuration file."
  [file]
  (let [contents (slurp file)]
    (case (filename-ext file)
      "clj" (read-string contents)
      "edn" (edn/read-string contents))))

(defn- parent-dirs [^String root]
  (->> (.getAbsoluteFile (io/file root))
       (iterate #(.getParentFile ^java.io.File %))
       (take-while some?)))

(defn- find-file-in-dir ^java.io.File [^java.io.File dir ^String name]
  (let [f (io/file dir name)]
    (when (.exists f) f)))

(defn- find-config-file-in-dir ^java.io.File [^java.io.File dir]
  (or (find-file-in-dir dir ".cljfmt.edn")
      (find-file-in-dir dir ".cljfmt.clj")))

(defn find-config-file
  "Find a `.cljfmt.end` or `.cljfmt.clj` configuration file in the current
  directory or from the first parent directory to contain one."
  ([] (find-config-file ""))
  ([path] (some->> (parent-dirs path) (some find-config-file-in-dir))))

(defn load-config
  "Load the default configuration merged with the nearest configuration
  dotfile."
  ([] (load-config ""))
  ([path]
   (some->> (find-config-file path)
            read-config
            (merge-configs default-config))))
