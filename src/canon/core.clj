(ns canon.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]))

(defn- form? [type? form]
  (and (vector? form) (type? (first form))))

(defn remove-trailing-newlines [form]
  (->> (reverse form)
       (drop-while (partial form? #{:whitespace :newline}))
       (reverse)
       (vec)))
