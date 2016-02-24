(ns leiningen.cljfmt
  (:refer-clojure :exclude [format])
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [leiningen.core.main :as main]
            [leiningen.cljfmt.diff :as diff]
            [meta-merge.core :refer [meta-merge]]))

(defn grep [re dir]
  (filter #(re-find re (str %)) (file-seq (io/file dir))))

(defn file-pattern [project]
  (get-in project [:cljfmt :file-pattern] #"\.clj[sx]?$"))

(defn find-files [project f]
  (let [f (io/file f)]
    (when-not (.exists f) (main/abort "No such file:" (str f)))
    (if (.isDirectory f)
      (grep (file-pattern project) f)
      [f])))

(def default-config
  {:indents cljfmt/default-indents})

(defn reformat-string [project s]
  (cljfmt/reformat-string s (meta-merge default-config (:cljfmt project {}))))

(defn relative-path [dir file]
  (-> (.toURI dir)
      (.relativize (.toURI file))
      (.getPath)))

(defn project-path [project file]
  (relative-path (io/file (:root project)) (io/file file)))

(defn format-diff
  ([project file]
   (let [original (slurp (io/file file))]
     (format-diff project file original (reformat-string project original))))
  ([project file original revised]
   (let [filename (project-path project file)
         diff     (diff/unified-diff filename original revised)]
     (if (get-in project [:cljfmt :ansi?] true)
       (diff/colorize-diff diff)
       diff))))

(defn format-paths [project]
  (let [paths (concat (:source-paths project)
                      (:test-paths project))]
    (if (empty? paths)
      (main/abort "No source or test paths defined in project map")
      (->> (map io/file paths)
           (filter #(and (.exists %) (.isDirectory %)))))))

(defn check
  ([project]
   (apply check project (format-paths project)))
  ([project path & paths]
   (let [files   (mapcat (partial find-files project) (cons path paths))
         flag    (atom 0)]
     (doseq [f     files
             :let  [original (slurp f)
                    revised  (reformat-string project original)]
             :when (not= original revised)]
       (main/warn (project-path project f) "has incorrect formatting:")
       (main/warn (format-diff project f original revised))
       (swap! flag inc))
     (if (zero? @flag)
       (main/info  "All source files formatted correctly")
       (do
         (main/warn)
         (main/abort @flag "file(s) formatted incorrectly"))))))

(defn fix
  ([project]
   (apply fix project (format-paths project)))
  ([project path & paths]
   (let [files (mapcat (partial find-files project) (cons path paths))]
     (doseq [f     files
             :let  [original (slurp f)
                    revised  (reformat-string project original)]
             :when (not= original revised)]
       (main/info "Reformatting" (project-path project f))
       (spit f revised)))))

(defn cljfmt
  "Format Clojure source files"
  [project command & args]
  (case command
    "check" (apply check project args)
    "fix"   (apply fix project args)
    (main/abort "Unknown cljfmt command:" command)))
