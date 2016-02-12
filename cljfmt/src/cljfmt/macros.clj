(ns cljfmt.macros
  (:require [clojure.java.io :as io]))

(defmacro read-resource
  [resource]
  (let [res (-> resource io/resource slurp read-string)]
    `(~'read-string ~(pr-str res))))
