(ns cljfmt.core-test
  (:require [#?@(:clj (clojure.test :refer)
                 :cljs (cljs.test :refer-macros)) [deftest testing is]]
            [cljfmt.core :refer [reformat-string default-line-separator
                                 normalize-newlines find-line-separator
                                 replace-newlines wrap-normalize-newlines]]
            [cljfmt.test-util.common :as common]
            #?(:clj [cljfmt.test-util.clojure]))
  #?(:cljs (:require-macros [cljfmt.test-util.cljs])))

(deftest test-indent
  (testing "list indentation"
    (is (reformats-to?
         ["(foo bar"
          "baz"
          "quz)"]
         ["(foo bar"
          "     baz"
          "     quz)"]))

    (is (reformats-to?
         ["(foo"
          "bar"
          "baz)"]
         ["(foo"
          " bar"
          " baz)"])
        "to first arg"))

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
          "(+ x 1)))"]
         ["(defn foo"
          "  ([] 0)"
          "  ([x]"
          "   (+ x 1)))"])
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
          "    (prn x)))"])))

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
        "applies custom indentation to namespaced defrecord"))

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
        "indentation should not be lost after comment line"))

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
        "hanging metadata and name on def does not hang subsequent indentation"))

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
          " )"])))

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
        "splicing syntax"))

  (testing "namespaced maps"
    (is (reformats-to?
         ["#:clj {:a :b"
          ":c :d}"]
         ["#:clj {:a :b"
          "       :c :d}"]))))

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
          "  (+ (* x x) (* y y)))"]))))

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
        "indents properly")))

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
       ["( "
        "foo"
        " )"]
       ["( "
        " foo"
        " )"]
       {:remove-surrounding-whitespace? false
        :remove-trailing-whitespace? false}))
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
       {:split-keypairs-over-multiple-lines? true})))

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
       ["foo:bar"]
       ["foo:bar"])
      "a name with an embedded colon")
  (is (reformats-to?
       ["#_(foo"
        "bar)"]
       ["#_(foo"
        "   bar)"]))
  (is (reformats-to?
       ["(juxt +' -')"]
       ["(juxt +' -')"]))
  (is (reformats-to?
       ["#\"(?i)foo\""]
       ["#\"(?i)foo\""]))
  (is (= "#\"a\nb\""
         (reformat-string "#\"a\nb\""))
      "regular expression with embedded newline"))

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
