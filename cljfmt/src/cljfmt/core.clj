(ns cljfmt.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [fast-zip.core :as fz]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z]))

(defn- edit-all [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc fz/next p?)]
      (recur (f zloc))
      zloc)))

(defn- transform [form zf & args]
  (z/root (apply zf (z/edn form) args)))

(defn- surrounding? [zloc p?]
  (and (p? zloc) (or (z/leftmost? zloc) (z/rightmost? zloc))))

(defn- top? [zloc]
  (not= (z/node zloc) (z/root zloc)))

(defn- surrounding-whitespace? [zloc]
  (and (top? (z/up zloc))
       (surrounding? zloc z/whitespace?)))

(defn remove-surrounding-whitespace [form]
  (transform form edit-all surrounding-whitespace? fz/remove))

(defn- element? [zloc]
  (if zloc (not (z/whitespace-or-comment? zloc))))

(defn missing-whitespace? [zloc]
  (and (element? zloc) (element? (fz/right zloc))))

(defn insert-missing-whitespace [form]
  (transform form edit-all missing-whitespace? z/append-space))

(defn- whitespace? [zloc]
  (= (z/tag zloc) :whitespace))

(defn- line-start? [zloc]
  (z/linebreak? (fz/prev zloc)))

(defn- indentation? [zloc]
  (and (line-start? zloc) (whitespace? zloc)))

(defn- skip-whitespace [zloc]
  (z/skip z/next whitespace? zloc))

(defn- comment-next? [zloc]
  (-> zloc skip-whitespace z/node n/comment?))

(defn- should-indent? [zloc]
  (and (line-start? zloc) (not (comment-next? zloc))))

(defn- should-unindent? [zloc]
  (and (indentation? zloc) (not (comment-next? zloc))))

(defn unindent [form]
  (transform form edit-all should-unindent? fz/remove))

(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"})

(defn- prior-string [zloc]
  (if-let [p (z/left* zloc)]
    (str (prior-string p) (n/string (z/node p)))
    (if-let [p (z/up* zloc)]
      (str (prior-string p) (start-element (n/tag (z/node p))))
      "")))

(defn- last-line-in-string [^String s]
  (subs s (inc (.lastIndexOf s "\n"))))

(defn- margin [zloc]
  (-> zloc prior-string last-line-in-string count))

(defn- whitespace [width]
  (n/whitespace-node (apply str (repeat width " "))))

(defn coll-indent [zloc]
  (-> zloc fz/leftmost margin))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))

(defn list-indent [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc fz/leftmost z/next margin)
    (coll-indent zloc)))

(def indent-size 2)

(defn indent-width [zloc]
  (case (z/tag zloc)
    :list indent-size
    :fn   (inc indent-size)))

(defn- remove-namespace [x]
  (if (symbol? x) (symbol (name x)) x))

(defn inner-indent [zloc sym depth]
  (let [top (nth (iterate z/up zloc) depth)]
    (if (= (-> top fz/leftmost z/value remove-namespace) sym)
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))

(defn- nth-form [zloc n]
  (reduce (fn [z f] (if z (f z)))
          (z/leftmost zloc)
          (repeat n z/right)))

(defn- first-form-in-line? [zloc]
  (if-let [zloc (fz/left zloc)]
    (if (whitespace? zloc)
      (recur zloc)
      (z/linebreak? zloc))
    true))

(defn block-indent [zloc sym idx]
  (if (and (some-> zloc (nth-form (inc idx)) first-form-in-line?)
           (> (index-of zloc) idx))
    (inner-indent zloc sym 0)))

(def read-resource
  (comp edn/read-string slurp io/resource))

(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.edn")
         (read-resource "cljfmt/indents/compojure.edn")))

(defmulti indenter-fn
  (fn [sym [type & args]] type))

(defmethod indenter-fn :inner [sym [_ depth]]
  (fn [zloc] (inner-indent zloc sym depth)))

(defmethod indenter-fn :block [sym [_ idx]]
  (fn [zloc] (block-indent zloc sym idx)))

(defn- make-indenter [[sym opts]]
  (apply some-fn (map (partial indenter-fn sym) opts)))

(defn- indent-amount [zloc indents]
  (let [indenter (apply some-fn (map make-indenter indents))]
    (if (-> zloc z/up z/tag #{:list :fn})
      (or (indenter zloc) (list-indent zloc))
      (coll-indent zloc))))

(defn- indent-line [zloc indents]
  (let [width (indent-amount zloc indents)]
    (if (> width 0)
      (fz/insert-left zloc (whitespace width))
      zloc)))

(defn indent [form indents]
  (let [indents (into default-indents indents)]
    (transform form edit-all should-indent? #(indent-line % indents))))

(defn reindent [form indents]
  (indent (unindent form) indents))

(defn reformat-form
  [form & [{:as opts}]]
  (-> form
      (cond-> (:remove-surrounding-whitespace? opts true)
        remove-surrounding-whitespace)
      (cond-> (:insert-missing-whitespace? opts true)
        insert-missing-whitespace)
      (cond-> (:indentation? opts true)
        (reindent (:indents opts {})))))

(defn reformat-string [form-string & [options]]
  (-> (p/parse-string-all form-string)
      (reformat-form options)
      (n/string)))
