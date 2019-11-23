(ns leiningen.cljfmt
  (:require [cljfmt.core :as cljfmt]
            [cljfmt.main]
            [clojure.java.io :as io]
            [leiningen.core.main]))

(defn- format-paths [project]
  (let [paths (concat (:source-paths project)
                      (:test-paths project))]
    (if (empty? paths)
      (leiningen.core.main/abort "No source or test paths defined in project map")
      (->> (map io/file paths)
           (filter #(and (.exists %) (.isDirectory %)))))))

(defn- execute-command [command options paths]
  (case command
    "check" (cljfmt.main/check paths options)
    "fix"   (cljfmt.main/fix paths options)
    (leiningen.core.main/abort "Unknown cljfmt command:" command)))

(defn ^:no-project-needed cljfmt
  "Format Clojure source files"
  [project command & paths]
  (let [paths   (or (seq paths)
                    (-> project :cljfmt :paths)
                    (format-paths project))
        options (-> (:cljfmt project)
                    (dissoc :paths)
                    (assoc :project-root (:root project))
                    (cljfmt.main/merge-default-options))]
    (if leiningen.core.main/*info*
      (execute-command command options paths)
      (with-out-str
        (execute-command command options paths)))))
