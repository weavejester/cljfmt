(ns cljfmt.config-test
  (:require [cljfmt.config :as config]
            [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]))

(deftest test-convert-legacy-keys
  (is (= {:indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:indents {'foo [[:inner 0]]}})))
  (is (= {:extra-indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:legacy/merge-indents? true
                                      :indents {'foo [[:inner 0]]}}))))

(deftest test-load-config-extra-configs
  (testing ":extra-configs allows you to import :extra-indents"
    (is (= (config/load-config
            (io/resource "cljfmt/config_test/empty-cljfmt.edn"))
           (-> (config/load-config
                (io/resource "cljfmt/config_test/cljfmt.edn"))
               (dissoc :extra-configs)
               (assoc :extra-indents {})))
        (str "should only differ by :extra-indents (via :extra-configs)."
             " All other keys are ignored."))
    (is (= '{;; exists only in base config `test_resources/cljfmt.edn`
             com.unrelated/a [[:inner 0]]
             com.foo/a [[:inner 0]],
             ;; exists only in `test_resources/extra1-cljfmt.edn`
             com.foo/b [[:inner 1]],
             ;; overwritten in `test_resources/extra2-cljfmt.edn`
             com.foo/c [[:inner 2]],
             com.foo/d [[:inner 2]],
             ;; exists only in `test_resources/extra2-cljfmt.edn`
             com.foo/e [[:inner 2]]}
           (:extra-indents (config/load-config
                            (io/resource "cljfmt/config_test/cljfmt.edn"))))
        (str "should respect :extra-configs in order (later is higher-prio),"
             " with base highest prio."))))
