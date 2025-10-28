(ns cljfmt.core-test
  (:require #?(:clj [cljfmt.test-util.clojure])
            [#?@(:clj (clojure.test :refer)
                 :cljs (cljs.test :refer-macros)) [deftest testing is are]]
            [cljfmt.core :refer [reformat-string default-line-separator
                                 normalize-newlines find-line-separator
                                 replace-newlines wrap-normalize-newlines
                                 realign-form unalign-form]]
            [cljfmt.test-util.common]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p])
  #?(:cljs (:require-macros [cljfmt.test-util.cljs])))

(deftest test-indent
  (testing "list indentation"
    (are [fn-args-indent]
         (reformats-to?
          ["(foo bar"
           "baz"
           "quz)"]
          ["(foo bar"
           "     baz"
           "     quz)"]
          {:function-arguments-indentation fn-args-indent})
      :community
      :cursive
      :zprint)

    (are [fn-args-indent expected]
         (reformats-to?
          ["(foo"
           "bar"
           "baz)"]
          expected
          {:function-arguments-indentation fn-args-indent})
      :community ["(foo"
                  " bar"
                  " baz)"]
      :cursive ["(foo"
                "  bar"
                "  baz)"]
      :zprint ["(foo"
               "  bar"
               "  baz)"]))

  (testing "block indentation"
    (is (reformats-to?
         ["(if (= x 1)"
          ":foo"
          ":bar)"]
         ["(if (= x 1)"
          "  :foo"
          "  :bar)"]))
    (is (reformats-to?
         ["(do"
          "(foo)"
          "(bar))"]
         ["(do"
          "  (foo)"
          "  (bar))"]))
    (is (reformats-to?
         ["(do (foo)"
          "(bar))"]
         ["(do (foo)"
          "    (bar))"])
        "do block to hanging argument")
    (is (reformats-to?
         ["(deftype Foo"
          "[x]"
          "Bar)"]
         ["(deftype Foo"
          "         [x]"
          "  Bar)"])
        "deftype fields to class name")
    (is (reformats-to?
         ["(cond->> x"
          "a? a"
          "b? b)"]
         ["(cond->> x"
          "  a? a"
          "  b? b)"])))

  (testing "constant indentation"
    (is (reformats-to?
         ["(def foo"
          "\"Hello World\")"]
         ["(def foo"
          "  \"Hello World\")"]))
    (is (reformats-to?
         ["(defn foo [x]"
          "(+ x 1))"]
         ["(defn foo [x]"
          "  (+ x 1))"])
        "defn does not indent to hanging name and params")
    (is (reformats-to?
         ["(defn foo"
          "[x]"
          "(+ x 1))"]
         ["(defn foo"
          "  [x]"
          "  (+ x 1))"])
        "defn does not indent to hanging name")
    (is (reformats-to?
         ["(defn foo"
          "([] 0)"
          "([x]"
          "(+ x 1))"
          "(^Number [x y]"
          "(+ x y)))"]
         ["(defn foo"
          "  ([] 0)"
          "  ([x]"
          "   (+ x 1))"
          "  (^Number [x y]"
          "   (+ x y)))"])
        "defn multi-arity")
    (is (reformats-to?
         ["(fn [x]"
          "(foo bar"
          "baz))"]
         ["(fn [x]"
          "  (foo bar"
          "       baz))"])
        "fn with body")
    (is (reformats-to?
         ["(fn [x] (foo bar"
          "baz))"]
         ["(fn [x] (foo bar"
          "             baz))"])
        "fn with hanging body")
    (is (reformats-to?
         ["(clojure.spec.alpha/def ::foo"
          "                        string?)"]
         ["(clojure.spec.alpha/def ::foo"
          "  string?)"])
        "unhangs hanging spec indentation")
    (is (reformats-to?
         ["(clojure.spec.alpha/fdef foo"
          "                         :args (clojure.spec.alpha/cat :x string?)"
          "                         :ret nat-int?)"]
         ["(clojure.spec.alpha/fdef foo"
          "  :args (clojure.spec.alpha/cat :x string?)"
          "  :ret nat-int?)"])
        "unhangs hanging spec indentations"))

  (testing "inner indentation"
    (is (reformats-to?
         ["(letfn [(foo [x]"
          "(* x x))]"
          "(foo 5))"]
         ["(letfn [(foo [x]"
          "          (* x x))]"
          "  (foo 5))"]))
    (is (reformats-to?
         ["(letfn [(foo [x]"
          "(* x x))"
          "(bar [x]\n(+ x x))]"
          "(foo 5))"]
         ["(letfn [(foo [x]"
          "          (* x x))"
          "        (bar [x]"
          "          (+ x x))]"
          "  (foo 5))"]))
    (is (reformats-to?
         ["(reify Closeable"
          "(close [_]"
          "(prn :closed)))"]
         ["(reify Closeable"
          "  (close [_]"
          "    (prn :closed)))"]))
    (is (reformats-to?
         ["(defrecord Foo [x]"
          "Closeable"
          "(close [_]"
          "(prn x)))"]
         ["(defrecord Foo [x]"
          "  Closeable"
          "  (close [_]"
          "    (prn x)))"]))
    (is (reformats-to?
         ["(defprotocol Testing"
          "(^:example"
          "test-method [x]))"]
         ["(defprotocol Testing"
          "  (^:example"
          "   test-method [x]))"]))
    (testing "nested rules like [:inner 1] (#349)"
      (is (reformats-to?
           ["(ns my.namespace)"
            ""
            "(defprotocol MyProtocol"
            "MyClass"
            "(with-x [this x]"
            "\"with-x is a method\"))"
            ""
            "(extend-protocol MyProtocol"
            "MyClass"
            "(with-x [this x]"
            "(+ this x)))"
            ""
            "(defn x [x]"
            "(with-x x "
            "1))"]
           ["(ns my.namespace)"
            ""
            "(defprotocol MyProtocol"
            "  MyClass"
            "  (with-x [this x]"
            "    \"with-x is a method\"))"
            ""
            "(extend-protocol MyProtocol"
            "  MyClass"
            "  (with-x [this x]"
            "    (+ this x)))"
            ""
            "(defn x [x]"
            "  (with-x x"
            "          1))"]
           {:extra-indents '{my.namespace/with-x [[:block 0]]}}))))

  (testing "data structure indentation"
    (is (reformats-to?
         ["[:foo"
          ":bar"
          ":baz]"]
         ["[:foo"
          " :bar"
          " :baz]"]))
    (is (reformats-to?
         ["{:foo 1"
          ":bar 2}"]
         ["{:foo 1"
          " :bar 2}"]))
    (is (reformats-to?
         ["#{:foo"
          ":bar"
          ":baz}"]
         ["#{:foo"
          "  :bar"
          "  :baz}"]))
    (is (reformats-to?
         ["{:foo [:bar"
          ":baz]}"]
         ["{:foo [:bar"
          "       :baz]}"])))

  (testing "embedded structures"
    (is (reformats-to?
         ["(let [foo {:x 1"
          ":y 2}]"
          "(:x foo))"]
         ["(let [foo {:x 1"
          "           :y 2}]"
          "  (:x foo))"]))
    (is (reformats-to?
         ["(if foo"
          "(do bar"
          "baz)"
          "quz)"]
         ["(if foo"
          "  (do bar"
          "      baz)"
          "  quz)"])))

  (testing "namespaces"
    (is (reformats-to?
         ["(t/defn foo [x]"
          "(+ x 1))"]
         ["(t/defn foo [x]"
          "  (+ x 1))"])
        "namespaced defn")
    (is (reformats-to?
         ["(t/defrecord Foo [x]"
          "Closeable"
          "(close [_]"
          "(prn x)))"]
         ["(t/defrecord Foo [x]"
          "  Closeable"
          "  (close [_]"
          "    (prn x)))"])
        "namespaced defrecord")
    (is (reformats-to?
         ["(ns foo)"
          "(-> 1"
          "prn)"]
         ["(ns foo)"
          "(-> 1"
          "    prn)"]
         {:indents {#"^\w" [[:inner 0]]}}))
    (is (reformats-to?
         ["(ns def)"
          "(-> 1"
          "prn)"]
         ["(ns def)"
          "(-> 1"
          "    prn)"]))
    (is (reformats-to?
         ["(ns example"
          "(:require [thing.core :as t]))"
          ""
          "(t/defn foo [x]"
          "(+ x 1))"
          ""
          "(defn foo [x]"
          "(+ x 1))"]
         ["(ns example"
          "    (:require [thing.core :as t]))"
          ""
          "(t/defn foo [x]"
          "  (+ x 1))"
          ""
          "(defn foo [x]"
          "      (+ x 1))"]
         {:indents {'thing.core/defn [[:inner 0]]}
          #?@(:cljs [:alias-map {"t" "thing.core"}])})
        "applies custom indentation to namespaced defn")
    (testing "handles metadata on or comments before forms inside ns :require list"
      (doseq [ignore-str [""
                          "^{:clj-kondo/ignore [:discouraged-namespace]} "
                          "^:clj-kondo/ignore "
                          "^{some-symbol another-symbol} "
                          "#_{:clj-kondo/ignore [:discouraged-namespace]} "
                          "#_:clj-kondo/ignore "
                          "^tag "
                          "#_old-thing "]
              ns-vec-str [(str ignore-str "[thing.core :as t]")
                          (str ignore-str "[thing [core :as t]]")
                          (str ignore-str "(thing [core :as t])")
                          (str "[" ignore-str "thing.core :as t]")
                          (str ignore-str " [" ignore-str "thing.core :as t]")]
              :let [ns-str (str "(ns example (:require " ns-vec-str "))")]]
        (testing ns-str
          (is (reformats-to?
               [ns-str
                ""
                "(t/defn foo [x]"
                "(+ x 1))"]
               [ns-str
                ""
                "(t/defn foo [x]"
                "  (+ x 1))"]
               {:indents {'ns [[:block 1]], 'thing.core/defn [[:inner 0]]}
                #?@(:cljs [:alias-map {"t" "thing.core"}])})))))
    (is (reformats-to?
         ["(comment)"
          "(ns thing.core)"
          ""
          "(defthing foo [x]"
          "(+ x 1))"]
         ["(comment)"
          "(ns thing.core)"
          ""
          "(defthing foo [x]"
          "  (+ x 1))"]
         {:indents {'thing.core/defthing [[:inner 0]]}
          #?@(:cljs [:alias-map {}])})
        "recognises the current namespace as part of a qualifed indent spec")
    (is (reformats-to?
         ["(m/defthing foo [x]"
          "(+ x 1))"]
         ["(m/defthing foo [x]"
          "  (+ x 1))"]
         {:indents {'thing.core/defthing [[:inner 0]]}
          :alias-map {'m 'thing.core}})
        "accepts symbols in :alias-map option")
    (is (reformats-to?
         ["(ns example"
          "(:require [thing.core :as t]))"
          ""
          "(t/defrecord Foo [x]"
          "Closeable"
          "(close [_]"
          "(prn x)))"
          ""
          "(defrecord Foo [x]"
          "Closeable"
          "(close [_]"
          "(prn x)))"]
         ["(ns example"
          "    (:require [thing.core :as t]))"
          ""
          "(t/defrecord Foo [x]"
          "  Closeable"
          "  (close [_]"
          "         (prn x)))"
          ""
          "(defrecord Foo [x]"
          "           Closeable"
          "           (close [_]"
          "                  (prn x)))"]
         {:indents {'thing.core/defrecord [[:inner 0]]}
          #?@(:cljs [:alias-map {"t" "thing.core"}])})
        "applies custom indentation to namespaced defrecord")
    (is (reformats-to?
         ["(let [ns subs]"
          "  (ns \"string\"))"
          ""
          "(ns thing.core)"
          ""
          "(defthing foo [x]"
          "(+ x 1))"]
         ["(let [ns subs]"
          "  (ns \"string\"))"
          ""
          "(ns thing.core)"
          ""
          "(defthing foo [x]"
          "  (+ x 1))"]
         {:indents {'thing.core/defthing [[:inner 0]]
                    'let [[:inner 0]]}
          #?@(:cljs [:alias-map {}])})
        "recognises the current namespace as part of a qualifed indent spec, even if preceded by a local var named ns")
    (is (reformats-to?
         ["(let [ns (range 10)]"
          "  (reduce + ns"
          "          ))"]
         ["(let [ns (range 10)]"
          "  (reduce + ns))"])
        "doesn't throw with local vars named ns bound to expressions"))

  (testing "function #() syntax"
    (is (reformats-to?
         ["#(while true"
          "(println :foo))"]
         ["#(while true"
          "   (println :foo))"])
        "anonymous function")
    (is (reformats-to?
         ["#(reify Closeable"
          "(close [_]"
          "(prn %)))"]
         ["#(reify Closeable"
          "   (close [_]"
          "     (prn %)))"])
        "anonymous class"))

  (testing "multiple arities"
    (is (reformats-to?
         ["(fn"
          "([x]"
          "(foo)"
          "(bar)))"]
         ["(fn"
          "  ([x]"
          "   (foo)"
          "   (bar)))"])
        "multiple arity function with only one arity defined")
    (is (reformats-to?
         ["(fn"
          "([x]"
          "(foo)))"]
         ["(fn"
          "  ([x]"
          "   (foo)))"]
         {:indents {#".*" [[:inner 0]]}})
        "forms starting without a symbol are treated correctly"))

  (testing "comments"
    (is (reformats-to?
         [";foo"
          "(def x 1)"]
         [";foo"
          "(def x 1)"])
        "header comments unchanged")
    (is (reformats-to?
         ["(ns foo.core)"
          ""
          ";; foo"
          "(defn foo [x]"
          "(inc x))"]
         ["(ns foo.core)"
          ""
          ";; foo"
          "(defn foo [x]"
          "  (inc x))"])
        "post namespace comment unchanged, code indented")
    (is (reformats-to?
         [";; foo"
          "(ns foo"
          "(:require bar))"]
         [";; foo"
          "(ns foo"
          "  (:require bar))"])
        "pre namespace comment unchanged, namespace indented")
    (is (reformats-to?
         ["(defn foo [x]"
          "  ;; +1"
          "(inc x))"]
         ["(defn foo [x]"
          "  ;; +1"
          "  (inc x))"])
        "commented out line unchanged, code indented")
    (is (reformats-to?
         ["(let [;foo"
          " x (foo bar"
          " baz)]"
          " x)"]
         ["(let [;foo"
          "      x (foo bar"
          "             baz)]"
          "  x)"])
        "inline comment unchanged, code indented")
    (is (reformats-to?
         ["(binding [x 1] ; foo"
          "x)"]
         ["(binding [x 1] ; foo"
          "  x)"])
        "binding inline comment unchanged, code indented")
    (is (reformats-to?
         ["(let [x y]"
          "  [this"
          "   that"
          "   ;;other"
          "   ]"
          "  )"]
         ["(let [x y]"
          "  [this"
          "   that"
          "   ;;other"
          "   ])"])
        "indents correctly after last comment in block")
    (is (reformats-to?
         ["(defn foo"
          "  []"
          "  (let [x 1]"
          "    x"
          "    ;; test1"
          "    )"
          "  ;; test2"
          "  )"]
         ["(defn foo"
          "  []"
          "  (let [x 1]"
          "    x"
          "    ;; test1"
          "    )"
          "  ;; test2"
          "  )"])
        "indentation should not be lost after comment line")
    (is (reformats-to?
         ["{:keys [aa"
          "        ;; bb"
          "        ] :as context}"]
         ["{:keys [aa"
          "        ;; bb"
          "        ] :as context}"])))

  (testing "shebang comment"
    (is (reformats-to?
         ["#!/usr/bin/env bb"
          "(def x 1)"]
         ["#!/usr/bin/env bb"
          "(def x 1)"])))

  (testing "metadata"
    (is (reformats-to?
         ["(defonce ^{:doc \"foo\"}"
          "foo"
          ":foo)"]
         ["(defonce ^{:doc \"foo\"}"
          "  foo"
          "  :foo)"])
        "hanging metadata on defonce does not hang subsequent indentation")
    (is (reformats-to?
         ["(def ^:private"
          "foo"
          ":foo)"]
         ["(def ^:private"
          "  foo"
          "  :foo)"])
        "hanging metata on def does not hang subsequent indentation")
    (is (reformats-to?
         ["(def ^:private foo"
          ":foo)"]
         ["(def ^:private foo"
          "  :foo)"])
        "hanging metadata and name on def does not hang subsequent indentation")

    (testing "metadata on the first symbol in a list should be ignored when looking for matching indentation rules"
      (is (reformats-to?
           ["(^:amazing fn [x y]"
            "(+ x y))"]
           ["(^:amazing fn [x y]"
            "  (+ x y))"]))
      (is (reformats-to?
           ["(^{:amazing true} fn [x y]"
            "(+ x y))"]
           ["(^{:amazing true} fn [x y]"
            "  (+ x y))"]))))

  (testing "fuzzy matches"
    (is (reformats-to?
         ["(with-foo x"
          "y"
          "z)"]
         ["(with-foo x"
          "  y"
          "  z)"])
        "^with fuzzy rule respected")
    (is (reformats-to?
         ["(defelem foo [x]"
          "[:foo x])"]
         ["(defelem foo [x]"
          "  [:foo x])"])
        "^def fuzzy rule respected")
    (is (reformats-to?
         ["(default foo"
          "         bar)"]
         ["(default foo"
          "         bar)"])
        "^def fuzzy rule does not alter 'default'")
    (is (reformats-to?
         ["(defer   foo"
          "         bar)"]
         ["(defer   foo"
          "         bar)"])
        "^def fuzzy rule does not alter 'defer'")
    (is (reformats-to?
         ["(deflate foo"
          "         bar)"]
         ["(deflate foo"
          "         bar)"])
        "^def fuzzy rule does not alter 'deflate'"))

  (testing "comment before ending bracket"
    (is (reformats-to?
         ["(foo a ; b"
          "c ; d"
          ")"]
         ["(foo a ; b"
          "     c ; d"
          "     )"])
        "ending parens indents after hanging inline comment")
    (is (reformats-to?
         ["(do"
          "a ; b"
          "c ; d"
          ")"]
         ["(do"
          "  a ; b"
          "  c ; d"
          "  )"])
        "ending parens indents after inline comment")
    (is (reformats-to?
         ["(let [x [1 2 ;; test1"
          "2 3 ;; test2"
          "]])"]
         ["(let [x [1 2 ;; test1"
          "         2 3 ;; test2"
          "         ]])"])
        "ending square bracket indents after inline comment "))

  (testing "indented comments with blank lines"
    (is (reformats-to?
         ["(;a"
          ""
          " ;b"
          " )"]
         ["(;a"
          ""
          " ;b"
          " )"]))
    (is (reformats-to?
         ["(;a"
          ""
          " ;b"
          " )"]
         ["(;a"
          ""
          " ;b"
          "  )"]
         {:function-arguments-indentation :cursive})))

  (testing "empty indent blocks"
    (is (reformats-to?
         ["(deftest foo"
          "  (testing \"testing foo\""
          "    (let [a b]"
          "      ;; comment only"
          "      )))"]
         ["(deftest foo"
          "  (testing \"testing foo\""
          "    (let [a b]"
          "      ;; comment only"
          "      )))"]))
    (is (reformats-to?
         ["(let []"
          ")"]
         ["(let []"
          "  )"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(cond foo"
          "  )"]
         ["(cond foo"
          "      )"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(cond foo"
          "bar)"]
         ["(cond foo"
          "      bar)"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["["
          "]"]
         ["["
          " ]"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(foo"
          ")"]
         ["(foo"
          " )"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(foo"
          ")"]
         ["(foo"
          "  )"]
         {:remove-surrounding-whitespace? false
          :function-arguments-indentation :cursive}))
    (is (reformats-to?
         ["(foo (bar (baz)))"]
         ["(foo (bar (baz)))"]))
    (is (reformats-to?
         ["(deftest foo"
          "  (testing \"testing foo\""
          "    (let [a b]"
          "      )))"]
         ["(deftest foo"
          "  (testing \"testing foo\""
          "    (let [a b]"
          "      )))"]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(ns example"
          "(:require [thing.core :as t]))"
          ""
          "(t/defn foo [x]"
          "(+ x 1"
          ";; empty block 1"
          "))"
          ""
          "(defn foo [x]"
          "(+ x 1"
          ";; empty block 2"
          "))"]
         ["(ns example"
          "    (:require [thing.core :as t]))"
          ""
          "(t/defn foo [x]"
          "  (+ x 1"
          ";; empty block 1"
          "     ))"
          ""
          "(defn foo [x]"
          "      (+ x 1"
          ";; empty block 2"
          "         ))"]
         {:indents {'thing.core/defn [[:inner 0]]}
          #?@(:cljs [:alias-map {"t" "thing.core"}])})
        "empty blocks with custom indents"))

  (testing "letfn block"
    (is (reformats-to?
         ["(letfn [(f [x]"
          "x)]"
          "(let [x (f 1)]"
          "(str x 2"
          "3 4)))"]
         ["(letfn [(f [x]"
          "          x)]"
          "  (let [x (f 1)]"
          "    (str x 2"
          "         3 4)))"])))

  (testing "let blocks"
    (is (reformats-to?
         ["(let [x 1]"
          "(+ x 1))"]
         ["(let [x 1]"
          "  (+ x 1))"]))
    (is (reformats-to?
         ["(let* [x 1]"
          "(+ x 1))"]
         ["(let* [x 1]"
          "  (+ x 1))"])))

  (testing "multiline right hand side forms"
    (is (reformats-to?
         ["(list foo :bar (fn a"
          "([] nil)"
          "([b] b)))"]
         ["(list foo :bar (fn a"
          "                 ([] nil)"
          "                 ([b] b)))"])))

  (testing "reader conditionals"
    (is (reformats-to?
         ["#?(:clj foo"
          ":cljs bar)"]
         ["#?(:clj foo"
          "   :cljs bar)"])
        "standard syntax")
    (is (reformats-to?
         ["#?@(:clj foo"
          ":cljs bar)"]
         ["#?@(:clj foo"
          "    :cljs bar)"])
        "splicing syntax")
    (testing "symbols using reader conditionals should indent correctly"
      (let [opts {:indents '{defprotocol          [[:block 1] [:inner 1]]
                             potemkin/defprotocol+ [[:block 1] [:inner 1]]}}]
        (testing "standard syntax"
          (is (reformats-to?
               ["(#?(:clj potemkin/defprotocol+ :cljs defprotocol) MyProtocol"
                "  \"This is a docstring for my protocol.\""
                "  (method [this x]"
                "    \"This is a docstring for a protocol method.\")"
                ")"]
               ["(#?(:clj potemkin/defprotocol+ :cljs defprotocol) MyProtocol"
                "  \"This is a docstring for my protocol.\""
                "  (method [this x]"
                "    \"This is a docstring for a protocol method.\"))"]
               opts)
              ":clj and :cljs"))
        (is (reformats-to?
             ["(#?(:clj potemkin/defprotocol+) MyProtocol"
              "  \"This is a docstring for my protocol.\""
              "  (method [this x]"
              "    \"This is a docstring for a protocol method.\")"
              ")"]
             ["(#?(:clj potemkin/defprotocol+) MyProtocol"
              "  \"This is a docstring for my protocol.\""
              "  (method [this x]"
              "    \"This is a docstring for a protocol method.\"))"]
             opts)
            "only :clj")
        (is (reformats-to?
             ["(#?(:cljs ^:wow defprotocol) MyProtocol"
              "  \"This is a docstring for my protocol.\""
              "  (method [this x]"
              "    \"This is a docstring for a protocol method.\")"
              ")"]
             ["(#?(:cljs ^:wow defprotocol) MyProtocol"
              "  \"This is a docstring for my protocol.\""
              "  (method [this x]"
              "    \"This is a docstring for a protocol method.\"))"]
             opts)
            "only :cljs; skip metadata in front of symbol"))))

  (testing "namespaced maps"
    (is (reformats-to?
         ["#:clj {:a :b"
          ":c :d}"]
         ["#:clj {:a :b"
          "       :c :d}"])))
  (testing "~ @ is not ~@"
    (is (reformats-to?
         ["~ @foo"]
         ["~ @foo"]))
    (is (reformats-to?
         ["~(deref foo)"]
         ["~(deref foo)"]))
    (is (reformats-to?
         ["~(clojure.core/deref foo)"]
         ["~(clojure.core/deref foo)"]))
    (is (reformats-to?
         ["~ @foo"]
         ["~ @foo"]))
    (is (reformats-to?
         ["~     @foo"]
         ["~     @foo"]))
    (is (reformats-to?
         ["~\n@foo"]
         ["~"
          " @foo"]))
    (is (reformats-to?
         ["~;;comment\n@foo"]
         ["~;;comment"
          " @foo"]))
    (is (reformats-to?
         ["~#_a@foo"]
         ["~#_a @foo"]))))

(deftest test-remove-multiple-non-indenting-spaces
  (let [opts {:remove-multiple-non-indenting-spaces? true}]
    (is (reformats-to? ["[]"] ["[]"] opts))
    (is (reformats-to? ["[   ]"] ["[]"] opts))
    (is (reformats-to? ["{   }"] ["{}"] opts))
    (is (reformats-to? ["#{ }"] ["#{}"] opts))
    (is (reformats-to? ["[a     b]"] ["[a b]"]  opts))
    (is (reformats-to? ["{a     b}"] ["{a b}"] opts))
    (is (reformats-to? ["{a,     b}"] ["{a, b}"] opts))
    (is (reformats-to? ["#{a     b}"] ["#{a b}"] opts))
    (is (reformats-to? ["#{    }"] ["#{}"] opts))
    (is (reformats-to? ["[a     b   c]"] ["[a b c]"] opts))
    (is (reformats-to? ["#{a     b     }"] ["#{a b}"] opts))
    (is (reformats-to? ["[a   ,  b]"] ["[a , b]"] opts))
    (is (reformats-to? ["(do"
                        ""
                        "  something)"]
                       ["(do"
                        ""
                        "  something)"]
                       opts)
        "non-comment newlines are respected")
    (is (reformats-to? ["(cond truc"
                        "      ;; foo"
                        "      (bar? a    b) :baz)"]
                       ["(cond truc"
                        "      ;; foo"
                        "      (bar? a b) :baz)"]
                       opts)
        "comments are respected")
    (is (reformats-to? ["(cond truc"
                        "             ;; foo"
                        "   (bar? a    b) :baz)"]
                       ["(cond truc"
                        "             ;; foo"
                        "   (bar? a b) :baz)"]
                       (assoc opts :indentation? false))
        "custom indentation is respected")))

(deftest test-surrounding-whitespace
  (testing "surrounding whitespace removed"
    (is (reformats-to?
         ["( foo bar )"]
         ["(foo bar)"]))
    (is (reformats-to?
         ["[ 1 2 3 ]"]
         ["[1 2 3]"]))
    (is (reformats-to?
         ["{  :x 1, :y 2 }"]
         ["{:x 1, :y 2}"])))

  (testing "surrounding newlines removed"
    (is (reformats-to?
         ["("
          "  foo"
          ")"]
         ["(foo)"]))
    (is (reformats-to?
         ["(  "
          "foo"
          ")"]
         ["(foo)"]))
    (is (reformats-to?
         ["(foo  "
          ")"]
         ["(foo)"]))
    (is (reformats-to?
         ["(foo"
          "  )"]
         ["(foo)"]))
    (is (reformats-to?
         ["["
          "1 2 3"
          "]"]
         ["[1 2 3]"]))
    (is (reformats-to?
         ["{"
          ":foo \"bar\""
          "}"]
         ["{:foo \"bar\"}"]))
    (is (reformats-to?
         ["( let [x 3"
          "y 4]"
          "(+ (* x x"
          ")(* y y)"
          "))"]
         ["(let [x 3"
          "      y 4]"
          "  (+ (* x x) (* y y)))"])))

  (testing "spacing before comments left alone"
    (is (reformats-to?
         ["[  ; comment"
          "1 2 3"
          "]"]
         ["[  ; comment"
          " 1 2 3]"]))))

(deftest test-missing-whitespace
  (testing "unglue inner"
    (is (reformats-to?
         ["(foo(bar baz)qux)"]
         ["(foo (bar baz) qux)"]))
    (is (reformats-to?
         ["(foo)bar(baz)"]
         ["(foo) bar (baz)"]))
    (is (reformats-to?
         ["(foo[bar]#{baz}{quz bang})"]
         ["(foo [bar] #{baz} {quz bang})"])))

  (testing "reader conditionals"
    (is (reformats-to?
         ["#?(:cljs(bar 1) :clj(foo 2))"]
         ["#?(:cljs (bar 1) :clj (foo 2))"]))
    (is (reformats-to?
         ["#?@(:cljs[foo bar] :clj[baz quux])"]
         ["#?@(:cljs [foo bar] :clj [baz quux])"]))))

(deftest test-consecutive-blank-lines
  (is (reformats-to?
       ["(foo)"
        ""
        "(bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        ","
        "(bar)"]
       ["(foo)"
        ","
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        " (bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        "(bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        " "
        " "
        "(bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        ""
        ""
        "(bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ";bar"
        ""
        "(baz)"]
       ["(foo)"
        ""
        ";bar"
        ""
        "(baz)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        ""
        ";bar"
        "(baz)"]
       ["(foo)"
        ""
        ";bar"
        "(baz)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        ""
        ";bar"
        ""
        ""
        ""
        ""
        "(baz)"]
       ["(foo)"
        ""
        ";bar"
        ""
        "(baz)"]))
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        ""
        ";bar"
        ""
        ""
        ""
        ""
        ";moo"
        ""
        ""
        ""
        ""
        "(baz)"]
       ["(foo)"
        ""
        ";bar"
        ""
        ";moo"
        ""
        "(baz)"]))
  (is (reformats-to?
       [";foo"
        ""
        ""
        ";bar"
        ""
        ""
        ";moo"
        ""
        ""
        ";baz"]
       [";foo"
        ""
        ";bar"
        ""
        ";moo"
        ""
        ";baz"]))
  (is (reformats-to?
       ["(foo)"
        ","
        ","
        ";bar"
        ","
        ""
        ";moo"
        ","
        "   ,,,,,  "
        "(baz)"]
       ["(foo)"
        ""
        ";bar"
        ""
        ";moo"
        ""
        "(baz)"]))
  (is (reformats-to?
       ["(foo)"
        ";bar"
        ";baz"
        ";qux"
        "(bang)"]
       ["(foo)"
        ";bar"
        ";baz"
        ";qux"
        "(bang)"]))
  (is (reformats-to?
       ["(foo"
        ")"
        ""
        "(bar)"]
       ["(foo)"
        ""
        "(bar)"]))
  (is (reformats-to?
       ["(ns bad-reformat-diff.core"
        "  (:require [clojure.test :refer :all]))"
        ""
        "(defn -main"
        "  [& args]"
        "  (println args))"
        ""
        ""
        ""]
       ["(ns bad-reformat-diff.core"
        "  (:require [clojure.test :refer :all]))"
        ""
        "(defn -main"
        "  [& args]"
        "  (println args))"
        ""
        ""
        ""])
      "blank lines at end of string are not affected by :remove-consecutive-blank-lines?"))

(deftest test-blank-lines-in-forms
  (is (reformats-to?
       ["(defn x []"
        "  (do-something!)"
        ""
        "  (do-something-else!)"
        ""
        ""
        "  (do-a-third-thing!))"]
       ["(defn x []"
        "  (do-something!)"
        "  (do-something-else!)"
        "  (do-a-third-thing!))"]
       {:remove-blank-lines-in-forms? true}))
  (is (reformats-to?
       ["(let [x 1"
        "      y 2]"
        "  (do-something!)"
        ""
        "  (do-something-else!))"]
       ["(let [x 1"
        "      y 2]"
        "  (do-something!)"
        "  (do-something-else!))"]
       {:remove-blank-lines-in-forms? true}))
  (testing "ignore pairwise forms"
    (testing "known clojure.core forms"
      (testing "cond"
        (let [form ["(cond"
                    "  (map? x)"
                    "  :map"
                    ""
                    "  (sequential? x)"
                    "  :seq)"]]
          (is (reformats-to? form form {:remove-blank-lines-in-forms? true})))))
    (testing "custom forms in :extra-blank-line-forms"
      (testing "from another namespace"
        (let [form ["(ns x"
                    "  (:require [better-cond.core :as b]))"
                    ""
                    "(b/cond*"
                    "  (map? x)"
                    "  :map"
                    ""
                    "  (sequential? x)"
                    "  :seq)"]]
          (is (reformats-to?
               form
               form
               {:remove-blank-lines-in-forms? true
                :extra-indents '{better-cond.core/cond* [[:block 0]]}
                :extra-blank-line-forms '{better-cond.core/cond* :all}
                #?@(:cljs [:alias-map {"b" "better-cond.core"}])}))))
      (testing "from the current namespace"
        (let [form ["(ns better-cond.core)"
                    ""
                    "(cond*"
                    "  (map? x)"
                    "  :map"
                    ""
                    "  (sequential? x)"
                    "  :seq)"]]
          (is (reformats-to?
               form
               form
               {:remove-blank-lines-in-forms? true
                :extra-indents '{better-cond.core/cond* [[:block 0]]}
                :extra-blank-line-forms '{better-cond.core/cond* :all}})))))
    (testing "handle reader conditionals"
      (let [form ["(#?(:clj cond :cljs cond)"
                  "  (map? x)"
                  "  :map"
                  ""
                  "  (sequential? x)"
                  "  :seq)"]]
        (is (reformats-to? form form {:remove-blank-lines-in-forms? true}))))
    (testing (str ":blank-line-forms :all should only exempt the immediate"
                  " children of the form")
      (is (reformats-to?
           ["(cond"
            "  (= x :a)"
            "  :a"
            ""
            "  (= x :b)"
            "  (do"
            "    (do-something!)"
            ""
            "    {:x x}))"]
           ["(cond"
            "  (= x :a)"
            "  :a"
            ""
            "  (= x :b)"
            "  (do"
            "    (do-something!)"
            "    {:x x}))"]
           {:remove-blank-lines-in-forms? true}))))
  (testing "ignore binding forms"
    (let [form ["(let [a"
                "      1"
                ""
                "      b"
                "      2]"
                "  (+ a b))"]]
      (is (reformats-to? form form {:remove-blank-lines-in-forms? true}))))
  (testing "ignore top-level newlines"
    (let [form ["(def x 1)"
                ""
                "(def x 2)"]]
      (is (reformats-to? form form {:remove-blank-lines-in-forms? true}))))
  (testing "Ignore blank lines when parent and/or grandparent forms are not lists"
    (let [form ["[:a"
                " [:b"
                ""
                "  :c]]"]]
      (is (reformats-to? form form {:remove-blank-lines-in-forms? true})))))

(deftest test-trailing-whitespace
  (testing "trailing-whitespace removed"
    (is (reformats-to?
         ["(foo bar) "]
         ["(foo bar)"]))
    (is (reformats-to?
         ["(foo bar)"
          ""]
         ["(foo bar)"
          ""]))
    (is (reformats-to?
         ["(foo bar) "
          " "]
         ["(foo bar)"
          ""]))
    (is (reformats-to?
         ["(foo bar) "
          "(foo baz)"]
         ["(foo bar)"
          "(foo baz)"]))
    (is (reformats-to?
         ["(foo bar)\t"
          "(foo baz)"]
         ["(foo bar)"
          "(foo baz)"])))

  (testing "preserve surrounding whitespace"
    (is (reformats-to?
         ["( foo bar ) "
          ""]
         ["( foo bar )"
          ""]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["( foo bar )   "
          "( foo baz )"
          ""]
         ["( foo bar )"
          "( foo baz )"
          ""]
         {:remove-surrounding-whitespace? false}))
    (is (reformats-to?
         ["(foo"
          "(bar"
          ")"
          ")"]
         ["(foo"
          " (bar"
          "  )"
          " )"]
         {:remove-surrounding-whitespace? false})
        "indents properly")
    (is (reformats-to?
         ["(foo"
          "(bar"
          ")"
          ")"]
         ["(foo"
          "  (bar"
          "    )"
          "  )"]
         {:remove-surrounding-whitespace? false
          :function-arguments-indentation :cursive})
        "indents properly with :function-arguments-indentation :cursive")))

(deftest test-options
  (is (reformats-to?
       ["(foo)"
        ""
        ""
        "(bar)"]
       ["(foo)"
        ""
        ""
        "(bar)"]
       {:remove-consecutive-blank-lines? false}))
  (is (reformats-to?
       ["(  foo  )"]
       ["(  foo  )"]
       {:remove-surrounding-whitespace? false}))
  (is (reformats-to?
       ["(foo(bar))"]
       ["(foo(bar))"]
       {:insert-missing-whitespace? false}))
  (is (reformats-to?
       ["(foo"
        "bar)"]
       ["(foo"
        "  bar)"]
       {:indents {'foo [[:block 0]]}})
      "can customize block indentation")
  (is (reformats-to?
       ["(defn f [x]"
        "(foo x"
        "1))"]
       ["(defn f [x]"
        "      (foo x"
        "        1))"]
       {:indents {'foo [[:inner 0]]}})
      "can override existing indents")
  (is (reformats-to?
       ["(defn f [x]"
        "(foo x"
        "1))"]
       ["(defn f [x]"
        "  (foo x"
        "    1))"]
       {:extra-indents {'foo [[:inner 0]]}})
      "can extend existing indents")
  (is (reformats-to?
       ["(do"
        "foo"
        "bar)"]
       ["(do"
        " foo"
        " bar)"]
       {:indents {}})
      "can clear all indents rules")
  (is (reformats-to?
       ["(do"
        "foo"
        "bar)"]
       ["(do"
        "  foo"
        "  bar)"]
       {:indents {}
        :function-arguments-indentation :cursive})
      "can clear indents rules with :function-arguments-indentation :cursive")
  (is (reformats-to?
       ["(do"
        "foo"
        "bar)"]
       ["(do"
        "foo"
        "bar)"]
       {:indentation? false})
      "can disable indentation")
  (is (reformats-to?
       ["(foo bar) "
        "(foo baz)"]
       ["(foo bar) "
        "(foo baz)"]
       {:remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["(foo bar) "
        ""]
       ["(foo bar) "
        ""]
       {:remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["(foo"
        " "
        ")"]
       ["(foo"
        " "
        " )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["(foo"
        " "
        ")"]
       ["(foo"
        "  "
        "  )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false
        :function-arguments-indentation :cursive}))
  (is (reformats-to?
       ["( "
        "foo"
        " )"]
       ["( "
        " foo"
        " )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["( "
        "foo"
        " )"]
       ["( "
        "  foo"
        "  )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false
        :function-arguments-indentation :cursive}))
  (is (reformats-to?
       ["(foo"
        "   bar "
        ")"]
       ["(foo"
        " bar "
        " )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["(foo"
        "   bar "
        ")"]
       ["(foo"
        "  bar "
        "  )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false
        :function-arguments-indentation :cursive}))
  (is (reformats-to?
       ["{:one two :three four}"]
       ["{:one two"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["{:one two"
        " :three four}"]
       ["{:one two"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["{:one two"
        ";comment"
        ":three four}"]
       ["{:one two"
        ";comment"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["{:one two ;comment"
        ":three four}"]
       ["{:one two ;comment"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["{;comment"
        ":one two"
        ":three four}"]
       ["{;comment"
        " :one two"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["{:one two, :three four}"]
       ["{:one two, "
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true
        :remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["{:one two,"
        " :three four}"]
       ["{:one two,"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true
        :remove-trailing-whitespace? false}))
  (is (reformats-to?
       ["{:one two #_comment"
        ":three four}"]
       ["{:one two #_comment"
        " :three four}"]
       {:split-keypairs-over-multiple-lines? true}))
  (is (reformats-to?
       ["(with-timezone (java.time.OffsetDateTime.)"
        "\"US/Pacific\")"]
       ["(with-timezone (java.time.OffsetDateTime.)"
        "               \"US/Pacific\")"]
       {:indents {#"^with-"      [[:inner 0]]
                  'with-timezone [[:default]]}})
      "Should be able to override fuzzy indent rules for with- and explicitly specify :default indentation")
  #?(:clj
     (is (reformats-to?
          ["(ns foo.bar"
           "  (:require"
           "   [some.lib :as lib]"
           "   [other.lib :as other]))"
           "(lib/block2 1 2"
           "      3 4)"
           "(other/block1 1"
           "     2"
           "        3 4)"
           "(other/overridden 1"
           "  2"
           "  3 4)"]
          ["(ns foo.bar"
           "    (:require"
           "     [other.lib :as other]"
           "     [some.lib :as lib]))"
           "(lib/block2 1 2"
           "  3 4)"
           "(other/block1 1"
           "  2"
           "  3 4)"
           "(other/overridden 1"
           "  2"
           "  3 4)"]
          {:alias-map {"other" "another.lib"}
           :sort-ns-references? true
           :indents
           {'block1                 [[:block 1]]
            'other.lib/overridden   [[:block 2]] ;; This one is ignored
            'another.lib/overridden [[:block 1]] ;; As this one overrides.
            'some.lib/block2        [[:block 2]]}})))
  #?(:clj
     (is (reformats-to?
          ["(ns foo.bar"
           "  (:require"
           "   [some.lib :as lib]"
           "   [other.lib :as other]))"
           "(lib/block2 1 2"
           "      3 4)"
           "(other/block1 1"
           "     2"
           "        3 4)"
           "(other/overridden 1"
           "  2"
           "  3 4)"]
          ["(ns foo.bar"
           "    (:require"
           "      [other.lib :as other]"
           "      [some.lib :as lib]))"
           "(lib/block2 1 2"
           "  3 4)"
           "(other/block1 1"
           "  2"
           "  3 4)"
           "(other/overridden 1"
           "  2"
           "  3 4)"]
          {:alias-map {"other" "another.lib"}
           :sort-ns-references? true
           :function-arguments-indentation :cursive
           :indents
           {'block1                 [[:block 1]]
            'other.lib/overridden   [[:block 2]] ;; This one is ignored
            'another.lib/overridden [[:block 1]] ;; As this one overrides.
            'some.lib/block2        [[:block 2]]}}))))

(deftest test-parsing
  (is (reformats-to?
       [";foo"]
       [";foo"]))
  (is (reformats-to?
       ["::foo"]
       ["::foo"]))
  (is (reformats-to?
       ["::foo/bar"]
       ["::foo/bar"]))
  (is (reformats-to?
       ["foo//"]
       ["foo//"]))
  (is (reformats-to?
       ["foo:bar"]
       ["foo:bar"])
      "a name with an embedded colon")
  (is (reformats-to?
       ["#_(foo"
        "bar)"]
       ["#_(foo"
        "   bar)"]))
  (is (reformats-to?
       ["#_(foo"
        "bar)"]
       ["#_(foo"
        "    bar)"]
       {:function-arguments-indentation :cursive}))
  (is (reformats-to?
       ["(juxt +' -')"]
       ["(juxt +' -')"]))
  (is (reformats-to?
       ["#\"(?i)foo\""]
       ["#\"(?i)foo\""]))
  (is (= "#\"a\nb\""
         (reformat-string "#\"a\nb\""))
      "regular expression with embedded newline")
  (is (reformats-to?
       ["##Inf ##-Inf ##NaN"]
       ["##Inf ##-Inf ##NaN"]))
  (is (reformats-to?
       [":&::before"]
       [":&::before"])
      "garden library style selector"))

(deftest test-namespaced-keywords
  (is (reformats-to? ["(ns myns.core)"
                      ""
                      ""
                      "{::foo-bar (do (map)"
                      "               (foo))}"]
                     ["(ns myns.core)"
                      ""
                      "{::foo-bar (do (map)"
                      "               (foo))}"]))
  (is (reformats-to? ["(println (not (= ::result/foo 2)))"]
                     ["(println (not (= ::result/foo 2)))"])))

(deftest test-namespaced-maps
  (is (reformats-to?
       ["#:my-prefix    {:a 1}"]
       ["#:my-prefix    {:a 1}"]))
  (is (reformats-to?
       ["#:my-prefix{:a 1}"]
       ["#:my-prefix{:a 1}"]))
  (is (reformats-to?
       ["#::{:b 2}"]
       ["#::{:b 2}"]))
  (is (reformats-to?
       ["#::my-alias{:c 3}"]
       ["#::my-alias{:c 3}"])))

(deftest test-normalize-newlines
  (is (= (normalize-newlines "foo\nbar\nbaz") "foo\nbar\nbaz"))
  (is (= (normalize-newlines "foo\r\nbar\r\nbaz") "foo\nbar\nbaz"))
  (is (= (normalize-newlines "foo\r\nbar\nbaz\r\n") "foo\nbar\nbaz\n"))
  (is (= (normalize-newlines "foo\\nbar\nbaz\r\n") "foo\\nbar\nbaz\n"))
  (is (= (normalize-newlines "foo\\nbar\r\nbaz\r\n") "foo\\nbar\nbaz\n"))
  (is (= (normalize-newlines "foo\\nbar\\r\nbaz") "foo\\nbar\\r\nbaz"))
  (is (= (normalize-newlines "foobarbaz") "foobarbaz")))

(deftest test-find-line-separator
  (is (= (find-line-separator "foo\nbar\nbaz") "\n"))
  (is (= (find-line-separator "foo\r\nbar\r\nbaz") "\r\n"))
  (is (= (find-line-separator "foo\r\nbar\nbaz\r\n") "\r\n"))
  (is (= (find-line-separator "foo\\nbar\nbaz\r\n") "\n"))
  (is (= (find-line-separator "foo\\nbar\r\nbaz\r\n") "\r\n"))
  (is (= (find-line-separator "foo\\nbar\\r\nbaz") "\n"))
  (is (= (find-line-separator "foobarbaz") default-line-separator)))

(deftest test-replace-newlines
  (is (= (replace-newlines "foo\nbar\nbaz" "\n") "foo\nbar\nbaz"))
  (is (= (replace-newlines "foo\nbar\nbaz" "\r\n") "foo\r\nbar\r\nbaz"))
  (is (= (replace-newlines "foobarbaz" "\n") "foobarbaz"))
  (is (= (replace-newlines "foobarbaz" "\r\n") "foobarbaz")))

(deftest test-wrap-normalize-newlines
  (is (= ((wrap-normalize-newlines identity) "foo\nbar\nbaz") "foo\nbar\nbaz"))
  (is (= ((wrap-normalize-newlines identity) "foo\r\nbar\r\nbaz") "foo\r\nbar\r\nbaz"))
  (is (= ((wrap-normalize-newlines identity) "foobarbaz") "foobarbaz")))

(deftest test-sort-ns-references
  (is (reformats-to?
       ["(ns foo"
        "  (:require b c a))"]
       ["(ns foo"
        "  (:require a b c))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo"
        "  (:require b"
        "            c"
        "            a))"]
       ["(ns foo"
        "  (:require a"
        "            b"
        "            c))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo"
        "  (:require b"
        "            [c :as d]"
        "            a))"]
       ["(ns foo"
        "  (:require a"
        "            b"
        "            [c :as d]))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo.bar"
        "  (:require [c]"
        "            [a.b :as b] ;; aabb"
        "            ;; bbb"
        "            b))"]
       ["(ns foo.bar"
        "  (:require [a.b :as b] ;; aabb"
        "            ;; bbb"
        "            b"
        "            [c]))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo.bar"
        "  (:require"
        "   [c]"
        "   [a.b :as b] ;; aabb"
        "   ;; bbb"
        "   b))"]
       ["(ns foo.bar"
        "  (:require"
        "   [a.b :as b] ;; aabb"
        "   ;; bbb"
        "   b"
        "   [c]))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo.bar"
        "  (:require"
        "   [c]"
        "   [a.b :as b] ;; aabb"
        "   ;; bbb"
        "   b))"]
       ["(ns foo.bar"
        "  (:require"
        "    [a.b :as b] ;; aabb"
        "   ;; bbb"
        "    b"
        "    [c]))"]
       {:sort-ns-references? true
        :function-arguments-indentation :cursive}))
  (is (reformats-to?
       ["(ns foo.bar"
        "  (:require"
        "   [c]"
        "   ^:keep a"
        "   #?(:clj d)"
        "   ^{:x 1} b))"]
       ["(ns foo.bar"
        "  (:require"
        "   #?(:clj d)"
        "   ^:keep a"
        "   ^{:x 1} b"
        "   [c]))"]
       {:sort-ns-references? true}))
  (is (reformats-to?
       ["(ns foo.bar"
        "  (:require"
        "   [c]"
        "   ^:keep a"
        "   #?(:clj d)"
        "   ^{:x 1} b))"]
       ["(ns foo.bar"
        "  (:require"
        "    #?(:clj d)"
        "    ^:keep a"
        "    ^{:x 1} b"
        "    [c]))"]
       {:sort-ns-references? true
        :function-arguments-indentation :cursive})))

(deftest cursive-and-zprint-function-argument-indents-depend-on-first-element
  (let [input ["(foo"
               "bar)"
               "(:foo"
               "bar)"
               "(^:foo bar"
               "baz)"
               "(^:foo [bar]"
               "baz)"
               "(^{:foo 1} bar"
               "baz)"
               "(^{:foo 1} [bar]"
               "baz)"
               "(#^:foo bar"
               "baz)"
               "(#^:foo [bar]"
               "baz)"
               "(#^{:foo 1} bar"
               "baz)"
               "(#^{:foo 1} [bar]"
               "baz)"
               "([foo]"
               "bar)"
               "({:foo 1}"
               "bar)"
               "((foo)"
               "bar)"
               "(#=foo"
               "bar)"
               "(#=[foo]"
               "bar)"
               "(#_foo"
               "bar)"
               "(#_[foo]"
               "bar)"
               "(#(foo)"
               "bar)"
               "(#{foo}"
               "bar)"
               "(@foo"
               "bar)"
               "(#?(:clj foo)"
               "bar)"
               "(#?(:clj [foo])"
               "bar)"
               "(~foo"
               "bar)"
               "(#'foo"
               "bar)"
               "('foo"
               "bar)"
               "(`foo"
               "bar)"
               "(~@foo"
               "bar)"
               "(#:foo{:bar 1}"
               "baz)"
               "(~ @foo"
               "bar)"]]
    (testing ":cursive style uses 2 spaces unless starting with a collection"
      (is (reformats-to?
           input
           ["(foo"
            "  bar)"
            "(:foo"
            "  bar)"
            "(^:foo bar"
            "  baz)"
            "(^:foo [bar]"
            " baz)"
            "(^{:foo 1} bar"
            "  baz)"
            "(^{:foo 1} [bar]"
            " baz)"
            "(#^:foo bar"
            "  baz)"
            "(#^:foo [bar]"
            " baz)"
            "(#^{:foo 1} bar"
            "  baz)"
            "(#^{:foo 1} [bar]"
            " baz)"
            "([foo]"
            " bar)"
            "({:foo 1}"
            " bar)"
            "((foo)"
            " bar)"
            "(#=foo"
            "  bar)"
            "(#=[foo]"
            "  bar)"
            "(#_foo"
            "  bar)"
            "(#_[foo]"
            "  bar)"
            "(#(foo)"
            "  bar)"
            "(#{foo}"
            " bar)"
            "(@foo"
            "  bar)"
            "(#?(:clj foo)"
            "  bar)"
            "(#?(:clj [foo])"
            "  bar)"
            "(~foo"
            "  bar)"
            "(#'foo"
            "  bar)"
            "('foo"
            "  bar)"
            "(`foo"
            "  bar)"
            "(~@foo"
            "  bar)"
            "(#:foo{:bar 1}"
            "  baz)"
            "(~ @foo"
            "  bar)"]
           {:function-arguments-indentation :cursive})))
    (testing ":zprint uses 2 spaces if starting with a symbol, keyword, or list"
      (is (reformats-to?
           input
           ["(foo"
            "  bar)"
            "(:foo"
            "  bar)"
            "(^:foo bar"
            " baz)"
            "(^:foo [bar]"
            " baz)"
            "(^{:foo 1} bar"
            " baz)"
            "(^{:foo 1} [bar]"
            " baz)"
            "(#^:foo bar"
            " baz)"
            "(#^:foo [bar]"
            " baz)"
            "(#^{:foo 1} bar"
            " baz)"
            "(#^{:foo 1} [bar]"
            " baz)"
            "([foo]"
            " bar)"
            "({:foo 1}"
            " bar)"
            "((foo)"
            "  bar)"
            "(#=foo"
            " bar)"
            "(#=[foo]"
            " bar)"
            "(#_foo"
            " bar)"
            "(#_[foo]"
            " bar)"
            "(#(foo)"
            " bar)"
            "(#{foo}"
            " bar)"
            "(@foo"
            " bar)"
            "(#?(:clj foo)"
            " bar)"
            "(#?(:clj [foo])"
            " bar)"
            "(~foo"
            " bar)"
            "(#'foo"
            " bar)"
            "('foo"
            " bar)"
            "(`foo"
            " bar)"
            "(~@foo"
            " bar)"
            "(#:foo{:bar 1}"
            " baz)"
            "(~ @foo"
            " bar)"]
           {:function-arguments-indentation :zprint})))))

(deftest test-clojure-12-syntax
  (is (reformats-to? ["^Long/1 a"] ["^Long/1 a"])))

(deftest test-indenting-comments
  (testing "whole-line comments"
    (testing "whole-line flush-left comments"
      (testing "whole-line margin flush-left ; comments"
        (is (reformats-to?
             ["(when 42"
              "; leave me alone"
              ")"]
             ["(when 42"
              "; leave me alone"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              ";"
              ")"]
             ["(when 42"
              ";"
              "  )"]
             {:indent-line-comments? true})))
      (testing "whole-line line flush-left ;; comments"
        (is (reformats-to?
             ["(when 42"
              ";; answer"
              ")"]
             ["(when 42"
              "  ;; answer"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              ";;"
              ")"]
             ["(when 42"
              "  ;;"
              "  )"]
             {:indent-line-comments? true})))
      (testing "whole-line heading flush-left ;;; comments"
        (is (reformats-to?
             ["(when 42"
              ";;; heading"
              ")"]
             ["(when 42"
              ";;; heading"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              ";;;"
              ")"]
             ["(when 42"
              ";;;"
              "  )"]
             {:indent-line-comments? true}))))
    (testing "whole-line pre-indented comments"
      (testing "whole-line margin pre-indented ; comments"
        (is (reformats-to?
             ["(when 42"
              "     ; leave me alone"
              ")"]
             ["(when 42"
              "     ; leave me alone"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              "     ;"
              ")"]
             ["(when 42"
              "     ;"
              "  )"]
             {:indent-line-comments? true})))
      (testing "whole-line line pre-indented ;; comments"
        (is (reformats-to?
             ["(when 42"
              "     ;; answer"
              ")"]
             ["(when 42"
              "  ;; answer"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              "     ;;"
              ")"]
             ["(when 42"
              "  ;;"
              "  )"]
             {:indent-line-comments? true})))
      (testing "whole-line heading pre-indented ;;; comments"
        (is (reformats-to?
             ["(when 42"
              "     ;;; heading"
              ")"]
             ["(when 42"
              "     ;;; heading"
              "  )"]
             {:indent-line-comments? true}))
        (is (reformats-to?
             ["(when 42"
              "     ;;;"
              ")"]
             ["(when 42"
              "     ;;;"
              "  )"]
             {:indent-line-comments? true})))))
  (testing "after-code comments"
    (testing "after-code margin ; comments"
      (is (reformats-to?
           ["(when 42  ; leave me alone"
            "     :a"
            ")"]
           ["(when 42  ; leave me alone"
            "  :a)"]
           {:indent-line-comments? true}))
      (is (reformats-to?
           ["(when 42  ;"
            "     :a"
            ")"]
           ["(when 42  ;"
            "  :a)"]
           {:indent-line-comments? true})))
    (testing "after-code line ;; comments"
      (is (reformats-to?
           ["(when 42  ;; leave me alone"
            "     :a"
            ")"]
           ["(when 42  ;; leave me alone"
            "  :a)"]
           {:indent-line-comments? true}))
      (is (reformats-to?
           ["(when 42  ;;"
            "     :a"
            ")"]
           ["(when 42  ;;"
            "  :a)"]
           {:indent-line-comments? true})))
    (testing "after-code heading ;;; comments"
      (is (reformats-to?
           ["(when 42  ;;; leave me alone"
            "     :a"
            ")"]
           ["(when 42  ;;; leave me alone"
            "  :a)"]
           {:indent-line-comments? true}))
      (is (reformats-to?
           ["(when 42  ;;;"
            "     :a"
            ")"]
           ["(when 42  ;;;"
            "  :a)"]
           {:indent-line-comments? true})))))

(deftest test-multibyte-codepoints
  (is (reformats-to?
       ["[(str \"email\""
        "      \"Brian\")"
        "(str \"📚 \" (str \"email\""
        "                  \"Convert\"))"
        "(str \"⌛️ \" (str \"email\""
        "                  \"Save\"))"
        "(str \"🎓 \" (str \"email\""
        "                  \"Help\"))"
        "(str \"🔢 \" (str \"email\""
        "                   \"Leverage\"))]"]
       ["[(str \"email\""
        "      \"Brian\")"
        " (str \"📚 \" (str \"email\""
        "                 \"Convert\"))"
        " (str \"⌛️ \" (str \"email\""
        "                 \"Save\"))"
        " (str \"🎓 \" (str \"email\""
        "                 \"Help\"))"
        " (str \"🔢 \" (str \"email\""
        "                 \"Leverage\"))]"])))

(deftest test-align-map-columns
  (testing "empty maps"
    (is (reformats-to?
         ["{}"]
         ["{}"]
         {:align-map-columns? true})))
  (testing "basic aligning"
    (is (reformats-to?
         ["{:x 1"
          " :longer 2}"]
         ["{:x      1"
          " :longer 2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:longer 1"
          " :x 2}"]
         ["{:longer 1"
          " :x      2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:x 1 :longer 2}"]
         ["{:x 1 :longer 2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:x 1 :y 2"
          " :longer 2}"]
         ["{:x      1 :y 2"
          " :longer 2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:a 1 :b 2 :cc 3"
          ":dd 4 :eee 5 :f 6"
          ":ggg 7 :hh 8 :iii 9}"]
         ["{:a   1 :b   2 :cc  3"
          " :dd  4 :eee 5 :f   6"
          " :ggg 7 :hh  8 :iii 9}"]
         {:align-map-columns? true})))
  (testing "wrong alignment"
    (is (reformats-to?
         ["{:f 1"
          " :bar    2}"]
         ["{:f   1"
          " :bar 2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:foo     1"
          " :b 2}"]
         ["{:foo 1"
          " :b   2}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{   :foo 1"
          ":b       2}"]
         ["{:foo 1"
          " :b   2}"]
         {:align-map-columns? true})))
  (testing "commas"
    (is (reformats-to?
         ["{:a 1, :b 2, :cc 3"
          ":dd 4, :eee 5, :f 6"
          ":ggg 7, :hh 8, :iii 9}"]
         ["{:a   1, :b   2, :cc  3"
          " :dd  4, :eee 5, :f   6"
          " :ggg 7, :hh  8, :iii 9}"]
         {:align-map-columns? true})))
  (testing "nested maps"
    (is (reformats-to?
         ["{:a {:b 1"
          "     :c 2}"
          " :ddd {:e 3}}"]
         ["{:a   {:b 1"
          "       :c 2}"
          " :ddd {:e 3}}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:aaa {:b 1"
          "       :c 2}"
          " :d {:e 3}}"]
         ["{:aaa {:b 1"
          "       :c 2}"
          " :d   {:e 3}}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{{:a 1"
          "  :b 2} 3"
          " {:ccc 4} 5}"]
         ["{{:a 1"
          "  :b 2}   3"
          " {:ccc 4} 5}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{{:a 1"
          "  :b 2} 3"
          " :c 5}"]
         ["{{:a 1"
          "  :b 2} 3"
          " :c     5}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:a {:b 1"
          " :c 2} :ddd 3"
          " :eee {:ff 3} :e 4}"]
         ["{:a   {:b 1"
          "       :c 2}  :ddd 3"
          " :eee {:ff 3} :e   4}"]
         {:align-map-columns? true})))
  (testing "nested forms"
    (is (reformats-to?
         ["{:x (let [x 1]"
          "      (+ x 1))"
          " :yyy (let [y 2]"
          "        (+ y 2))}"]
         ["{:x   (let [x 1]"
          "        (+ x 1))"
          " :yyy (let [y 2]"
          "        (+ y 2))}"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["(def m {:x 1"
          ":longer 2})"]
         ["(def m {:x      1"
          "        :longer 2})"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["(def m {{:a 1"
          ":b 2} 3"
          ":d 4})"]
         ["(def m {{:a 1"
          "         :b 2} 3"
          "        :d     4})"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["(def m {{:a 1"
          ":b 2} [x"
          "y]"
          ":d [z]})"]
         ["(def m {{:a 1"
          "         :b 2} [x"
          "                y]"
          "        :d     [z]})"]
         {:align-map-columns? true}))
    (is (reformats-to?
         ["{:x (let [x 1"
          "          y 2]"
          "      (+ x y))"
          " :longer 3}"]
         ["{:x      (let [x 1"
          "               y 2]"
          "           (+ x y))"
          " :longer 3}"]
         {:align-map-columns? true})))
  (testing "comments"
    (is (reformats-to?
         ["{:x 1   ; a comment"
          " :longer 2}"]
         ["{:x      1   ; a comment"
          " :longer 2}"]
         {:align-map-columns? true}))))

(deftest test-align-form-columns
  (testing "basic alignment"
    (is (reformats-to?
         ["(let [x 1]"
          "  x)"]
         ["(let [x 1]"
          "  x)"]
         {:align-form-columns? true}))
    (is (reformats-to?
         ["(let [longer 1"
          "      x 2]"
          "  (+ x longer))"]
         ["(let [longer 1"
          "      x      2]"
          "  (+ x longer))"]
         {:align-form-columns? true}))
    (is (reformats-to?
         ["(def foo [aaa bb"
          "          c d]"
          "  (let [longer 1"
          "        x 2]"
          "    (+ x longer)))"]
         ["(def foo [aaa bb"
          "          c d]"
          "  (let [longer 1"
          "        x      2]"
          "    (+ x longer)))"]
         {:align-form-columns? true}))
    (is (reformats-to?
         ["(for [x [0 1 2 3 4 5]"
          "      :let [y (* x 3)]"
          "      :when (even? y)]"
          "  y)"]
         ["(for [x     [0 1 2 3 4 5]"
          "      :let  [y (* x 3)]"
          "      :when (even? y)]"
          "  y)"]
         {:align-form-columns? true})))
  (testing "alignment with maps"
    (is (reformats-to?
         ["(let [longer {:x 1"
          "              :wider 2}"
          "      y 3]"
          "  (+ x longer))"]
         ["(let [longer {:x     1"
          "              :wider 2}"
          "      y      3]"
          "  (+ x longer))"]
         {:align-form-columns? true
          :align-map-columns?  true}))
    (is (reformats-to?
         ["{:x (let [x 1"
          "          wider 2]"
          "      (+ x 1))"
          " :longer 3}"]
         ["{:x      (let [x     1"
          "               wider 2]"
          "           (+ x 1))"
          " :longer 3}"]
         {:align-form-columns? true
          :align-map-columns?  true})))
  (testing "nested alignment"
    (is (reformats-to?
         ["(let [longer (let [x 1"
          "                   yyy 2]"
          "               (+ x yyy))"
          "      y 3]"
          "  (+ x longer))"]
         ["(let [longer (let [x   1"
          "                   yyy 2]"
          "               (+ x yyy))"
          "      y      3]"
          "  (+ x longer))"]
         {:align-form-columns? true})))
  (testing "custom alignment"
    (is (reformats-to?
         ["(foobar"
          " #{:x :a"
          "   :yyy :b})"]
         ["(foobar"
          " #{:x   :a"
          "   :yyy :b})"]
         {:align-form-columns? true
          :aligned-forms {'foobar #{0}}}))))

(deftest test-realign-form
  (is (= "
{:x   1
 :yyy 2}"
         (-> "
{:x 1
 :yyy 2}"
             p/parse-string-all
             realign-form
             n/string)))
  (is (= "
{:x   [1
       2]
 :yyy 3}"
         (-> "
{:x [1
     2]
 :yyy 3}"
             p/parse-string-all
             realign-form
             n/string)))
  (is (= "
{:x   {:aaa 1
       :b 2}
 :yyy 3}"
         (-> "
{:x {:aaa 1
     :b 2}
 :yyy 3}"
             p/parse-string-all
             realign-form
             n/string))))

(deftest test-unalign-form
  (is (= "
{:x 1
 :yyy 2}"
         (-> "
{:x   1
 :yyy 2}"
             p/parse-string-all
             unalign-form
             n/string)))
  (is (= "
{:xxx 1
 :y 2}"
         (-> "
{:xxx 1
 :y   2}"
             p/parse-string-all
             unalign-form
             n/string)))
  (is (= "
{:xxx 1, :a 3
 :y 2, :bbb 4}"
         (-> "
{:xxx 1, :a   3
 :y   2, :bbb 4}"
             p/parse-string-all
             unalign-form
             n/string)))
  (is (= "
{:x [1
     2]
 :yyy 3}"
         (-> "
{:x   [1
       2]
 :yyy 3}"
             p/parse-string-all
             unalign-form
             n/string)))
  (is (= "
{:x {:aaa 1
     :b 2}
 :yyy 3}"
         (-> "
{:x   {:aaa 1
       :b 2}
 :yyy 3}"
             p/parse-string-all
             unalign-form
             n/string))))
