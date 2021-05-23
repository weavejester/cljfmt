(ns cljfmt.test-util.cljs
  (:require [cljs.test :as test]
            [cljfmt.test-util.common :as common]))

(defmethod test/assert-expr 'reformats-to? [_env msg form]
  `(let [report-result# (common/reformats-to-event ~msg ~form)]
     (test/do-report report-result#)
     (= :pass (:type report-result#))))
