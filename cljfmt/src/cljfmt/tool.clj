(ns cljfmt.tool
  (:require [cljfmt.config :as config]
            [cljfmt.core :as cljfmt]
            [cljfmt.diff :as diff]
            [cljfmt.io :as io]
            [cljfmt.report :as report]))

(def ^:dynamic *verbose* false)

(defn- trace [& args]
  (when *verbose* (apply report/warn args)))

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

(defn- format-diff
  ([options file]
   (let [original (io/read-file file)]
     (format-diff options file original (reformat-string options original))))
  ([options file original revised]
   (let [filename (io/project-path options file)
         diff     (diff/unified-diff filename original revised)]
     (if (:ansi? options)
       (diff/colorize-diff diff)
       diff))))

(defn- check-one [options file]
  (trace "Processing file:" (io/project-path options file))
  (let [original (io/read-file file)
        status   {:counts report/zero-counts :file file}]
    (try
      (let [revised (reformat-string options original)]
        (if (not= original revised)
          (-> status
              (assoc-in [:counts :incorrect] 1)
              (assoc :diff (format-diff options file original revised)))
          (assoc-in status [:counts :okay] 1)))
      (catch Exception ex
        (-> status
            (assoc-in [:counts :error] 1)
            (assoc :exception ex))))))

(defn check-no-config
  "The same as `check`, but ignores dotfile configuration."
  [{:keys [parallel? paths report] :as options
    :or   {report report/console}}]
  (let [context (report :check/initial options nil)
        map*    (if parallel? pmap map)
        summary (->> paths
                     (mapcat (partial find-files context))
                     (map* (partial check-one context))
                     (reduce #(report :check/file %1 %2) context))]
    (report :check/summary summary nil)))

(defn check
  "Checks that the Clojure paths specified by the :paths option are
  correctly formatted."
  [options]
  (let [opts (merge (config/load-config) options)]
    (check-no-config opts)))

(defn- fix-one [options file]
  (trace "Processing file:" (io/project-path options file))
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
  [{:keys [parallel? report] :as options
    :or   {report report/console}}]
  (let [context (report :fix/initial options nil)
        files   (recursively-find-files context)
        map*    (if parallel? pmap map)
        summary (->> files
                     (map* (partial fix-one context))
                     (reduce #(report :fix/file %1 %2) context))]
    (report :fix/summary summary nil)))

(defn fix
  "Fixes the formatting for all files specified by the :paths option."
  [options]
  (let [opts (merge (config/load-config) options)]
    (fix-no-config opts)))
