(ns cljfmt.core
  #?@(:clj
      [(:refer-clojure :exclude [reader-conditional?])
       (:require [clojure.java.io :as io]
                 [clojure.string :as str]
                 [clojure.zip :as zip]
                 [rewrite-clj.node :as n]
                 [rewrite-clj.parser :as p]
                 [rewrite-clj.zip :as z
                  :refer [append-space edn skip whitespace-or-comment? child-sexprs]])
       (:import java.util.regex.Pattern)]
      :cljs
      [(:require [cljs.reader :as reader]
                 [clojure.zip :as zip]
                 [clojure.string :as str]
                 [rewrite-clj.node :as n]
                 [rewrite-clj.parser :as p]
                 [rewrite-clj.zip :as z]
                 [rewrite-clj.zip.base :as zb :refer [edn child-sexprs]]
                 [rewrite-clj.zip.whitespace :as zw
                  :refer [append-space skip whitespace-or-comment?]])
       (:require-macros [cljfmt.core :refer [read-resource]])]))

#?(:clj (def read-resource* (comp read-string slurp io/resource)))
#?(:clj (defmacro read-resource [path] `'~(read-resource* path)))

(def zwhitespace?
  #?(:clj z/whitespace? :cljs zw/whitespace?))

(def zlinebreak?
  #?(:clj z/linebreak? :cljs zw/linebreak?))

(def includes?
  #?(:clj  (fn [^String a ^String b] (.contains a b))
     :cljs str/includes?))

(defn- find-all [zloc p?]
  (loop [matches []
         zloc zloc]
    (if-let [zloc (z/find-next zloc zip/next p?)]
      (recur (conj matches zloc)
             (zip/next zloc))
      matches)))

(defn- edit-all [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc zip/next p?)]
      (recur (f zloc))
      zloc)))

(defn- transform [form zf & args]
  (z/root (apply zf (edn form) args)))

(defn- surrounding? [zloc p?]
  (and (p? zloc) (or (nil? (zip/left zloc))
                     (nil? (skip zip/right p? zloc)))))

(defn root? [zloc]
  (nil? (zip/up zloc)))

(defn- top? [zloc]
  (and zloc (not= (z/node zloc) (z/root zloc))))

(defn- top [zloc]
  (if (root? zloc) zloc (recur (z/up zloc))))

(defn- surrounding-whitespace? [zloc]
  (and (top? (z/up zloc))
       (surrounding? zloc zwhitespace?)))

(defn remove-surrounding-whitespace [form]
  (transform form edit-all surrounding-whitespace? zip/remove))

(defn- element? [zloc]
  (and zloc (not (whitespace-or-comment? zloc))))

(defn- reader-macro? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))

(defn- namespaced-map? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :namespaced-map)))

(defn- missing-whitespace? [zloc]
  (and (element? zloc)
       (not (reader-macro? (zip/up zloc)))
       (not (namespaced-map? (zip/up zloc)))
       (element? (zip/right zloc))))

(defn insert-missing-whitespace [form]
  (transform form edit-all missing-whitespace? append-space))

(defn- whitespace? [zloc]
  (= (z/tag zloc) :whitespace))

(defn- comment? [zloc]
  (some-> zloc z/node n/comment?))

(defn- line-break? [zloc]
  (or (zlinebreak? zloc) (comment? zloc)))

(defn- skip-whitespace [zloc]
  (skip zip/next whitespace? zloc))

(defn- count-newlines [zloc]
  (loop [zloc zloc, newlines 0]
    (if (zlinebreak? zloc)
      (recur (-> zloc zip/right skip-whitespace)
             (-> zloc z/string count (+ newlines)))
      newlines)))

(defn- final-transform-element? [zloc]
  (= (z/next zloc) zloc))

(defn- consecutive-blank-line? [zloc]
  (and (> (count-newlines zloc) 2)
       (not (final-transform-element? zloc))))

(defn- remove-whitespace-and-newlines [zloc]
  (if (zwhitespace? zloc)
    (recur (zip/remove zloc))
    zloc))

(defn- replace-consecutive-blank-lines [zloc]
  (-> zloc
      z/next
      zip/prev
      remove-whitespace-and-newlines
      z/next
      (zip/insert-left (n/newlines 2))))

(defn remove-consecutive-blank-lines [form]
  (transform form edit-all consecutive-blank-line? replace-consecutive-blank-lines))

(defn- indentation? [zloc]
  (and (line-break? (zip/prev zloc)) (whitespace? zloc)))

(defn- comment-next? [zloc]
  (-> zloc zip/next skip-whitespace comment?))

(defn- should-indent? [zloc]
  (and (line-break? zloc) (not (comment-next? zloc))))

(defn- should-unindent? [zloc]
  (and (indentation? zloc) (not (comment-next? zloc))))

(defn unindent [form]
  (transform form edit-all should-unindent? zip/remove))

(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"
   :namespaced-map "#"})

(defn- prior-line-string [zloc]
  (loop [zloc     zloc
         worklist '()]
    (if-let [p (zip/left zloc)]
      (let [s            (str (n/string (z/node p)))
            new-worklist (cons s worklist)]
        (if-not (includes? s "\n")
          (recur p new-worklist)
          (apply str new-worklist)))
      (if-let [p (zip/up zloc)]
        ;; newline cannot be introduced by start-element
        (recur p (cons (start-element (n/tag (z/node p))) worklist))
        (apply str worklist)))))

(defn- last-line-in-string [^String s]
  (subs s (inc (.lastIndexOf s "\n"))))

(defn- margin [zloc]
  (-> zloc prior-line-string last-line-in-string count))

(defn- whitespace [width]
  (n/whitespace-node (apply str (repeat width " "))))

(defn- coll-indent [zloc]
  (-> zloc zip/leftmost margin))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (take-while identity)
       (count)
       (dec)))

(defn- list-indent [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc zip/leftmost z/right margin)
    (coll-indent zloc)))

(def indent-size 2)

(defn- indent-width [zloc]
  (case (z/tag zloc)
    :list indent-size
    :fn   (inc indent-size)))

(defn- remove-namespace [x]
  (if (symbol? x) (symbol (name x)) x))

(defn pattern? [v]
  (instance? #?(:clj Pattern :cljs js/RegExp) v))

(defn- top-level-form [zloc]
  (->> zloc
       (iterate z/up)
       (take-while (complement root?))
       last))

(defn- ns-form? [zloc]
  (some-> zloc child-sexprs first (= 'ns)))

(defn- find-namespace [zloc]
  (some-> zloc top (z/find z/next ns-form?) z/sexpr second))

(defn- indent-matches? [key sym]
  (if (symbol? sym)
    (cond
      (symbol? key)  (= key sym)
      (pattern? key) (re-find key (str sym)))))

(defn- token? [zloc]
  (= (z/tag zloc) :token))

(defn- token-value [zloc]
  (if (token? zloc) (z/sexpr zloc)))

(defn- reader-conditional? [zloc]
  (and (reader-macro? zloc) (#{"?" "?@"} (-> zloc z/down token-value str))))

(defn- form-symbol [zloc]
  (-> zloc z/leftmost token-value))

(defn- index-matches-top-argument? [zloc depth idx]
  (and (> depth 0)
       (= (inc idx) (index-of (nth (iterate z/up zloc) depth)))))

(defn- qualify-symbol-by-alias-map [possible-sym alias-map]
  (when-let [ns-str (namespace possible-sym)]
    (symbol (get alias-map ns-str ns-str) (name possible-sym))))

(defn- qualify-symbol-by-ns-form [possible-sym zloc]
  (when-let [ns-name (find-namespace zloc)]
    (symbol (name ns-name) (name possible-sym))))

(defn- fully-qualified-symbol [zloc alias-map]
  (let [possible-sym (form-symbol zloc)]
    (if (symbol? possible-sym)
      (or (qualify-symbol-by-alias-map possible-sym alias-map)
          (qualify-symbol-by-ns-form possible-sym zloc))
      possible-sym)))

(defn- inner-indent [zloc key depth idx alias-map]
  (let [top (nth (iterate z/up zloc) depth)]
    (if (and (or (indent-matches? key (fully-qualified-symbol zloc alias-map))
                 (indent-matches? key (remove-namespace (form-symbol top))))
             (or (nil? idx) (index-matches-top-argument? zloc depth idx)))
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))

(defn- nth-form [zloc n]
  (reduce (fn [z f] (if z (f z)))
          (z/leftmost zloc)
          (repeat n z/right)))

(defn- first-form-in-line? [zloc]
  (and (some? zloc)
       (if-let [zloc (zip/left zloc)]
         (if (whitespace? zloc)
           (recur zloc)
           (or (zlinebreak? zloc) (comment? zloc)))
         true)))

(defn- block-indent [zloc key idx alias-map]
  (if (or (indent-matches? key (fully-qualified-symbol zloc alias-map))
          (indent-matches? key (remove-namespace (form-symbol zloc))))
    (let [zloc-after-idx (some-> zloc (nth-form (inc idx)))]
      (if (and (or (nil? zloc-after-idx) (first-form-in-line? zloc-after-idx))
               (> (index-of zloc) idx))
        (inner-indent zloc key 0 nil alias-map)
        (list-indent zloc)))))

(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.clj")
         (read-resource "cljfmt/indents/compojure.clj")
         (read-resource "cljfmt/indents/fuzzy.clj")))

(defmulti ^:private indenter-fn
  (fn [sym alias-map [type & args]] type))

(defmethod indenter-fn :inner [sym alias-map [_ depth idx]]
  (fn [zloc] (inner-indent zloc sym depth idx alias-map)))

(defmethod indenter-fn :block [sym alias-map [_ idx]]
  (fn [zloc] (block-indent zloc sym idx alias-map)))

(defn- make-indenter [[key opts] alias-map]
  (apply some-fn (map (partial indenter-fn key alias-map) opts)))

(defn- indent-order [[key _]]
  (cond
    (and (symbol? key) (namespace key)) (str 0 key)
    (symbol? key) (str 1 key)
    (pattern? key) (str 2 key)))

(defn- custom-indent [zloc indents alias-map]
  (if (empty? indents)
    (list-indent zloc)
    (let [indenter (->> (sort-by indent-order indents)
                        (map #(make-indenter % alias-map))
                        (apply some-fn))]
      (or (indenter zloc)
          (list-indent zloc)))))

(defn- indent-amount [zloc indents alias-map]
  (let [tag (-> zloc z/up z/tag)
        gp  (-> zloc z/up z/up)]
    (cond
      (reader-conditional? gp) (coll-indent zloc)
      (#{:list :fn} tag)       (custom-indent zloc indents alias-map)
      (= :meta tag)            (indent-amount (z/up zloc) indents alias-map)
      :else                    (coll-indent zloc))))

(defn- indent-line [zloc indents alias-map]
  (let [width (indent-amount zloc indents alias-map)]
    (if (> width 0)
      (zip/insert-right zloc (whitespace width))
      zloc)))

(defn indent
  ([form]
   (indent form default-indents))
  ([form indents]
   (transform form edit-all should-indent? #(indent-line % indents {})))
  ([form indents alias-map]
   (transform form edit-all should-indent? #(indent-line % indents alias-map))))

(defn reindent
  ([form]
   (indent (unindent form)))
  ([form indents]
   (indent (unindent form) indents))
  ([form indents alias-map]
   (indent (unindent form) indents alias-map)))

(defn final? [zloc]
  (and (nil? (zip/right zloc)) (root? (zip/up zloc))))

(defn- trailing-whitespace? [zloc]
  (and (whitespace? zloc)
       (or (zlinebreak? (zip/right zloc)) (final? zloc))))

(defn remove-trailing-whitespace [form]
  (transform form edit-all trailing-whitespace? zip/remove))

(defn reformat-form
  ([form]
   (reformat-form form {}))
  ([form opts]
   (-> form
       (cond-> (:remove-consecutive-blank-lines? opts true)
         remove-consecutive-blank-lines)
       (cond-> (:remove-surrounding-whitespace? opts true)
         remove-surrounding-whitespace)
       (cond-> (:insert-missing-whitespace? opts true)
         insert-missing-whitespace)
       (cond-> (:indentation? opts true)
         (reindent (:indents opts default-indents)
                   (:alias-map opts {})))
       (cond-> (:remove-trailing-whitespace? opts true)
         remove-trailing-whitespace))))

(defn is-shebang?
  [str]
  (str/starts-with? str "#!"))

(defn ignore-form?
  ([form]
   (ignore-form? form {}))
  ([form opts]
   (let [ignore-filters (cond-> []
                          (:ignore-shebang? opts true) (conj is-shebang?))]
     (some true? (map #(%1 form) ignore-filters)))))

#?(:clj
   (defn- ns-require-form? [zloc]
     (and (some-> zloc top-level-form ns-form?)
          (some-> zloc child-sexprs first (= :require)))))

#?(:clj
   (defn- as-keyword? [zloc]
     (and (= :token (z/tag zloc))
          (= :as (z/sexpr zloc)))))

#?(:clj
   (defn- as-zloc->alias-mapping [as-zloc]
     (let [alias             (some-> as-zloc z/right z/sexpr)
           current-namespace (some-> as-zloc z/leftmost z/sexpr)
           grandparent-node  (some-> as-zloc z/up z/up)
           parent-namespace  (when-not (ns-require-form? grandparent-node)
                               (first (z/child-sexprs grandparent-node)))]
       (when (and (symbol? alias) (symbol? current-namespace))
         {(str alias) (if parent-namespace
                        (format "%s.%s" parent-namespace current-namespace)
                        (str current-namespace))}))))

#?(:clj
   (defn- alias-map-for-form [form]
     (when-let [require-zloc (-> form z/edn (z/find z/next ns-require-form?))]
       (->> (find-all require-zloc as-keyword?)
            (map as-zloc->alias-mapping)
            (apply merge)))))

(defn reformat-string
  ([form-string]
   (reformat-string form-string {}))
  ([form-string options]
   (if (ignore-form? form-string options)
     form-string
     (let [parsed-form (p/parse-string-all form-string)
           alias-map   #?(:clj (or (:alias-map options)
                                   (alias-map-for-form parsed-form))
                          :cljs (:alias-map options))]
       (-> parsed-form
           (reformat-form (cond-> options
                            alias-map (assoc :alias-map alias-map)))
           (n/string))))))

(def default-line-separator
  #?(:clj (System/lineSeparator) :cljs \newline))

(defn normalize-newlines [s]
  (str/replace s #"\r\n" "\n"))

(defn replace-newlines [s sep]
  (str/replace s #"\n" sep))

(defn find-line-separator [s]
  (or (re-find #"\r?\n" s) default-line-separator))

(defn wrap-normalize-newlines [f]
  (fn [s]
    (let [sep (find-line-separator s)]
      (-> s normalize-newlines f (replace-newlines sep)))))
