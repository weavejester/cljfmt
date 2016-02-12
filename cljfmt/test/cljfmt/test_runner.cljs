(ns cljfmt.test-runner
  (:require [cljs.nodejs :as nodejs]
            [cljs.test :refer-macros [run-tests]]
            [cljfmt.core-test :as ct]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests 'cljfmt.core-test))

(set! *main-cli-fn* -main)
