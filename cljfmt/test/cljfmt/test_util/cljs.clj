(ns cljfmt.test-util.cljs
  (:require [cljfmt.test-util.common :as common]
            [cljs.test :as test]))

(defmethod test/assert-expr 'reformats-to? [_env msg form]
  `(test/do-report (common/assert-reformats-to ~msg ~@(rest form))))
