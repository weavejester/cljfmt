(ns cljfmt.test-util.common
  (:require [clojure.string :as str]
            [cljfmt.core :as cljfmt]))

(defmacro reformats-to-event [msg form]
  `(let [in-lines# ~(nth form 1)
         expected-lines# ~(nth form 2)
         options# ~(nth form 3 {})
         actual-lines# (-> (str/join "\n" in-lines#)
                           (cljfmt/reformat-string options#)
                           (str/split #"\n" -1))
         result# (= expected-lines# actual-lines#)]
     {:type (if result# :pass :fail)
      :message ~msg
      :expected expected-lines#
      :actual actual-lines#}))
