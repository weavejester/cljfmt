(ns cljfmt.core
  #?(:clj (:refer-clojure :exclude [reader-conditional?]))
  (:require #?(:clj [clojure.java.io :as io])
            [clojure.string :as str]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z])
  #?(:clj (:import java.util.regex.Pattern)
     :cljs (:require-macros [cljfmt.core :refer [read-resource]])))

#?(:clj (def read-resource* (comp read-string slurp io/resource)))
#?(:clj (defmacro read-resource [path] `'~(read-resource* path)))

(def includes?
  #?(:clj  (fn [^String a ^String b] (.contains a b))
     :cljs str/includes?))

#?(:clj 
   (defn- find-all [zloc p?]
     (loop [matches []
            zloc zloc]
       (if-let [zloc (z/find-next zloc z/next* p?)]
         (recur (conj matches zloc)
                (z/next* zloc))
         matches))))

(defn- edit-all [zloc p? f]
  (loop [zloc (if (p? zloc) (f zloc) zloc)]
    (if-let [zloc (z/find-next zloc z/next* p?)]
      (recur (f zloc))
      zloc)))

(defn- transform [form zf & args]
  (z/root (apply zf (z/edn form) args)))

(defn- surrounding? [zloc p?]
  (and (p? zloc) (or (nil? (z/left* zloc))
                     (nil? (z/skip z/right* p? zloc)))))

(defn root? [zloc]
  (nil? (z/up* zloc)))

(defn- top? [zloc]
  (and zloc (not= (z/node zloc) (z/root zloc))))

(defn- top [zloc]
  (if (root? zloc) zloc (recur (z/up zloc))))

(defn- clojure-whitespace? [zloc]
  (z/whitespace? zloc))

(defn- surrounding-whitespace? [zloc]
  (and (top? (z/up zloc))
       (surrounding? zloc clojure-whitespace?)))

(defn remove-surrounding-whitespace [form]
  (transform form edit-all surrounding-whitespace? z/remove*))

(defn- element? [zloc]
  (and zloc (not (z/whitespace-or-comment? zloc))))

(defn- reader-macro? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :reader-macro)))

(defn- namespaced-map? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :namespaced-map)))

(defn- missing-whitespace? [zloc]
  (and (element? zloc)
       (not (reader-macro? (z/up* zloc)))
       (not (namespaced-map? (z/up* zloc)))
       (element? (z/right* zloc))))

(defn insert-missing-whitespace [form]
  (transform form edit-all missing-whitespace? z/insert-space-right))

(defn- space? [zloc]
  (= (z/tag zloc) :whitespace))

(defn- comment? [zloc]
  (some-> zloc z/node n/comment?))

(defn- comma? [zloc]
  (some-> zloc z/node n/comma?))

(defn- line-break? [zloc]
  (or (z/linebreak? zloc) (comment? zloc)))

(defn- skip-whitespace [zloc]
  (z/skip z/next* space? zloc))

(defn- skip-whitespace-and-commas [zloc]
  (z/skip z/next* #(or (space? %) (comma? %)) zloc)) 

(defn- skip-clojure-whitespace
  ([zloc] (skip-clojure-whitespace zloc z/next*))
  ([zloc f] (z/skip f clojure-whitespace? zloc)))

(defn- count-newlines [zloc]
  (loop [zloc' zloc, newlines 0]
    (if (z/linebreak? zloc')
      (recur (-> zloc' z/right* skip-whitespace-and-commas)
             (-> zloc' z/string count (+ newlines)))
      (if (comment? (skip-clojure-whitespace zloc z/left*)) 
        (inc newlines)
        newlines))))

(defn- final-transform-element? [zloc]
  (nil? (skip-clojure-whitespace (z/next* zloc))))

(defn- consecutive-blank-line? [zloc]
  (and (> (count-newlines zloc) 2)
       (not (final-transform-element? zloc))))

(defn- remove-clojure-whitespace [zloc]
  (if (clojure-whitespace? zloc)
    (recur (z/remove* zloc))
    zloc))

(defn- replace-consecutive-blank-lines [zloc]
  (let [zloc-elem-before (-> zloc
                             skip-clojure-whitespace
                             z/prev*
                             remove-clojure-whitespace)]
    (-> zloc-elem-before
        z/next*
        (z/insert-left* (n/newlines (if (comment? zloc-elem-before) 1 2))))))

(defn remove-consecutive-blank-lines [form]
  (transform form edit-all consecutive-blank-line? replace-consecutive-blank-lines))

(defn- indentation? [zloc]
  (and (line-break? (z/prev* zloc)) (space? zloc)))

(defn- comment-next? [zloc]
  (-> zloc z/next* skip-whitespace comment?))

(defn- should-indent? [zloc]
  (and (line-break? zloc) (not (comment-next? zloc))))

(defn- should-unindent? [zloc]
  (and (indentation? zloc) (not (comment-next? zloc))))

(defn unindent [form]
  (transform form edit-all should-unindent? z/remove*))

(def ^:private start-element
  {:meta "^", :meta* "#^", :vector "[",       :map "{"
   :list "(", :eval "#=",  :uneval "#_",      :fn "#("
   :set "#{", :deref "@",  :reader-macro "#", :unquote "~"
   :var "#'", :quote "'",  :syntax-quote "`", :unquote-splicing "~@"
   :namespaced-map "#"})

(defn- prior-line-string [zloc]
  (loop [zloc     zloc
         worklist '()]
    (if-let [p (z/left* zloc)]
      (let [s            (str (n/string (z/node p)))
            new-worklist (cons s worklist)]
        (if-not (includes? s "\n")
          (recur p new-worklist)
          (apply str new-worklist)))
      (if-let [p (z/up* zloc)]
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
  (-> zloc z/leftmost* margin))

(defn- uneval? [zloc]
  (= (z/tag zloc) :uneval))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (remove uneval?)
       (take-while identity)
       (count)
       (dec)))

(defn- list-indent [zloc]
  (if (> (index-of zloc) 1)
    (-> zloc z/leftmost* z/right margin)
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

#?(:clj
   (defn- top-level-form [zloc]
     (->> zloc
          (iterate z/up)
          (take-while (complement root?))
          last)))

(defn- token? [zloc]
  (= (z/tag zloc) :token))

(defn- ns-token? [zloc]
  (and (token? zloc)
       (= 'ns (z/sexpr zloc))))

(defn- ns-form? [zloc]
  (some-> zloc z/down ns-token?))

(defn- indent-matches? [key sym]
  (if (symbol? sym)
    (cond
      (symbol? key)  (= key sym)
      (pattern? key) (re-find key (str sym)))))

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

(defn- qualify-symbol-by-ns-name [possible-sym ns-name]
  (when ns-name
    (symbol (name ns-name) (name possible-sym))))

(defn- fully-qualified-symbol [zloc context]
  (let [possible-sym (form-symbol zloc)]
    (if (symbol? possible-sym)
      (or (qualify-symbol-by-alias-map possible-sym (:alias-map context))
          (qualify-symbol-by-ns-name possible-sym (:ns-name context)))
      possible-sym)))

(defn- inner-indent [zloc key depth idx context]
  (let [top (nth (iterate z/up zloc) depth)]
    (if (and (or (indent-matches? key (fully-qualified-symbol zloc context))
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
       (if-let [zloc (z/left* zloc)]
         (if (space? zloc)
           (recur zloc)
           (or (z/linebreak? zloc) (comment? zloc)))
         true)))

(defn- block-indent [zloc key idx context]
  (if (or (indent-matches? key (fully-qualified-symbol zloc context))
          (indent-matches? key (remove-namespace (form-symbol zloc))))
    (let [zloc-after-idx (some-> zloc (nth-form (inc idx)))]
      (if (and (or (nil? zloc-after-idx) (first-form-in-line? zloc-after-idx))
               (> (index-of zloc) idx))
        (inner-indent zloc key 0 nil context)
        (list-indent zloc)))))

(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.clj")
         (read-resource "cljfmt/indents/compojure.clj")
         (read-resource "cljfmt/indents/fuzzy.clj")))

(defmulti ^:private indenter-fn
  (fn [sym context [type & args]] type))

(defmethod indenter-fn :inner [sym context [_ depth idx]]
  (fn [zloc] (inner-indent zloc sym depth idx context)))

(defmethod indenter-fn :block [sym context [_ idx]]
  (fn [zloc] (block-indent zloc sym idx context)))

(defn- make-indenter [[key opts] context]
  (apply some-fn (map (partial indenter-fn key context) opts)))

(defn- indent-order [[key _]]
  (cond
    (and (symbol? key) (namespace key)) (str 0 key)
    (symbol? key) (str 1 key)
    (pattern? key) (str 2 key)))

(defn- custom-indent [zloc indents context]
  (if (empty? indents)
    (list-indent zloc)
    (let [indenter (->> (sort-by indent-order indents)
                        (map #(make-indenter % context))
                        (apply some-fn))]
      (or (indenter zloc)
          (list-indent zloc)))))

(defn- indent-amount [zloc indents context]
  (let [tag (-> zloc z/up z/tag)
        gp  (-> zloc z/up z/up)]
    (cond
      (reader-conditional? gp) (coll-indent zloc)
      (#{:list :fn} tag)       (custom-indent zloc indents context)
      (= :meta tag)            (indent-amount (z/up zloc) indents context)
      :else                    (coll-indent zloc))))

(defn- indent-line [zloc indents context]
  (let [width (indent-amount zloc indents context)]
    (if (> width 0)
      (z/insert-right* zloc (whitespace width))
      zloc)))

(defn- find-namespace [zloc]
  (some-> zloc top (z/find z/next ns-form?) z/down z/next z/sexpr))

(defn indent
  ([form]
   (indent form default-indents {}))
  ([form indents]
   (indent form indents {}))
  ([form indents alias-map]
   (let [ns-name (find-namespace (z/edn form))]
     (transform form edit-all should-indent? 
                #(indent-line % indents {:alias-map alias-map 
                                         :ns-name ns-name})))))

(defn- map-key? [zloc]
  (and (z/map? (z/up zloc))
       (even? (index-of zloc))
       (not (uneval? zloc))
       (not (z/whitespace-or-comment? zloc))))

(defn- preceded-by-line-break? [zloc]
  (loop [previous (z/left* zloc)]
    (cond
      (line-break? previous)
      true
      (z/whitespace-or-comment? previous)
      (recur (z/left* previous)))))

(defn- map-key-without-line-break? [zloc]
  (and (map-key? zloc) (not (preceded-by-line-break? zloc))))

(defn- insert-newline-left [zloc]
  (z/insert-left* zloc (n/newlines 1)))

(defn split-keypairs-over-multiple-lines [form]
  (transform form edit-all map-key-without-line-break? insert-newline-left))

(defn reindent
  ([form]
   (indent (unindent form)))
  ([form indents]
   (indent (unindent form) indents))
  ([form indents alias-map]
   (indent (unindent form) indents alias-map)))

(defn final? [zloc]
  (and (nil? (z/right* zloc)) (root? (z/up* zloc))))

(defn- trailing-whitespace? [zloc]
  (and (space? zloc)
       (or (z/linebreak? (z/right* zloc)) (final? zloc))))

(defn remove-trailing-whitespace [form]
  (transform form edit-all trailing-whitespace? z/remove*))

(defn- replace-with-one-space [zloc]
  (z/replace* zloc (whitespace 1)))

(defn- non-indenting-whitespace? [zloc]
  (and (space? zloc) (not (indentation? zloc))))

(defn remove-multiple-non-indenting-spaces [form]
  (transform form edit-all non-indenting-whitespace? replace-with-one-space))

(defn reformat-form
  ([form]
   (reformat-form form {}))
  ([form opts]
   (-> form
       (cond-> (:split-keypairs-over-multiple-lines? opts false)
         (split-keypairs-over-multiple-lines))
       (cond-> (:remove-consecutive-blank-lines? opts true)
         remove-consecutive-blank-lines)
       (cond-> (:remove-surrounding-whitespace? opts true)
         remove-surrounding-whitespace)
       (cond-> (:insert-missing-whitespace? opts true)
         insert-missing-whitespace)
       (cond-> (:remove-multiple-non-indenting-spaces? opts false)
         remove-multiple-non-indenting-spaces)
       (cond-> (:indentation? opts true)
         (reindent (:indents opts default-indents)
                   (:alias-map opts {})))
       (cond-> (:remove-trailing-whitespace? opts true)
         remove-trailing-whitespace))))

#?(:clj
   (defn- ns-require-form? [zloc]
     (and (some-> zloc top-level-form ns-form?)
          (some-> zloc z/child-sexprs first (= :require)))))

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
   (let [parsed-form (p/parse-string-all form-string)
         alias-map   #?(:clj (or (:alias-map options)
                                 (alias-map-for-form parsed-form))
                        :cljs (:alias-map options))]
     (-> parsed-form
         (reformat-form (cond-> options
                          alias-map (assoc :alias-map alias-map)))
         (n/string)))))

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
