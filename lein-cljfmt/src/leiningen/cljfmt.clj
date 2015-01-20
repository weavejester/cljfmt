(ns leiningen.cljfmt
  (:refer-clojure :exclude [format])
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.main :as main]
            [leiningen.cljfmt.diff :as diff]))

(defn grep [re dir]
  (filter #(re-find re (str %)) (file-seq (io/file dir))))

(defn file-pattern [project]
  (get-in project [:cljfmt :file-pattern] #"\.clj[sx]?"))

(defn find-files [project f]
  (let [f (io/file f)]
    (when-not (.exists f) (main/abort "No such file:" (str f)))
    (if (.isDirectory f)
      (grep (file-pattern project) f)
      [f])))

(defn reformat-string [project s]
  (cljfmt/reformat-string s (:cljfmt project {})))

(defn valid-format? [project file]
  (let [content (slurp (io/file file))]
    (= content (reformat-string project content))))

(defn relative-path [dir file]
  (-> (.toURI dir)
      (.relativize (.toURI file))
      (.getPath)))

(defn project-path [project file]
  (relative-path (io/file (:root project)) (io/file file)))

(defn format-diff [project file]
  (let [filename (project-path project file)
        original (slurp (io/file file))
        revised  (reformat-string project original)
        diff     (diff/unified-diff filename original revised)]
    (if (get-in project [:cljfmt :ansi?] true)
      (diff/colorize-diff diff)
      diff)))

(defn format-paths [project]
  (->> (concat (:source-paths project)
               (:test-paths project))
       (map io/file)
       (filter #(and (.exists %) (.isDirectory %)))))

(defn check
  ([project]
   (apply check project (format-paths project)))
  ([project path & paths]
   (let [files   (mapcat (partial find-files project) (cons path paths))
         invalid (remove (partial valid-format? project) files)]
     (if (empty? invalid)
       (main/info  "All source files formatted correctly")
       (do (doseq [f invalid]
             (main/warn (project-path project f) "has incorrect formatting:")
             (main/warn (format-diff project f)))
           (main/warn)
           (main/abort (count invalid) "file(s) formatted incorrectly"))))))

(defn fix
  ([project]
   (apply fix project (format-paths project)))
  ([project path & paths]
   (let [files (mapcat (partial find-files project) (cons path paths))]
     (doseq [f files :when (not (valid-format? project f))]
       (main/info "Reformatting" (project-path project f))
       (spit f (reformat-string project (slurp f)))))))

(defn cljfmt
  "Format Clojure source files"
  [project command & args]
  (case command
    "check" (apply check project args)
    "fix"   (apply fix project args)
    (main/abort "Unknown cljfmt command:" command)))
