(ns canon.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]))

(defn- tag [form]
  (if (vector? form) (first form)))

(defn- lines [form]
  (partition-by (comp #{:newline} tag) form))

(defn- unlines [lines]
  (vec (apply concat lines)))

(defn- make-whitespace [width]
  [:whitespace (apply str (repeat width " "))])

(defn- indent-line [margin line]
  (vec (cons (make-whitespace margin) line)))

(defn- unindent-line [line]
  (drop-while (comp #{:whitespace} tag) line))

(defn- remove-trailing-newlines [form]
  (->> (reverse form)
       (drop-while (comp #{:whitespace :newline} tag))
       (reverse)
       (vec)))

(defn- element-width [elem]
  (if (vector? elem)
    (-> elem prn/->string count)
    (-> [elem] prn/->string count dec)))

(defn- position [line index]
  (->> (take index line)
       (map element-width)
       (apply +)))

(defn- indent-line-forms [margin line]
  (map-indexed (fn [i x] (indent x (+ margin (position line i)))) line))

(defn- indent-collection [form margin]
  (let [[head & tail] (lines form)]
    (->> (map unindent-line tail)
         (map (partial indent-line (inc margin)))
         (cons head)
         (map (partial indent-line-forms margin))
         (unlines))))

(defn indent
  ([form] (indent form 0))
  ([form margin]
     (condp contains? (tag form)
       #{:vector :map :set} (indent-collection form margin)
       form)))
