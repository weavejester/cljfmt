(ns cljfmt.test-util.clojure
  (:require [clojure.test :as test]
            [cljfmt.test-util.common :as common]))

(defmethod test/assert-expr 'reformats-to? [msg form]
  `(test/do-report (common/assert-reformats-to ~msg ~@(rest form))))
