(ns leiningen.cljfmt
  (:refer-clojure :exclude [format])
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.main :as main]
            [leiningen.cljfmt.diff :as diff]))

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
  (let [content (slurp (io/file file))]
    (= content (cljfmt/reformat-string content))))

(defn relative-path [dir file]
  (-> (.toURI dir)
      (.relativize (.toURI file))
      (.getPath)))

(defn project-path [project file]
  (relative-path (io/file (:root project)) (io/file file)))

(defn format-diff [project file]
  (let [filename (project-path project file)
        original (slurp (io/file file))
        revised  (cljfmt/reformat-string original)]
    (diff/unified-diff filename original revised)))

(defn check
  ([project]
   (apply check project (:source-paths project)))
  ([project path & paths]
   (let [files   (mapcat find-files (cons path paths))
         invalid (remove valid-format? files)]
     (if (empty? invalid)
       (main/info  "All source files formatted correctly")
       (do (doseq [f invalid]
             (main/warn (project-path project f) "formatted incorrectly")
             (main/warn (format-diff project f)))
           (main/abort "\n" (count invalid) "file(s) formatted incorrectly"))))))

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
