(ns smoke-test
  (:require [babashka.process :as p]
            [clojure.test :refer [deftest is run-tests]]))

(defn- shell-ok?
  ([cmd] (shell-ok? cmd {}))
  ([cmd opts]
   (zero? (:exit (p/shell (assoc opts :continue true) cmd)))))

(deftest check-lein-plugin
  (is (shell-ok? "lein cljfmt check" {:dir "lein-cljfmt"})))

(deftest check-clj-tool
  (is (shell-ok? "clojure -M -m cljfmt.main check cljfmt/src cljfmt/test")))

(deftest check-standalone
  (is (shell-ok? "cljfmt/target/cljfmt check cljfmt/src cljfmt/test")))

(defn -main []
  (let [{:keys [fail error]} (run-tests 'smoke-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
