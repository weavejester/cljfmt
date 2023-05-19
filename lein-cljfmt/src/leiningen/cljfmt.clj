(ns leiningen.cljfmt
  (:require [cljfmt.config :as config]
            [cljfmt.tool :as tool]
            [clojure.java.io :as io]
            [leiningen.core.main :as lein]))

(defn- format-paths [project]
  (let [paths (concat (:source-paths project)
                      (:test-paths project))]
    (if (empty? paths)
      (lein/abort "No source or test paths defined in project map")
      (->> (map io/file paths)
           (filter #(and (.exists %) (.isDirectory %)))))))

(defn- execute-command [command options]
  (case command
    "check" (tool/check-no-config options)
    "fix"   (tool/fix-no-config options)
    (lein/abort "Unknown cljfmt command:" command)))

(defn- merge-default-options [options]
  (config/merge-configs config/default-config options))

(defn ^:no-project-needed cljfmt
  "Format Clojure source files."
  [project command & paths]
  (let [paths   (or (seq paths)
                    (-> project :cljfmt :paths)
                    (format-paths project))
        options (-> (:cljfmt project)
                    (assoc :paths paths)
                    (assoc :project-root (:root project))
                    (merge-default-options))]
    (if leiningen.core.main/*info*
      (execute-command command options)
      (with-out-str
        (execute-command command options)))))
