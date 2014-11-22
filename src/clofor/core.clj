(ns clofor.core
  (:require [fast-zip.core :as fz]
            [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]
            [rewrite-clj.zip :as z]))

(defn- edit-all [zloc p? f]
  (loop [zloc zloc]
    (if-let [zloc (z/find-next zloc fz/next p?)]
      (recur (f zloc))
      zloc)))

(defn- transform [form zf & args]
  (z/root (apply zf (z/edn form) args)))

(defn- whitespace? [zloc]
  (= (z/tag zloc) :whitespace))

(defn- line-start? [zloc]
  (z/linebreak? (fz/prev zloc)))

(defn- indentation? [zloc]
  (and (line-start? zloc) (whitespace? zloc)))

(defn unindent [form]
  (transform form edit-all indentation? fz/remove))

(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"})

(defn- prior-string [zloc]
  (if-let [p (z/left* zloc)]
    (str (prior-string p) (prn/->string (z/node p)))
    (if-let [p (z/up* zloc)]
      (str (prior-string p) (start-element (first (z/node p))))
      "")))

(defn- last-line-in-string [^String s]
  (subs s (inc (.lastIndexOf s "\n"))))

(defn- margin [zloc]
  (-> zloc prior-string last-line-in-string count))

(defn- whitespace [width]
  [:whitespace (apply str (repeat width " "))])

;; z/leftmost currently broken
(defn- leftmost [zloc]
  (if-let [zloc (fz/left zloc)]
    (recur zloc)
    zloc))

(defn- indent-coll-amount [zloc]
  (-> zloc leftmost margin))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))

(defmulti indent-list-amount
  (comp z/value leftmost))

(defmethod indent-list-amount 'let [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc z/up margin (+ 2))
    (indent-coll-amount zloc)))

(defmethod indent-list-amount :default [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc leftmost z/next margin)
    (indent-coll-amount zloc)))

(defn- indent-amount [zloc]
  (if (-> zloc z/up z/tag #{:list})
    (indent-list-amount zloc)
    (indent-coll-amount zloc)))

(defn- indent-line [zloc]
  (fz/insert-left zloc (whitespace (indent-amount zloc))))

(defn indent [form]
  (transform form edit-all line-start? indent-line))

(defn- trailing-newline? [zloc]
  (and (z/linebreak? zloc) (z/rightmost? zloc)))

(defn remove-trailing-newlines [form]
  (transform form edit-all trailing-newline? fz/remove))
