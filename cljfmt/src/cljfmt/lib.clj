(ns cljfmt.lib
  (:require [cljfmt.config :as config]
            [cljfmt.core :as cljfmt]
            [cljfmt.diff :as diff]
            [cljfmt.io :as io]
            [clojure.java.io :as cio])
  (:import (java.io File)))

(defn- grep [re dir]
  (filter #(re-find re (io/relative-path % dir)) (io/list-files dir)))

(defn- find-files [{:keys [file-pattern]} f]
  (let [f (io/file-entity f)]
    (when (io/exists? f)
      (if (io/directory? f)
        (grep file-pattern f)
        (list f)))))

(defn- reformat-string [options s]
  ((cljfmt/wrap-normalize-newlines #(cljfmt/reformat-string % options)) s))

(defn- project-path [{:keys [project-root]} file]
  (->> (or project-root ".") cio/file (io/relative-path file)))

(defn- format-diff
  ([options file]
   (let [original (io/read-file file)]
     (format-diff options file original (reformat-string options original))))
  ([options file original revised]
   (let [filename (project-path options file)
         diff     (diff/unified-diff filename original revised)]
     (if (:ansi? options)
       (diff/colorize-diff diff)
       diff))))

(def ^:private zero-counts {:okay 0, :incorrect 0, :error 0})

(defn- check-one [{:keys [cljfmt/trace diff?] :as options
                   :or {trace (constantly nil), diff? true}} file]
  (trace "Processing file:" (project-path options file))
  (let [original (io/read-file file)
        status   {:counts zero-counts :file file}]
    (try
      (let [revised (reformat-string options original)]
        (if (not= original revised)
          (cond-> status
            true  (assoc-in [:counts :incorrect] 1)
            diff? (assoc :diff (format-diff options file original revised)))
          (assoc-in status [:counts :okay] 1)))
      (catch Exception ex
        (-> status
            (assoc-in [:counts :error] 1)
            (assoc :exception ex))))))

(defn- merge-counts
  ([]    zero-counts)
  ([a]   a)
  ([a b] (merge-with + a b)))

(defn- merge-statuses
  [a b]
  (let [^File file (:file b)
        path       (.getPath file)
        diff       (:diff b)
        exception  (:exception b)]
    (cond-> a
      true (update :counts #(merge-with + % (:counts b)))

      (and file (= 1 (-> b :counts :incorrect)))
      (assoc-in [:incorrect path] "has incorrect formatting")

      (and file diff) (assoc-in [:incorrect path] diff)
      (and file exception) (assoc-in [:error path] exception))))

(defn check-no-config
  "The same as `check`, but ignores dotfile configuration."
  [{:keys [cljfmt/report parallel? paths] :as options}]
  (let [map*     (if parallel? pmap map)
        statuses (->> paths
                      (mapcat (partial find-files options))
                      (map* (partial check-one options))
                      (map (fn [status]
                             (when report
                               (let [path (project-path options (:file status))]
                                 (report (assoc status :path path))))
                             status)))]
    (if report
      (reduce merge-counts (:counts statuses))
      (reduce merge-statuses {} statuses))))

(defn check
  "Checks that the Clojure paths specified by the :paths option are
  correctly formatted."
  [options]
  (let [opts (merge (config/load-config) options)]
    (check-no-config opts)))

(defn- fix-one [{:keys [cljfmt/trace] :as options} file]
  (trace "Processing file:" (project-path options file))
  (let [original (io/read-file file)]
    (try
      (let [revised  (reformat-string options original)
            changed? (not= original revised)]
        (io/update-file file revised changed?)
        (if changed?
          {:file file :reformatted true}
          {:file file}))
      (catch Exception e
        {:file file :exception e}))))

(defn- recursively-find-files [{:keys [paths] :as options}]
  (mapcat #(find-files options %) (set paths)))

(defn fix-no-config
  "The same as `fix`, but ignores dotfile configuration."
  [{:keys [cljfmt/report] :as options}]
  (let [files (recursively-find-files options)
        map*  (if (:parallel? options) pmap map)]
    (->> files
         (map* (partial fix-one options))
         (map (partial report options))
         dorun)))

(defn fix
  "Fixes the formatting for all files specified by the :paths option."
  [options]
  (let [opts (merge (config/load-config) options)]
    (fix-no-config opts)))
