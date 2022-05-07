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
  (some-> zloc z/up root?))

(defn- root [zloc]
  (if (root? zloc) zloc (recur (z/up zloc))))

(defn- clojure-whitespace? [zloc]
  (z/whitespace? zloc))

(defn- surrounding-whitespace? [zloc]
  (and (not (top? zloc))
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
  (and (top? zloc)
       (= (z/tag zloc) :list)
       (some-> zloc z/down ns-token?)))

(defn- token-value [zloc]
  (when (token? zloc) (z/sexpr zloc)))

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

(defn- fully-qualified-symbol [possible-sym context]
  (if (symbol? possible-sym)
    (or (qualify-symbol-by-alias-map possible-sym (:alias-map context))
        (qualify-symbol-by-ns-name possible-sym (:ns-name context)))
    possible-sym))

(defn- inner-indent [depth idx zloc]
  (when (or (nil? idx) (index-matches-top-argument? zloc depth idx))
    (let [zup (z/up zloc)]
      (+ (margin zup) (indent-width zup)))))

(defn- nth-form [zloc n]
  (reduce (fn [z f] (when z (f z)))
          (z/leftmost zloc)
          (repeat n z/right)))

(defn- first-form-in-line? [zloc]
  (and (some? zloc)
       (if-let [zloc (z/left* zloc)]
         (if (space? zloc)
           (recur zloc)
           (or (z/linebreak? zloc) (comment? zloc)))
         true)))

(defn- block-indent [idx zloc]
    (let [zloc-after-idx (some-> zloc (nth-form (inc idx)))]
      (if (and (or (nil? zloc-after-idx) (first-form-in-line? zloc-after-idx))
               (> (index-of zloc) idx))
        (inner-indent 0 nil zloc)
        (list-indent zloc))))

(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.clj")
         (read-resource "cljfmt/indents/compojure.clj")
         (read-resource "cljfmt/indents/fuzzy.clj")))

(defmulti ^:private indenter-fn first)

(defmethod indenter-fn :inner [[_ depth idx]]
  (partial inner-indent depth idx))

(defmethod indenter-fn :block [[_ idx]]
  (partial block-indent idx))

(defmulti ^:private lookup-indent-rules-for-form-symbols
  (fn [symbols [section indent-map]] section))

(defmethod lookup-indent-rules-for-form-symbols :by-symbol [symbols [_ indent-map]]
  (mapcat (partial get indent-map) symbols))

(defmethod lookup-indent-rules-for-form-symbols :by-pattern [symbols [_ indent-map]]
  (mapcat (fn [sym]
            (->> indent-map
                 (filter (fn [[pattern rules]]
                           (re-find pattern (str sym))))
                 (mapcat second)))
          symbols))

(defn- applicable-indent-rules [zloc indents context]
  (let [form-symbol-parents (->> zloc (iterate z/up) (map form-symbol))
        qualified-form-symbol-parents (map #(fully-qualified-symbol % context) form-symbol-parents)
        unqualified-form-symbol-parents (map remove-namespace form-symbol-parents)
        relevant-symbols-at-depth (map (fn [depth]
                                         (->> [(nth qualified-form-symbol-parents depth)
                                               (nth unqualified-form-symbol-parents depth)]
                                              (filter symbol?)
                                              set))
                                       (range))]
    (mapcat
     (fn [[depth sections]]
       (mapcat
        (partial lookup-indent-rules-for-form-symbols (nth relevant-symbols-at-depth depth))
        sections))
     indents)))

(defn- indent-order [{:keys [key-type key rule-index]}]
  ;; TODO: this preserves the behaviour so far, but it might be better to take
  ;; depth into account as well
  [(case key-type
     :namespaced-symbol 0
     :symbol 1
     :pattern 2)
   (str key)
   rule-index])

(defn- applicable-indenter-fns [zloc indents context]
  (->> (applicable-indent-rules zloc indents context)
       (sort-by indent-order)
       (map (comp indenter-fn :rule))))

(defn- custom-indent [zloc indents context]
  (let [fns (concat (applicable-indenter-fns zloc indents context)
                    [list-indent])]
    ((apply some-fn fns) zloc)))

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
  (some-> zloc root z/down (z/find z/right ns-form?) z/down z/next z/sexpr))

(defn- indent-rule-key-type [key]
  (cond
    (and (symbol? key) (namespace key)) :namespaced-symbol
    (symbol? key) :symbol
    :else :pattern))

(defn- build-indent-rule-map [indents]
  ;; prepare look up maps for various depths, based on what indent rules will
  ;; need to be matched, e.g.
  ;; {'defprotocol [[:block 1] [:inner 1]]
  ;;  'defstruct   [[:block 1]]
  ;;  'deftest     [[:inner 0]]
  ;;  #"^with-"    [[:inner 0]]}
  ;; will become
  ;; {0 {:by-symbol {'defprotocol ({:depth 0,
  ;;                                :key-type :symbol,
  ;;                                :key 'defprotocol,
  ;;                                :rule-index 0,
  ;;                                :rule [:block 1]}),
  ;;                 'defstruct   ({:depth 0,
  ;;                                :key-type :symbol,
  ;;                                :key 'defstruct,
  ;;                                :rule-index 0,
  ;;                                :rule [:block 1]}),
  ;;                 'deftest     ({:depth 0,
  ;;                                :key-type :symbol,
  ;;                                :key 'deftest,
  ;;                                :rule-index 0,
  ;;                                :rule [:inner 0]}) }
  ;;     :by-pattern {#"^with-" ({:depth 0,
  ;;                              :key-type :patrule-tern,
  ;;                              :key #"^wirule-th-",
  ;;                              :rule-indrule-ex 0,
  ;;                              :rule [:inner 0]})}},
  ;;  1 {:by-symbol {'defprotocol ({:depth 1,
  ;;                                :key-type :symbol,
  ;;                                :key 'defprotocol,
  ;;                                :rule-index 1,
  ;;                                :rule [:inner 1]})}}}
  ;; which allows finding rules for a given symbol and depth efficiently
  (->> (for [[key rules] indents
             [rule-index rule] (map-indexed vector rules)]
         [key rule-index rule])
       (reduce (fn [result [key rule-index [type :as rule]]]
                 (let [key-type (indent-rule-key-type key)
                       depth (case type
                               :inner (second rule)
                               :block 0)]
                   (update-in result
                              [depth (if (= key-type :pattern) :by-pattern :by-symbol) key]
                              conj
                              {:depth depth
                               :key-type key-type
                               :key key
                               :rule-index rule-index
                               :rule rule})))
               {})))

(defn indent
  ([form]
   (indent form default-indents {}))
  ([form indents]
   (indent form indents {}))
  ([form indents alias-map]
   (let [ns-name (find-namespace (z/edn form))
         prepared-indents (build-indent-rule-map indents)]
     (transform form edit-all should-indent?
                #(indent-line % prepared-indents {:alias-map alias-map
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

(def default-options
  {:indentation?                          true
   :insert-missing-whitespace?            true
   :remove-consecutive-blank-lines?       true
   :remove-multiple-non-indenting-spaces? false
   :remove-surrounding-whitespace?        true
   :remove-trailing-whitespace?           true
   :split-keypairs-over-multiple-lines?   false
   :indents   default-indents
   :alias-map {}})

(defn reformat-form
  ([form]
   (reformat-form form {}))
  ([form opts]
   (let [opts (merge default-options opts)]
     (-> form
         (cond-> (:split-keypairs-over-multiple-lines? opts)
           (split-keypairs-over-multiple-lines))
         (cond-> (:remove-consecutive-blank-lines? opts)
           remove-consecutive-blank-lines)
         (cond-> (:remove-surrounding-whitespace? opts)
           remove-surrounding-whitespace)
         (cond-> (:insert-missing-whitespace? opts)
           insert-missing-whitespace)
         (cond-> (:remove-multiple-non-indenting-spaces? opts)
           remove-multiple-non-indenting-spaces)
         (cond-> (:indentation? opts)
           (reindent (:indents opts) (:alias-map opts)))
         (cond-> (:remove-trailing-whitespace? opts)
           remove-trailing-whitespace)))))

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
