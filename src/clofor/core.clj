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

(defn- surrounding? [zloc p?]
  (and (p? zloc) (or (z/leftmost? zloc) (z/rightmost? zloc))))

(defn remove-surrounding-whitespace [form]
  (transform form edit-all #(surrounding? % z/whitespace?) fz/remove))

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

(defn coll-indent [zloc]
  (-> zloc leftmost margin))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))

(defn list-indent [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc leftmost z/next margin)
    (coll-indent zloc)))

(def basic-indent-size 2)

(defn basic-indent [zloc sym idx]
  (if (and (= (-> zloc leftmost z/value) sym)
           (> (index-of zloc) idx))
    (-> zloc z/up margin (+ basic-indent-size))))

(def basic-indent-defaults
  '{go   0, case   1, if-let   1, when-not        1
    do   0, while  1, thread   0, defstruct       1
    if   1, doseq  1, testing  1, with-open       1
    ns   1, alt!!  0, finally  0, defrecord       2
    are  1, letfn  1, deftype  2, when-some       1
    try  0, condp  2, if-some  1, when-first      1
    let  1, proxy  2, go-loop  1, struct-map      1
    for  1, catch  2, dotimes  1, extend-type     1
    doto 1, assoc  1, binding  1, defprotocol     1
    alt! 0, if-not 1, locking  1, with-precision  1
    when 1, future 0, comment  0, extend-protocol 1
    loop 1, extend 1, when-let 1, with-local-vars 1})

(def default-indents
  (for [[s i] basic-indent-defaults] #(basic-indent % s i)))

(defn- indent-amount [zloc]
  (if (-> zloc z/up z/tag #{:list})
    (or ((apply some-fn default-indents) zloc) (list-indent zloc))
    (coll-indent zloc)))

(defn- indent-line [zloc]
  (fz/insert-left zloc (whitespace (indent-amount zloc))))

(defn indent [form]
  (transform form edit-all line-start? indent-line))

(def reindent
  (comp indent unindent))
