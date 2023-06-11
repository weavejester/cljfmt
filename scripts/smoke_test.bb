(ns smoke-test
  (:require [babashka.process :as p]
            [clojure.test :refer [deftest is run-tests]]))

(deftest check-lein-plugin
  (is (zero? (:exit (p/shell {:dir "lein-cljfmt", :continue true}
                             "lein cljfmt check")))))

(deftest check-clj-tool
  (is (zero? (:exit (p/shell
                     "clj -M -m cljfmt.main check cljfmt/src cljfmt/test")))))

(deftest check-standalone
  (is (zero? (:exit (p/shell
                     "cljfmt/target/cljfmt check cljfmt/src cljfmt/test")))))

(defn -main []
  (let [{:keys [fail error]} (run-tests 'smoke-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
