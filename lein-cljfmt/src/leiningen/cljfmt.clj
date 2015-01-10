(ns leiningen.cljfmt
  (:refer-clojure :exclude [format])
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.main :as main]))

(defn clojure-file? [file]
  (re-find #"\.clj[sx]?" (str file)))

(defn clojure-files [dir]
  (filter clojure-file? (file-seq (io/file dir))))

(defn source-files [project]
  (mapcat clojure-files (:source-paths project)))

(defn valid-format? [file]
  (let [s (slurp file)]
    (= s (cljfmt/reformat-string s))))

(defn relative-path [dir file]
  (-> (.toURI dir)
      (.relativize (.toURI file))
      (.getPath)))

(defn project-paths [project files]
  (let [root (io/file (:root project))]
   (map (partial relative-path root) files)))

(defn check [project]
  (let [files  (source-files project)
        errors (remove valid-format? files)]
    (if (empty? errors)
      (main/info  "All source files formatted correctly")
      (main/abort "The following source files have incorrect formatting:\n "
                  (str/join "\n  " (project-paths project errors))))))

(defn cljfmt
  "Format Clojure source files"
  [project command]
  (case command
    "check" (check project)
    (main/abort "Unknown cljfmt command:" command)))
