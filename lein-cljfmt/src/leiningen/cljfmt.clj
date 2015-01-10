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

(defn find-files [f]
  (let [f (io/file f)]
    (when-not (.exists f) (main/abort "No such file:" (str f)))
    (if (.isDirectory f)
      (clojure-files f)
      [f])))

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
   (apply check project (:source-paths project)))
  ([project path & paths]
   (let [files  (mapcat find-files (cons path paths))
         errors (remove valid-format? files)]
     (if (empty? errors)
       (main/info  "All source files formatted correctly")
       (main/abort (str "The following source files have incorrect formatting:\n"
                        (show-paths project errors)))))))

(defn fix
  ([project]
   (apply fix project (:source-paths project)))
  ([project path & paths]
   (let [files (mapcat find-files (cons path paths))]
     (doseq [f files :when (not (valid-format? f))]
       (main/info "Reformating" (project-path project f))
       (spit f (cljfmt/reformat-string (slurp f)))))))

(defn cljfmt
  "Format Clojure source files"
  [project command & args]
  (case command
    "check" (apply check project args)
    "fix"   (apply fix project args)
    (main/abort "Unknown cljfmt command:" command)))
