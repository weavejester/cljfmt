(ns canon.core
  (:require [fast-zip.core :as fz]
            [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]
            [rewrite-clj.zip :as z]))

(def ^:private start-element
  {:meta "^", :meta* "#^", :deref "@", :var "#'", :fn "#("
   :list "(", :vector "[", :map "{", :set "#{", :eval "#="
   :uneval "#_", :reader-macro "#", :quote "'", :syntax-quote "`"
   :unquote "~", :unquote-splicing "~@"})

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

(defn- make-whitespace [width]
  [:whitespace (apply str (repeat width " "))])

(defn- find-next [zloc p?]
  (->> (fz/next zloc)
       (iterate fz/next)
       (take-while identity)
       (take-while (complement fz/end?))
       (drop-while (complement p?))
       (first)))

(defn- edit-all [zloc p? f]
  (loop [zloc zloc]
    (if-let [zloc (find-next zloc p?)]
      (recur (f zloc))
      zloc)))

(defn- transform [form zf & args]
  (z/root (apply zf (z/edn form) args)))

(defn- whitespace? [zloc]
  (= (z/tag zloc) :whitespace))

(defn- indentation? [zloc]
  (and (whitespace? zloc) (z/linebreak? (fz/prev zloc))))

(defn- unindent-line [zloc]
  (if (whitespace? zloc) (z/remove* zloc) zloc))

(defn unindent [form]
  (transform form edit-all indentation? fz/remove))

(defn indent [form])

(defn- trailing-newline? [zloc]
  (and (z/linebreak? zloc) (z/rightmost? zloc)))

(defn remove-trailing-newlines [form]
  (transform form edit-all trailing-newline? fz/remove))
