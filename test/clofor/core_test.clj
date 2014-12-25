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
           "(do (foo)\n    (bar))"))))

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
