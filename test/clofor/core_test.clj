(ns clofor.core-test
  (:require [clojure.test :refer :all]
            [clofor.core :refer :all]))

(deftest test-indent
  (testing "function indentation"
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
           "(do (foo)\n    (bar))")))

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
           "(if foo\n  (do bar\n      baz)\n  quz)"))))

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
    (is (= (reformat-string "[\n1 2 3\n]")
           "[1 2 3]"))
    (is (= (reformat-string "{\n:foo \"bar\"\n}")
           "{:foo \"bar\"}"))))

(deftest test-missing-whitespace
  (is (= (reformat-string "(foo(bar baz)qux)")
         "(foo (bar baz) qux)"))
  (is (= (reformat-string "(foo)bar(baz)")
         "(foo) bar (baz)"))
  (is (= (reformat-string "(foo[bar]#{baz}{quz bang})")
         "(foo [bar] #{baz} {quz bang})")))
