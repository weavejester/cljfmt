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

(defn check [project]
  (let [files (source-files project)]
    (if (every? valid-format? files)
      (println "ok")
      (println "fail"))))

(defn cljfmt
  "Format Clojure source files"
  [project command]
  (case command
    "check" (check project)
    (main/abort "Unknown format command:" command)))
