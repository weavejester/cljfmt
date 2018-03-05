(ns cljfmt.core-test
  (:require [#?@(:clj (clojure.test :refer)
                 :cljs (cljs.test :refer-macros)) [deftest testing is]]
            [cljfmt.core :refer [reformat-string]]
            [clojure.string :as str]))

(deftest test-indent
  (testing "list indentation"
    (is (= (reformat-string "(foo bar\nbaz\nquz)")
           "(foo bar\n     baz\n     quz)"))
    (is (= (reformat-string "(foo\nbar\nbaz)")
           "(foo\n bar\n baz)")))

  (testing "block indentation"
    (is (= (reformat-string "(if (= x 1)\n:foo\n:bar)")
           "(if (= x 1)\n  :foo\n  :bar)"))
    (is (= (reformat-string "(do\n(foo)\n(bar))")
           "(do\n  (foo)\n  (bar))"))
    (is (= (reformat-string "(do (foo)\n(bar))")
           "(do (foo)\n    (bar))"))
    (is (= (reformat-string "(deftype Foo\n[x]\nBar)")
           "(deftype Foo\n         [x]\n  Bar)"))
    (is (= (reformat-string "(cond->> x\na? a\nb? b)")
           "(cond->> x\n  a? a\n  b? b)")))

  (testing "constant indentation"
    (is (= (reformat-string "(def foo\n\"Hello World\")")
           "(def foo\n  \"Hello World\")"))
    (is (= (reformat-string "(defn foo [x]\n(+ x 1))")
           "(defn foo [x]\n  (+ x 1))"))
    (is (= (reformat-string "(defn foo\n[x]\n(+ x 1))")
           "(defn foo\n  [x]\n  (+ x 1))"))
    (is (= (reformat-string "(defn foo\n([] 0)\n([x]\n(+ x 1)))")
           "(defn foo\n  ([] 0)\n  ([x]\n   (+ x 1)))"))
    (is (= (reformat-string "(fn [x]\n(foo bar\nbaz))")
           "(fn [x]\n  (foo bar\n       baz))"))
    (is (= (reformat-string "(fn [x] (foo bar\nbaz))")
           "(fn [x] (foo bar\n             baz))")))

  (testing "inner indentation"
    (is (= (reformat-string "(letfn [(foo [x]\n(* x x))]\n(foo 5))")
           "(letfn [(foo [x]\n          (* x x))]\n  (foo 5))"))
    (is (= (reformat-string "(reify Closeable\n(close [_]\n(prn :closed)))")
           "(reify Closeable\n  (close [_]\n    (prn :closed)))"))
    (is (= (reformat-string "(defrecord Foo [x]\nCloseable\n(close [_]\n(prn x)))")
           "(defrecord Foo [x]\n  Closeable\n  (close [_]\n    (prn x)))")))

  (testing "data structure indentation"
    (is (= (reformat-string "[:foo\n:bar\n:baz]")
           "[:foo\n :bar\n :baz]"))
    (is (= (reformat-string "{:foo 1\n:bar 2}")
           "{:foo 1\n :bar 2}"))
    (is (= (reformat-string "#{:foo\n:bar\n:baz}")
           "#{:foo\n  :bar\n  :baz}"))
    (is (= (reformat-string "{:foo [:bar\n:baz]}")
           "{:foo [:bar\n       :baz]}")))

  (testing "embedded structures"
    (is (= (reformat-string "(let [foo {:x 1\n:y 2}]\n(:x foo))")
           "(let [foo {:x 1\n           :y 2}]\n  (:x foo))"))
    (is (= (reformat-string "(if foo\n(do bar\nbaz)\nquz)")
           "(if foo\n  (do bar\n      baz)\n  quz)")))

  (testing "namespaces"
    (is (= (reformat-string "(t/defn foo [x]\n(+ x 1))")
           "(t/defn foo [x]\n  (+ x 1))"))
    (is (= (reformat-string "(t/defrecord Foo [x]\nCloseable\n(close [_]\n(prn x)))")
           "(t/defrecord Foo [x]\n  Closeable\n  (close [_]\n    (prn x)))"))
    (is (= (-> (str "(ns example\n"
                    "(:require [thing.core :as t]))\n\n"
                    "(t/defn foo [x]\n"
                    "(+ x 1))\n\n"
                    "(defn foo [x]\n"
                    "(+ x 1))\n")
               (reformat-string {:indents {'thing.core/defn [[:inner 0]]}
                                 #?@(:cljs [:alias-map {"t" "thing.core"}])}))
           (str "(ns example\n"
                "    (:require [thing.core :as t]))\n\n"
                "(t/defn foo [x]\n"
                "  (+ x 1))\n\n"
                "(defn foo [x]\n"
                "      (+ x 1))\n")))
    (is (= (-> (str "(ns example\n"
                    "(:require [thing.core :as t]))\n\n"
                    "(t/defrecord Foo [x]\n"
                    "Closeable\n"
                    "(close [_]\n"
                    "(prn x)))\n\n"
                    "(defrecord Foo [x]\n"
                    "Closeable\n"
                    "(close [_]\n"
                    "(prn x)))\n")
               (reformat-string {:indents {'thing.core/defrecord [[:inner 0]]}
                                 #?@(:cljs [:alias-map {"t" "thing.core"}])}))
           (str "(ns example\n"
                "    (:require [thing.core :as t]))\n\n"
                "(t/defrecord Foo [x]\n"
                "  Closeable\n"
                "  (close [_]\n"
                "         (prn x)))\n\n"
                "(defrecord Foo [x]\n"
                "           Closeable\n"
                "           (close [_]\n"
                "                  (prn x)))\n"))))

  (testing "function #() syntax"
    (is (= (reformat-string "#(while true\n(println :foo))")
           "#(while true\n   (println :foo))"))
    (is (= (reformat-string "#(reify Closeable\n(close [_]\n(prn %)))")
           "#(reify Closeable\n   (close [_]\n     (prn %)))")))

  (testing "multiple arities"
    (is (= (reformat-string "(fn\n([x]\n(foo)\n(bar)))")
           "(fn\n  ([x]\n   (foo)\n   (bar)))")))

  (testing "comments"
    (is (= (reformat-string ";foo\n(def x 1)")
           ";foo\n(def x 1)"))
    (is (= (reformat-string "(ns foo.core)\n\n;; foo\n(defn foo [x]\n(inc x))")
           "(ns foo.core)\n\n;; foo\n(defn foo [x]\n  (inc x))"))
    (is (= (reformat-string ";; foo\n(ns foo\n(:require bar))")
           ";; foo\n(ns foo\n  (:require bar))"))
    (is (= (reformat-string "(defn foo [x]\n  ;; +1\n(inc x))")
           "(defn foo [x]\n  ;; +1\n  (inc x))"))
    (is (= (reformat-string "(let [;foo\n x (foo bar\n baz)]\n x)")
           "(let [;foo\n      x (foo bar\n             baz)]\n  x)"))
    (is (= (reformat-string "(binding [x 1] ; foo\nx)")
           "(binding [x 1] ; foo\n  x)")))

  (testing "metadata"
    (is (= (reformat-string "(defonce ^{:doc \"foo\"}\nfoo\n:foo)")
           "(defonce ^{:doc \"foo\"}\n  foo\n  :foo)"))
    (is (= (reformat-string "(def ^:private\nfoo\n:foo)")
           "(def ^:private\n  foo\n  :foo)"))
    (is (= (reformat-string "(def ^:private foo\n:foo)")
           "(def ^:private foo\n  :foo)")))

  (testing "fuzzy matches"
    (is (= (reformat-string "(with-foo x\ny\nz)")
           "(with-foo x\n  y\n  z)"))
    (is (= (reformat-string "(defelem foo [x]\n[:foo x])")
           "(defelem foo [x]\n  [:foo x])")))

  (testing "comment before ending bracket"
    (is (= (reformat-string "(foo a ; b\nc ; d\n)")
           "(foo a ; b\n     c ; d\n     )"))
    (is (= (reformat-string "(do\na ; b\nc ; d\n)")
           "(do\n  a ; b\n  c ; d\n  )"))
    (is (= (reformat-string "(let [x [1 2 ;; test1\n2 3 ;; test2\n]])")
           "(let [x [1 2 ;; test1\n         2 3 ;; test2\n         ]])")))

  (testing "indented comments with blank lines"
    (is (= (reformat-string "(;a\n\n ;b\n )")
           "(;a\n\n ;b\n )")))

  (testing "indentated forms in letfn block"
    (is (= (reformat-string "(letfn [(f [x]\nx)]\n(let [x (f 1)]\n(str x 2\n3 4)))")
           (str "(letfn [(f [x]\n          x)]\n"
                "  (let [x (f 1)]\n    (str x 2\n         3 4)))"))))

  (testing "miltiline right hand side forms"
    (is (= (reformat-string "(list foo :bar (fn a\n([] nil)\n([b] b)))")
           "(list foo :bar (fn a\n                 ([] nil)\n                 ([b] b)))")))

  (testing "reader conditionals"
    (is (= (reformat-string "#?(:clj foo\n:cljs bar)")
           "#?(:clj foo\n   :cljs bar)"))
    (is (= (reformat-string "#?@(:clj foo\n:cljs bar)")
           "#?@(:clj foo\n    :cljs bar)"))))

(deftest test-surrounding-whitespace
  (testing "surrounding spaces"
    (is (= (reformat-string "( foo bar )")
           "(foo bar)"))
    (is (= (reformat-string "[ 1 2 3 ]")
           "[1 2 3]"))
    (is (= (reformat-string "{  :x 1, :y 2 }")
           "{:x 1, :y 2}")))

  (testing "surrounding newlines"
    (is (= (reformat-string "(\n  foo\n)")
           "(foo)"))
    (is (= (reformat-string "(  \nfoo\n)")
           "(foo)"))
    (is (= (reformat-string "(foo  \n)")
           "(foo)"))
    (is (= (reformat-string "(foo\n  )")
           "(foo)"))
    (is (= (reformat-string "[\n1 2 3\n]")
           "[1 2 3]"))
    (is (= (reformat-string "{\n:foo \"bar\"\n}")
           "{:foo \"bar\"}"))
    (is (= (reformat-string "( let [x 3\ny 4]\n(+ (* x x\n)(* y y)\n))")
           "(let [x 3\n      y 4]\n  (+ (* x x) (* y y)))"))))

(deftest test-missing-whitespace
  (testing "collections"
    (is (= (reformat-string "(foo(bar baz)qux)")
           "(foo (bar baz) qux)"))
    (is (= (reformat-string "(foo)bar(baz)")
           "(foo) bar (baz)"))
    (is (= (reformat-string "(foo[bar]#{baz}{quz bang})")
           "(foo [bar] #{baz} {quz bang})")))

  (testing "reader conditionals"
    (is (= (reformat-string "#?(:cljs(bar 1) :clj(foo 2))")
           "#?(:cljs (bar 1) :clj (foo 2))"))
    (is (= (reformat-string "#?@(:cljs[foo bar] :clj[baz quux])")
           "#?@(:cljs [foo bar] :clj [baz quux])"))))

(deftest test-consecutive-blank-lines
  (is (= (reformat-string "(foo)\n\n(bar)")
         "(foo)\n\n(bar)"))
  (is (= (reformat-string "(foo)\n\n\n(bar)")
         "(foo)\n\n(bar)"))
  (is (= (reformat-string "(foo)\n \n \n(bar)")
         "(foo)\n\n(bar)"))
  (is (= (reformat-string "(foo)\n\n\n\n\n(bar)")
         "(foo)\n\n(bar)"))
  (is (= (reformat-string "(foo)\n\n;bar\n\n(baz)")
         "(foo)\n\n;bar\n\n(baz)"))
  (is (= (reformat-string "(foo)\n;bar\n;baz\n;qux\n(bang)")
         "(foo)\n;bar\n;baz\n;qux\n(bang)"))
  (is (= (reformat-string "(foo\n)\n\n(bar)")
         "(foo)\n\n(bar)")))

(deftest test-trailing-whitespace
  (testing "trailing-whitespace"
    (is (= (reformat-string "(foo bar) ")
           "(foo bar)"))
    (is (= (reformat-string "(foo bar)\n")
           "(foo bar)\n"))
    (is (= (reformat-string "(foo bar) \n ")
           "(foo bar)\n"))
    (is (= (reformat-string "(foo bar) \n(foo baz)")
           "(foo bar)\n(foo baz)"))
    (is (= (reformat-string "(foo bar)\t\n(foo baz)")
           "(foo bar)\n(foo baz)")))

  (testing "preserve surrounding whitespace"
    (is (= (reformat-string "( foo bar ) \n" {:remove-surrounding-whitespace? false})
           "( foo bar )\n"))
    (is (= (reformat-string
            "( foo bar )   \n( foo baz )\n" {:remove-surrounding-whitespace? false})
           "( foo bar )\n( foo baz )\n"))))

(deftest test-options
  (is (= (reformat-string "(foo)\n\n\n(bar)" {:remove-consecutive-blank-lines? false})
         "(foo)\n\n\n(bar)"))
  (is (= (reformat-string "(  foo  )" {:remove-surrounding-whitespace? false})
         "(  foo  )"))
  (is (= (reformat-string "(foo(bar))" {:insert-missing-whitespace? false})
         "(foo(bar))"))
  (is (= (reformat-string "(foo\nbar)" {:indents '{foo [[:block 0]]}})
         "(foo\n  bar)"))
  (is (= (reformat-string "(do\nfoo\nbar)" {:indents {}})
         "(do\n foo\n bar)"))
  (is (= (reformat-string "(do\nfoo\nbar)" {:indentation? false})
         "(do\nfoo\nbar)"))
  (is (= (reformat-string "(foo bar) \n(foo baz)" {:remove-trailing-whitespace? false})
         "(foo bar) \n(foo baz)"))
  (is (= (reformat-string "(foo bar) \n" {:remove-trailing-whitespace? false})
         "(foo bar) \n")))

(deftest test-parsing
  (is (= (reformat-string ";foo") ";foo"))
  (is (= (reformat-string "::foo") "::foo"))
  (is (= (reformat-string "::foo/bar") "::foo/bar"))
  (is (= (reformat-string "foo:bar") "foo:bar"))
  (is (= (reformat-string "#_(foo\nbar)") "#_(foo\n   bar)"))
  (is (= (reformat-string "(juxt +' -')") "(juxt +' -')"))
  (is (= (reformat-string "#\"(?i)foo\"") "#\"(?i)foo\""))
  (is (= (reformat-string "#\"a\nb\"") "#\"a\nb\"")))
