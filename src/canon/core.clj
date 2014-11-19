(ns canon.core
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]
            [rewrite-clj.zip :as z]))

(defn- tag [form]
  (if (vector? form) (first form)))

(def ^:private start-element
  {:meta "^", :meta* "#^", :deref "@", :var "#'", :fn "#("
   :list "(", :vector "[", :map "{", :set "#{", :eval "#="
   :uneval "#_", :reader-macro "#", :quote "'", :syntax-quote "`"
   :unquote "~", :unquote-splicing "~@"})

(defn- prior-string [zip]
  (if-let [p (z/left* zip)]
    (str (prior-string p) (prn/->string (z/node p)))
    (if-let [p (z/up* zip)]
      (str (prior-string p) (start-element (first (z/node p))))
      "")))

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

(defn- part->string [elem]
  (if (vector? elem)
    (prn/->string elem)
    (start-element elem)))

(defn- position [line index]
  (->> (take index line)
       (map (comp count part->string))
       (apply +)))

(declare indent)

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
