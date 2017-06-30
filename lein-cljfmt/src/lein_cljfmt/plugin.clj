(ns lein-cljfmt.plugin)

(defn middleware [project]
  (let [settings (:cljfmt project)]
    (if (:in-runtime settings)
      (update-in
        project [:injections] concat
        `[(require 'cljfmt.core)
          (reset! cljfmt.core/project-settings-store (quote ~settings))])  
      project)))
