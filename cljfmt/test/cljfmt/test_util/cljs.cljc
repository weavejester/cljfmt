(ns cljfmt.test-util.cljs
  (:require [cljs.test :as test]
            [cljfmt.test-util.common :as common]))

#?(:clj
   (defmethod test/assert-expr 'reformats-to? [_env msg form]
     `(test/do-report (common/assert-reformats-to ~msg ~@(rest form)))))
