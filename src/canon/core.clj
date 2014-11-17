(ns canon.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]))

(defn- form? [type? form]
  (boolean (and (vector? form) (type? (first form)))))

(defn- lines [form]
  (partition-by (partial form? #{:newline}) form))

(defn- unlines [lines]
  (vec (apply concat lines)))

(defn- indentation [line]
  (->> (take-while (partial form? #{:whitespace}) line)
       (map (comp count second))
       (apply +)))

(defn- make-whitespace [indent]
  [:whitespace (apply str (repeat indent " "))])

(defn- indent-line [n line]
  (vec (cons (make-whitespace n) line)))

(defn- unindent-line [line]
  (drop-while (partial form? #{:whitespace}) line))

(defn remove-trailing-newlines [form]
  (->> (reverse form)
       (drop-while (partial form? #{:whitespace :newline}))
       (reverse)
       (vec)))

(defn indent-collection
  ([form] (indent-collection form 0))
  ([form indent]
     (let [[head & tail] (lines form)]
       (->> (map unindent-line tail)
            (map (partial indent-line (inc indent)))
            (cons head)
            (unlines)))))
