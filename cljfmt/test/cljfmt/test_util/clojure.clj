(ns cljfmt.test-util.clojure
  (:require [cljfmt.test-util.common :as common]
            [clojure.test :as test]))

(defmethod test/assert-expr 'reformats-to? [msg form]
  `(test/do-report (common/assert-reformats-to ~msg ~@(rest form))))
