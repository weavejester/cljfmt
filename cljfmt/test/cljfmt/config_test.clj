(ns cljfmt.config-test
  (:require [cljfmt.config :as config]
            [clojure.test :refer [deftest is]]))

(deftest test-convert-legacy-keys
  (is (= {:indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:indents {'foo [[:inner 0]]}})))
  (is (= {:extra-indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:legacy/merge-indents? true
                                      :indents {'foo [[:inner 0]]}}))))
