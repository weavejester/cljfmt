(ns leiningen.cljfmt
  (:refer-clojure :exclude [format])
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.stacktrace :refer [print-stack-trace]]
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
  (relative-path (io/file (:root project ".")) (io/file file)))

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

(defn check-one [project ^java.io.File f]
  (let [original (slurp f)]
    (try
      (let [revised (reformat-string project original)]
        (if (not= original revised)
          {:incorrect 1
           :file      f
           :diff      (format-diff project f original revised)}
          {:okay 1}))
      (catch Exception e
        {:error 1
         :ex    e}))))

(defn check
  ([project]
   (if project
     (apply check project (format-paths project))
     (main/abort "No project found and no source paths provided")))
  ([project path & paths]
   (loop [[^java.io.File f & files'] (mapcat (partial find-files project) (cons path paths))
          acc                        {:okay      0
                                      :incorrect 0
                                      :error     0}]
     (let [status (check-one project f)
           path   (project-path project f)
           acc'   (merge-with (fnil + 0 0) acc (dissoc status :diff :ex :file))]

       (when-let [ex (:ex status)]
         (main/warn "Failed to format file:" path)
         (binding [*out* *err*]
           (print-stack-trace ex)))

       (when-let [diff (:diff status)]
         (main/warn path "has incorrect formatting")
         (main/warn diff))

       (if-not (empty? files')
         (recur files' acc')
         (let [error     (:error acc' 0)
               incorrect (:incorrect acc' 0)]
           (when-not (zero? error)
             (main/warn error "file(s) could not be parsed for formatting"))
           (when-not (zero? incorrect)
             (main/warn incorrect "file(s) formatted incorrectly"))
           (if (and (zero? incorrect)
                    (zero? error))
             (main/info  "All source files formatted correctly")
             (main/exit 1))))))))

(defn fix
  ([project]
   (if project
     (apply fix project (format-paths project))
     (main/abort "No project found and no source paths provided")))
  ([project path & paths]
   (let [files (mapcat (partial find-files project) (cons path paths))]
     (doseq [^java.io.File f files]
       (let [original (slurp f)]
         (try
           (let [revised (reformat-string project original)]
             (when (not= original revised)
               (main/info "Reformatting" (project-path project f))
               (spit f revised)))
           (catch Exception e
             (main/warn "Failed to format file:" (project-path project-path f))
             (binding [*out* *err*]
               (print-stack-trace e)))))))))

(defn ^:no-project-needed cljfmt
  "Format Clojure source files"
  [project command & args]
  (case command
    "check" (apply check project args)
    "fix"   (apply fix project args)
    (main/abort "Unknown cljfmt command:" command)))
