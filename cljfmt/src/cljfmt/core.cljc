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

(defn- edit-all
  ([zloc p? f]
   (edit-all zloc p? f z/next*))
  ([zloc p? f nextf]
   (loop [zloc (if (p? zloc) (f zloc) zloc)]
     (if-let [zloc (z/find-next zloc nextf p?)]
       (recur (f zloc))
       zloc))))

(defn- transform [form zf & args]
  (z/root (apply zf (z/of-node form) args)))

(defn root? [zloc]
  (nil? (z/up* zloc)))

(defn- top? [zloc]
  (some-> zloc z/up root?))

(defn- root [zloc]
  (if (root? zloc) zloc (recur (z/up zloc))))

(defn- clojure-whitespace? [zloc]
  (z/whitespace? zloc))

(defn- unquote? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :unquote)))

(defn- deref? [zloc]
  (and zloc (= (n/tag (z/node zloc)) :deref)))

(defn- unquote-deref? [zloc]
  (and (deref? zloc)
       (unquote? (z/up* zloc))))

(defn- comment? [zloc]
  (some-> zloc z/node n/comment?))

(defn- uneval? [zloc]
  (= (z/tag zloc) :uneval))

(defn- surrounding-whitespace? [zloc opts]
  (and (not (top? zloc))
       (clojure-whitespace? zloc)
       (not (and (:ignore-lines-with-only-uneval-tags? opts)
                 (z/linebreak? zloc)
                 (nil? (z/left* zloc))
                 (uneval? (z/up* zloc))))
       (or (and (nil? (z/left* zloc))
                ;; don't convert ~ @ to ~@
                (not (unquote-deref? (z/right* zloc)))
                ;; ignore space before comments
                (not (comment? (z/right* zloc))))
           (nil? (z/skip z/right* clojure-whitespace? zloc)))))

(defn remove-surrounding-whitespace [form opts]
  (transform form edit-all #(surrounding-whitespace? % opts) z/remove*))

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

(defn- line-comment? [zloc]
  (and (comment? zloc) (re-matches #"(?s);;([^;].*)?" (z/string zloc))))

(defn- comma? [zloc]
  (some-> zloc z/node n/comma?))

(defn- line-break? [zloc]
  (or (z/linebreak? zloc) (comment? zloc)))

(defn- skip-whitespace [zloc]
  (z/skip z/next* space? zloc))

(defn- skip-whitespace-and-commas
  ([zloc] (skip-whitespace-and-commas zloc z/next*))
  ([zloc f] (z/skip f (some-fn space? comma?) zloc)))

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
  (and (line-break? (z/left* zloc)) (space? zloc)))

(defn- comment-next? [zloc]
  (-> zloc z/next* skip-whitespace comment?))

(defn- comment-next-other-than-line-comment? [zloc]
  (when-let [znext (-> zloc z/next* skip-whitespace)]
    (and (comment? znext) (not (line-comment? znext)))))

(defn- should-indent? [zloc opts]
  (and (line-break? zloc)
       (if (:indent-line-comments? opts)
         (not (comment-next-other-than-line-comment? zloc))
         (not (comment-next? zloc)))))

(defn- should-unindent? [zloc opts]
  (and (indentation? zloc)
       (if (:indent-line-comments? opts)
         (not (comment-next-other-than-line-comment? zloc))
         (not (comment-next? zloc)))))

(defn unindent
  ([form]
   (unindent form {}))
  ([form opts]
   (transform form edit-all #(should-unindent? % opts) z/remove*)))

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

(defn- last-line-in-string ^String [^String s]
  (subs s (inc (.lastIndexOf s "\n"))))

(defn- margin [zloc]
  (-> zloc prior-line-string last-line-in-string count))

(defn- whitespace [width]
  (n/whitespace-node (apply str (repeat width " "))))

(defn- coll-indent [zloc]
  (-> zloc z/leftmost* margin))

(defn- index-of [zloc]
  (->> (iterate z/left zloc)
       (remove uneval?)
       (take-while identity)
       (count)
       (dec)))

(defn- skip-meta [zloc]
  (if (#{:meta :meta*} (z/tag zloc))
    (-> zloc z/down z/right)
    zloc))

(defn- cursive-two-space-list-indent? [zloc]
  (-> zloc z/leftmost* skip-meta z/tag #{:vector :map :list :set} not))

(defn- zprint-two-space-list-indent? [zloc]
  (-> zloc z/leftmost* z/tag #{:token :list}))

(defn two-space-list-indent? [zloc context]
  (case (:function-arguments-indentation context)
    :community false
    :cursive (cursive-two-space-list-indent? zloc)
    :zprint (zprint-two-space-list-indent? zloc)))

(defn- first-form-in-line? [zloc]
  (and (some? zloc)
       (if-let [zloc (z/left* zloc)]
         (if (space? zloc)
           (recur zloc)
           (or (z/linebreak? zloc) (comment? zloc)))
         true)))

(defn- list-indent [zloc context]
  (let [idx (index-of zloc)
        first-arg (-> zloc
                      z/leftmost*
                      z/right)
        ;; Check if there's an uneval before this element
        has-uneval-before? (and (= idx 1)
                                (some-> zloc
                                        z/left*
                                        uneval?))]
    (if (or (> idx 1) has-uneval-before?)
      ;;  If index > 1, or if at index 1 but there's an uneval before us,
      ;; align with the first argument position
      (if (uneval? first-arg)
        ;; If uneval is first on its own line, its child is already at the
        ;; correct margin. Otherwise, add space after the #_ token.
        (if (first-form-in-line? first-arg)
          (margin first-arg)
          (inc (margin first-arg)))
        (margin first-arg))
      (cond-> (coll-indent zloc)
        (two-space-list-indent? zloc context) inc))))

(def indent-size 2)

(defn- indent-width [zloc]
  (case (z/tag zloc)
    :list indent-size
    :fn   (inc indent-size)))

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
  (let [zloc (skip-meta zloc)]
    (when (token? zloc) (z/sexpr zloc))))

(defn- reader-conditional? [zloc]
  (and (reader-macro? zloc) (#{"?" "?@"} (-> zloc z/down token-value str))))

(defn- find-next-keyword [zloc]
  (z/find zloc z/right #(n/keyword-node? (z/node %))))

(defn- first-symbol-in-reader-conditional [zloc]
  (when (reader-conditional? zloc)
    (when-let [key-loc (-> zloc z/down z/right z/down find-next-keyword)]
      (when-let [value-loc (-> key-loc z/next skip-meta)]
        (when (token? value-loc)
          (z/sexpr value-loc))))))

(defn- form-symbol [zloc]
  (let [zloc (z/leftmost zloc)
        sym  (or (token-value zloc)
                 (first-symbol-in-reader-conditional zloc))]
    (when (symbol? sym) sym)))

(defn- index-matches-top-argument? [zloc depth idx]
  (and (> depth 0)
       (= (inc idx) (index-of (nth (iterate z/up zloc) depth)))))

(defn- qualify-symbol-by-alias-map [possible-sym alias-map]
  (when-let [ns-str (namespace possible-sym)]
    (symbol (get alias-map ns-str ns-str) (name possible-sym))))

(defn- qualify-symbol-by-ns-name [sym ns-name]
  (when ns-name (symbol (name ns-name) (name sym))))

(defn- fully-qualified-symbol [sym context]
  (or (qualify-symbol-by-alias-map sym (:alias-map context))
      (qualify-symbol-by-ns-name sym (:ns-name context))))

(defn- string-matches-key-part? [s key-part]
  (if (pattern? key-part) (re-find key-part s) (= s (name key-part))))

(defn- parts-match-vector-key? [sym-ns sym-name [ns-key name-key]]
  (and (string-matches-key-part? sym-ns ns-key)
       (string-matches-key-part? sym-name name-key)))

(defn- form-matches-key? [zloc key context]
  (when-some [sym (form-symbol zloc)]
    (let [full-sym (fully-qualified-symbol sym context)
          sym-name (name sym)
          sym-ns   (or (some-> full-sym namespace) (namespace sym))]
      (cond
        (vector? key)           (parts-match-vector-key? sym-ns sym-name key)
        (pattern? key)          (re-find key sym-name)
        (qualified-symbol? key) (= key full-sym)
        (symbol? key)           (= (name key) sym-name)))))

(defn- inner-indent [zloc key depth idx context]
  (let [top (nth (iterate z/up zloc) depth)]
    (when (and (z/left zloc)
               (form-matches-key? top key context)
               (or (nil? idx) (index-matches-top-argument? zloc depth idx)))
      (let [zup (z/up zloc)]
        (+ (margin zup) (indent-width zup))))))

(defn- nth-form [zloc n]
  (reduce (fn [z f] (when z (f z)))
          (z/leftmost zloc)
          (repeat n z/right)))

(defn- block-indent [zloc key idx context]
  (when (form-matches-key? zloc key context)
    (let [zloc-after-idx (some-> zloc (nth-form (inc idx)))]
      (if (and (or (nil? zloc-after-idx) (first-form-in-line? zloc-after-idx))
               (> (index-of zloc) idx))
        (inner-indent zloc key 0 nil context)
        (list-indent zloc context)))))

(def default-indents
  (merge (read-resource "cljfmt/indents/clojure.clj")
         (read-resource "cljfmt/indents/compojure.clj")
         (read-resource "cljfmt/indents/fuzzy.clj")))

(def default-aligned-forms
  (read-resource "cljfmt/aligned_forms/clojure.clj"))

(def blank-line-forms
  (read-resource "cljfmt/blank_line_forms/clojure.clj"))

(def default-options
  {:alias-map                             {}
   :align-binding-columns?                false
   :align-map-columns?                    false
   :align-single-column-lines?            false
   :aligned-forms                         default-aligned-forms
   :blank-line-forms                      blank-line-forms
   :extra-aligned-forms                   {}
   :extra-blank-line-forms                {}
   :extra-indents                         {}
   :function-arguments-indentation        :community
   :ignore-lines-with-only-uneval-tags?   true
   :indent-line-comments?                 false
   :indentation?                          true
   :indents                               default-indents
   :normalize-newlines-at-file-end?       false
   :insert-missing-whitespace?            true
   :remove-blank-lines-in-forms?          false
   :remove-consecutive-blank-lines?       true
   :remove-multiple-non-indenting-spaces? false
   :remove-surrounding-whitespace?        true
   :remove-trailing-whitespace?           true
   :sort-ns-references?                   false
   :split-keypairs-over-multiple-lines?   false})

(defmulti ^:private indenter-fn
  (fn [_sym _context [type & _args]] type))

(defmethod indenter-fn :inner [sym context [_ depth idx]]
  (fn [zloc] (inner-indent zloc sym depth idx context)))

(defmethod indenter-fn :block [sym context [_ idx]]
  (fn [zloc] (block-indent zloc sym idx context)))

(defmethod indenter-fn :default [sym context [_]]
  (fn [zloc]
    (when (form-matches-key? zloc sym context)
      (list-indent zloc context))))

(defn- make-indenter [[key opts] context]
  (apply some-fn (map (partial indenter-fn key context) opts)))

(defn- indent-order [[key specs]]
  (let [get-depth (fn [[type depth]] (if (= type :inner) depth 0))
        max-depth (transduce (map get-depth) max 0 specs)
        key-order  (cond
                     (qualified-symbol? key) 0
                     (simple-symbol? key)    1
                     (pattern? key)          2)]
    [(- max-depth) key-order (str key)]))

(defn- custom-indent [zloc indents context]
  (if (empty? indents)
    (list-indent zloc context)
    (let [indenter (->> indents
                        (map #(make-indenter % context))
                        (apply some-fn))]
      (or (indenter zloc)
          (list-indent zloc context)))))

(defn- indent-amount [zloc indents context]
  (let [tag (-> zloc z/up z/tag)
        gp  (-> zloc z/up z/up)]
    (cond
      (reader-conditional? gp) (coll-indent zloc)
      (#{:list :fn} tag)       (custom-indent zloc indents context)
      (= :meta tag)            (indent-amount (z/up zloc) indents context)
      ;; For uneval: if it's the first element on the line, don't indent
      ;; the child form. Otherwise, indent to align after the #_ token.
      (= :uneval tag)          (if (first-form-in-line? (z/up zloc))
                                 (margin (z/up zloc))
                                 (inc (margin (z/up zloc))))
      :else                    (coll-indent zloc))))

(defn- indent-line [zloc indents context]
  (let [width (indent-amount zloc indents context)]
    (if (> width 0)
      (z/insert-right* zloc (whitespace width))
      zloc)))

(defn- find-namespace [zloc]
  (some-> zloc root z/down (z/find z/right ns-form?) z/down z/next z/sexpr))

(defn indent
  ([form]
   (indent form default-indents {}))
  ([form indents]
   (indent form indents {}))
  ([form indents alias-map]
   (indent form indents alias-map default-options))
  ([form indents alias-map opts]
   (let [ns-name (find-namespace (z/of-node form))
         sorted-indents (sort-by indent-order indents)
         context (merge (select-keys opts [:function-arguments-indentation])
                        {:alias-map alias-map
                         :ns-name ns-name})]
     (transform form edit-all #(should-indent? % opts)
                #(indent-line % sorted-indents context)))))

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
   (indent (unindent form) indents alias-map))
  ([form indents alias-map opts]
   (indent (unindent form opts) indents alias-map opts)))

(defn final? [zloc]
  (and (nil? (z/right* zloc)) (root? (z/up* zloc))))

(defn- trailing-whitespace? [zloc]
  (and (space? zloc)
       (or (z/linebreak? (z/right* zloc)) (final? zloc))))

(defn remove-trailing-whitespace [form]
  (transform form edit-all trailing-whitespace? z/remove*))

(defn normalize-newlines-at-file-end [s]
  (cond-> (str/trimr s)
    (not (str/blank? s)) (str "\n")))

(defn- replace-with-one-space [zloc]
  (z/replace* zloc (whitespace 1)))

(defn- non-indenting-whitespace? [zloc]
  (and (space? zloc)
       (not (indentation? zloc))
       (not (comment? (z/right* zloc)))))

(defn remove-multiple-non-indenting-spaces [form]
  (transform form edit-all non-indenting-whitespace? replace-with-one-space))

(def ^:private ns-reference-symbols
  #{:import :require :require-macros :use})

(defn- ns-reference? [zloc]
  (and (z/list? zloc)
       (some-> zloc z/up ns-form?)
       (-> zloc z/sexpr first ns-reference-symbols)))

(defn- re-indexes [re s]
  (let [matcher    #?(:clj  (re-matcher re s)
                      :cljs (js/RegExp. (.-source re) "g"))
        next-match #?(:clj  #(when (.find matcher)
                               [(.start matcher) (.end matcher)])
                      :cljs #(when-let [result (.exec matcher s)]
                               [(.-index result) (.-lastIndex matcher)]))]
    (take-while some? (repeatedly next-match))))

(defn- re-seq-matcher [re charmap coll]
  {:pre (every? charmap coll)}
  (let [s (apply str (map charmap coll))
        v (vec coll)]
    (for [[start end] (re-indexes re s)]
      {:value (subvec v start end)
       :start start
       :end   end})))

(defn- find-elements-with-comments [nodes]
  (re-seq-matcher #"(CNS*)*E(S*C)?"
                  #(case (n/tag %)
                     (:whitespace :comma) \S
                     :comment \C
                     :newline \N
                     \E)
                  nodes))

(defn- splice-into [coll splices]
  (letfn [(splice [v i splices]
            (when-let [[{:keys [value start end]} & splices] (seq splices)]
              (lazy-cat (subvec v i start) value (splice v end splices))))]
    (splice (vec coll) 0 splices)))

(defn- add-newlines-after-comments [nodes]
  (mapcat #(if (n/comment? %) [% (n/newlines 1)] [%]) nodes))

(defn- remove-newlines-after-comments [nodes]
  (mapcat #(when-not (and %1 (n/comment? %1) (n/linebreak? %2)) [%2])
          (cons nil nodes)
          nodes))

(defn- sort-node-arguments-by [f nodes]
  (let [nodes  (add-newlines-after-comments nodes)
        args   (rest (find-elements-with-comments nodes))
        sorted (sort-by f (map :value args))]
    (->> sorted
         (map #(assoc %1 :value %2) args)
         (splice-into nodes)
         (remove-newlines-after-comments))))

(defn- update-children [zloc f]
  (let [node (z/node zloc)]
    (z/replace zloc (n/replace-children node (f (n/children node))))))

(defn- nodes-string [nodes]
  (apply str (map n/string nodes)))

(defn- remove-node-metadata [nodes]
  (mapcat #(if (= (n/tag %) :meta)
             (rest (n/children %))
             [%])
          nodes))

(defn- node-sort-string [nodes]
  (-> (remove (some-fn n/comment? n/whitespace?) nodes)
      (remove-node-metadata)
      (nodes-string)
      (str/replace #"[\[\]\(\)\{\}]" "")
      (str/trim)
      (str/lower-case)))

(defn sort-arguments [zloc]
  (update-children zloc #(sort-node-arguments-by node-sort-string %)))

(defn sort-ns-references [form]
  (transform form edit-all ns-reference? sort-arguments))

(defn- reduce-columns [zloc f init]
  (loop [zloc zloc, col 0, acc init]
    (if-some [zloc (skip-whitespace-and-commas zloc z/right*)]
      (if (line-break? zloc)
        (recur (z/right* zloc) 0 acc)
        (recur (z/right* zloc) (inc col) (f zloc col acc)))
      acc)))

(defn- count-columns [zloc]
  (inc (reduce-columns zloc #(max %2 %3) 0)))

(defn- trailing-commas [zloc]
  (let [right (z/right* zloc)]
    (if (and right (comma? right))
      (-> right z/node n/string)
      "")))

(defn- node-end-position [zloc]
  (let [lines (str (prior-line-string zloc)
                   (n/string (z/node zloc))
                   (trailing-commas zloc))]
    (transduce (comp (remove #(str/starts-with? % ";"))
                     (map count))
               max 0 (str/split lines #"\r?\n"))))

(defn- single-column-line? [zloc]
  (and (line-break? (skip-whitespace-and-commas (z/right* zloc) z/right*))
       (line-break? (skip-whitespace-and-commas (z/left* zloc) z/left*))))

(defn- max-column-end-position [zloc col align-single-column-lines?]
  (reduce-columns zloc
                  (fn [zloc c max-pos]
                    (if (and (= c col)
                             (or align-single-column-lines?
                                 (not (single-column-line? zloc))))
                      (max max-pos (node-end-position zloc))
                      max-pos))
                  0))

(defn- node-str-length [zloc]
  (-> zloc z/node n/string count))

(defn- update-space-left [zloc delta]
  (let [left (z/left* zloc)]
    (cond
      (space? left) (let [n (max 0 (+ delta (node-str-length left)))]
                      (z/right* (z/replace* left (n/spaces n))))
      (pos? delta)  (z/insert-space-left zloc delta)
      :else         zloc)))

(defn- nil-if-end [zloc]
  (when (and zloc (not (z/end? zloc))) zloc))

(defn- skip-to-next-line [zloc]
  (->> zloc (z/skip z/next* (complement line-break?)) z/next nil-if-end))

(defn- pad-inside-node [zloc padding]
  (if-some [zloc (z/down zloc)]
    (loop [zloc zloc]
      (if-some [zloc (skip-to-next-line zloc)]
        (recur (update-space-left zloc padding))
        zloc))
    zloc))

(defn- pad-node [zloc padding]
  (-> (update-space-left zloc padding)
      (z/subedit-> (pad-inside-node padding))))

(defn- pad-to-position [zloc start-position]
  (pad-node zloc (- start-position (margin zloc))))

(defn- edit-column [zloc column f]
  (loop [zloc zloc, col 0]
    (if-some [zloc (skip-whitespace-and-commas zloc z/right*)]
      (let [zloc (if (and (= col column) (not (line-break? zloc)))
                   (f zloc)
                   zloc)
            col  (if (line-break? zloc) 0 (inc col))]
        (if-some [zloc (z/right* zloc)]
          (recur zloc col)
          zloc))
      zloc)))

(defn- align-one-column [zloc col align-single-column-lines?]
  (if-some [zloc (z/down zloc)]
    (let [start-position (inc (max-column-end-position
                               zloc (dec col) align-single-column-lines?))]
      (z/up (edit-column zloc col #(pad-to-position % start-position))))
    zloc))

(defn- align-columns [zloc align-single-column-lines?]
  (reduce #(align-one-column %1 %2 align-single-column-lines?)
          zloc
          (-> zloc z/down count-columns range rest)))

(defn align-map-columns
  ([form]
   (align-map-columns form default-options))
  ([form {:keys [align-single-column-lines?]}]
   (let [align #(align-columns % align-single-column-lines?)]
     (transform form edit-all z/map? align))))

(defn- matching-form-index? [zloc [k indexes] context]
  (if (= :all indexes)
    (and (z/list? zloc)
         (form-matches-key? (z/down zloc) k context))
    (and (z/list? (z/up zloc))
         (form-matches-key? zloc k context)
         (contains? (set indexes) (dec (index-of zloc))))))

(defn- matching-form? [zloc form-indexes context]
  (and (or (z/list? zloc) (z/list? (z/up zloc)))
       (some #(matching-form-index? zloc % context) form-indexes)))

(defn align-form-columns
  ([form aligned-forms alias-map]
   (align-form-columns form aligned-forms alias-map default-options))
  ([form aligned-forms alias-map {:keys [align-single-column-lines?]}]
   (let [ns-name  (find-namespace (z/of-node form))
         context  {:alias-map alias-map, :ns-name ns-name}
         aligned? #(matching-form? % aligned-forms context)
         align    #(align-columns % align-single-column-lines?)]
     (transform form edit-all aligned? align))))

(defn realign-form
  "Realign a rewrite-clj form such that the columns line up into columns."
  ([form]
   (realign-form form default-options))
  ([form {:keys [align-single-column-lines?]}]
   (-> form z/of-node (align-columns align-single-column-lines?) z/root)))

(defn- unalign-from-space [zloc]
  (pad-node (z/right* zloc) (- 1 (node-str-length zloc))))

(defn unalign-form
  "Remove any consecutive non-indenting whitespace within the form."
  [form]
  (-> form z/of-node z/down
      (edit-all non-indenting-whitespace? unalign-from-space z/right*)
      z/root))

(defn- blank-line-in-form? [zloc blank-line-forms context]
  (and (z/linebreak? zloc)
       (> (count-newlines zloc) 1)
       (not (z/map? (z/up zloc)))
       (not (root? (z/up zloc)))
       (not (matching-form? (z/up zloc) blank-line-forms context))))

(defn- replace-with-single-newline [zloc]
  (z/replace zloc (n/newline-node "\n")))

(defn remove-blank-lines-in-forms
  [form blank-line-forms alias-map]
  (let [ns-name     (find-namespace (z/of-node form))
        context     {:alias-map alias-map, :ns-name ns-name}
        blank-line? #(blank-line-in-form? % blank-line-forms context)]
    (transform form edit-all blank-line? replace-with-single-newline)))

#?(:clj
   (defn- ns-require-form? [zloc]
     (and (some-> zloc top-level-form ns-form?)
          (some-> zloc z/child-sexprs first (= :require)))))

#?(:clj
   (defn- as-keyword? [zloc]
     (and (= :token (z/tag zloc))
          (= :as (z/sexpr zloc)))))

#?(:clj
   (defn- symbol-node? [zloc]
     (some-> zloc z/node n/symbol-node?)))

#?(:clj
   (defn- leftmost-symbol [zloc]
     (some-> zloc z/leftmost (z/find (comp symbol-node? skip-meta)))))

#?(:clj
   (defn- as-zloc->alias-mapping [as-zloc]
     (let [alias             (some-> as-zloc z/right z/sexpr)
           current-namespace (some-> as-zloc leftmost-symbol z/sexpr)
           grandparent-node  (some-> as-zloc z/up z/up)
           parent-namespace  (when-not (ns-require-form? grandparent-node)
                               (when (or (z/vector? grandparent-node)
                                         (z/list? grandparent-node))
                                 (first (z/child-sexprs grandparent-node))))]
       (when (and (symbol? alias) (symbol? current-namespace))
         {(str alias) (if parent-namespace
                        (format "%s.%s" parent-namespace current-namespace)
                        (str current-namespace))}))))

#?(:clj
   (defn- alias-map-for-form [form]
     (when-let [req-zloc (-> form z/of-node (z/find z/next ns-require-form?))]
       (->> (find-all req-zloc as-keyword?)
            (map as-zloc->alias-mapping)
            (apply merge)))))

(defn- stringify-map [m]
  (into {} (map (fn [[k v]] [(str k) (str v)])) m))

(defn reformat-form
  "Reformats a rewrite-clj form data structure. Accepts a map of
  [formatting options][1]. See also: [[reformat-string]].

  [1]: https://github.com/weavejester/cljfmt#formatting-options"
  ([form]
   (reformat-form form {}))
  ([form options]
   (let [opts      (merge default-options options)
         indents   (merge (:indents opts) (:extra-indents opts))
         aligned   (merge (:aligned-forms opts) (:extra-aligned-forms opts))
         blank     (merge (:blank-line-forms opts)
                          (:extra-blank-line-forms opts))
         alias-map #?(:clj  (merge (alias-map-for-form form)
                                   (stringify-map (:alias-map opts)))
                      :cljs (stringify-map (:alias-map opts)))]
     (-> form
         (cond-> (:sort-ns-references? opts)
           sort-ns-references)
         (cond-> (:split-keypairs-over-multiple-lines? opts)
           split-keypairs-over-multiple-lines)
         (cond-> (:remove-consecutive-blank-lines? opts)
           remove-consecutive-blank-lines)
         (cond-> (:remove-surrounding-whitespace? opts)
           (remove-surrounding-whitespace opts))
         (cond-> (:insert-missing-whitespace? opts)
           insert-missing-whitespace)
         (cond-> (:remove-multiple-non-indenting-spaces? opts)
           remove-multiple-non-indenting-spaces)
         (cond-> (:indentation? opts)
           (reindent indents alias-map opts))
         (cond-> (:align-map-columns? opts)
           (align-map-columns opts))
         (cond-> (:align-form-columns? opts)
           (align-form-columns aligned alias-map opts))
         (cond-> (:remove-trailing-whitespace? opts)
           remove-trailing-whitespace)
         (cond-> (:remove-blank-lines-in-forms? opts)
           (remove-blank-lines-in-forms blank alias-map))))))

(defn reformat-string
  "Reformat a string. Accepts a map of [formatting options][1].

  [1]: https://github.com/weavejester/cljfmt#formatting-options"
  ([form-string]
   (reformat-string form-string {}))
  ([form-string options]
   (-> (p/parse-string-all form-string)
       (reformat-form options)
       n/string
       (cond-> (:normalize-newlines-at-file-end? options)
         normalize-newlines-at-file-end))))

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
