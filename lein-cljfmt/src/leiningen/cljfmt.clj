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
  (let [s (slurp (io/file file))]
    (= s (cljfmt/reformat-string s))))

(defn relative-path [dir file]
  (-> (.toURI dir)
      (.relativize (.toURI file))
      (.getPath)))

(defn project-path [project file]
  (relative-path (io/file (:root project)) (io/file file)))

(defn show-paths [project files]
  (str "  " (str/join "\n  " (map (partial project-path project) files))))

(defn check
  ([project]
   (apply check project (source-files project)))
  ([project file & files]
   (let [errors (remove valid-format? (cons file files))]
     (if (empty? errors)
       (main/info  "Source files formatted correctly")
       (main/abort (str "The following source files have incorrect formatting:\n"
                      (show-paths project errors)))))))

(defn cljfmt
  "Format Clojure source files"
  [project command & args]
  (case command
    "check" (apply check project args)
    (main/abort "Unknown cljfmt command:" command)))
