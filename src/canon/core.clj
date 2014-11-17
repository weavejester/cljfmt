(ns canon.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]))

(defn- tag [form]
  (if (vector? form) (first form)))

(defn- lines [form]
  (partition-by (comp #{:newline} tag) form))

(defn- unlines [lines]
  (vec (apply concat lines)))

(defn- indentation [line]
  (->> (take-while (comp #{:whitespace} tag) line)
       (map (comp count second))
       (apply +)))

(defn- make-whitespace [indent]
  [:whitespace (apply str (repeat indent " "))])

(defn- indent-line [n line]
  (vec (cons (make-whitespace n) line)))

(defn- unindent-line [line]
  (drop-while (comp #{:whitespace} tag) line))

(defn remove-trailing-newlines [form]
  (->> (reverse form)
       (drop-while (comp #{:whitespace :newline} tag))
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
