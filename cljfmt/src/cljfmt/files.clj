(ns cljfmt.files
  (:require [cljfmt.core :as cljfmt]
            [clojure.java.io :as io]
            [clojure.tools.namespace.file :as tools.ns.file]
            [clojure.tools.namespace.find :as tools.ns.find]
            [clojure.tools.namespace.parse :as tools.ns.parse]))

(defn- relative-path [^java.io.File dir ^java.io.File file]
  (-> (.toAbsolutePath (.toPath dir))
      (.relativize (.toAbsolutePath (.toPath file)))
      (.toString)))

(defn- files-in-directory [^java.io.File dir {:keys [file-pattern platform], :or {platform :clj}}]
  (let [files (tools.ns.find/find-sources-in-dir dir (case (keyword platform)
                                                       :clj  tools.ns.find/clj
                                                       :cljs tools.ns.find/cljs))]
    (cond->> files
      file-pattern (filter #(re-find file-pattern (relative-path dir %))))))

(defn- find-files [path {:keys [project-root], :as options}]
  {:pre [(string? path)]}
  (try
    (let [file (io/file path)]
      (when-not (.exists file)
        (throw (ex-info (str "No such file: " (str file))
                        {:path path})))
      (for [file (if (.isDirectory file)
                   (files-in-directory file options)
                   [file])]
        {:file             file
         :project-filename (-> project-root (or ".") io/file (relative-path file))}))
    (catch Throwable e
      (throw (ex-info (str "Error finding files: " (ex-message e))
                      {:path    path
                       :options options}
                      e)))))

(defn- load-file-namespace [file]
  {:pre [(instance? java.io.File file)]}
  (let [ns-decl (tools.ns.file/read-file-ns-decl file)
        ns-symb (tools.ns.parse/name-from-ns-decl ns-decl)]
    (try
      (require ns-symb)
      (the-ns ns-symb)
      (catch Throwable e
        (throw (ex-info (format "Error loading namespace %s: %s" ns-symb (ex-message e))
                        {:namespace ns-symb}
                        e))))))

(defn- with-loaded-namespace [{:keys [file], :as info}]
  (try
    (if-let [file-namespace (load-file-namespace file)]
      (assoc info :namespace file-namespace)
      info)
    (catch Throwable e
      (assoc info :load-error (ex-info (format "Error loading file %s: %s" (str file) (ex-message e))
                                       (merge {} info)
                                       e)))))

(defn- style-indent-spec->cljfmt-spec [spec]
  (cond
    (number? spec)
    [[:block spec]]

    (= spec :defn)
    [[:inner 0]]

    :else
    (binding [*out* *err*]
      (println "Don't know how to convert" (pr-str spec) "to a cljfmt spec")
      nil)))

(defn- ns-indent-metadata-specs [a-namespace]
  (into {} (for [[symb varr] (ns-interns a-namespace)
                 :let        [spec (some-> (meta varr) :style/indent style-indent-spec->cljfmt-spec)]
                 :when       spec]
             [symb spec])))

(defn- ns-indents
  [a-namespace]
  (not-empty
   (into
    (ns-indent-metadata-specs a-namespace)
    (for [[ns-alias nmspace] (ns-aliases a-namespace)
          [symb spec] (ns-indent-metadata-specs nmspace)]
      [(symbol (name ns-alias #_(ns-name nmspace)) (name symb)) spec]))))

(defn- with-ns-indents [{file-namespace :namespace, :as info}]
  (cond-> info
    file-namespace (assoc :ns-indents (ns-indents file-namespace))))

(defn- reformat-string [options s]
  ((cljfmt/wrap-normalize-newlines #(cljfmt/reformat-string % options)) s))

(defn- with-sources [{:keys [file ns-indents], :as info} options]
  (let [options (cond-> options
                  ns-indents (update :indents (partial merge ns-indents)))]
    (merge
     info
     (try
       (let [original (slurp file)]
         {:original original
          :revised  (reformat-string options original)})
       (catch Throwable e
         {:error e})))))

(defn reducible-files [paths options]
  {:pre [(sequential? paths) (every? string? paths)]}
  (eduction
   (mapcat #(find-files % options))
   (map with-loaded-namespace)
   (map with-ns-indents)
   (map #(with-sources % options))
   paths))
