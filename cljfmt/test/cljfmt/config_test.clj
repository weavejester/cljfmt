(ns cljfmt.config-test
  (:require [cljfmt.config :as config]
            [clojure.test :refer [deftest is testing]]))

(deftest test-convert-legacy-keys
  (is (= {:indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:indents {'foo [[:inner 0]]}})))
  (is (= {:extra-indents {'foo [[:inner 0]]}}
         (config/convert-legacy-keys {:legacy/merge-indents? true
                                      :indents {'foo [[:inner 0]]}}))))

(deftest read-config-test
  (testing "Ensure that reading .clj config files works for valid data"
    (let [temp-file (java.io.File/createTempFile "test" ".clj")]
      (spit temp-file "{:key \"value\"}")
      (is (= {:key "value"} (config/read-config temp-file)))
      (.delete temp-file)))

  (testing "Ensure that reading .edn config files works"
    (let [temp-file (java.io.File/createTempFile "test" ".edn")]
      (spit temp-file "{:key \"value\"}")
      (is (= {:key "value"} (config/read-config temp-file)))
      (.delete temp-file))))

(deftest load-config-test
  (let [temp-dir (java.io.File/createTempFile "test" "")
        _ (do (.delete temp-dir)
              (.mkdir temp-dir))
        path (.getPath temp-dir)
        clj-file (java.io.File. temp-dir ".cljfmt.clj")
        delete-regex-ks #(dissoc % :file-pattern :indents)
        config (delete-regex-ks config/default-config)
        new-config (-> config
                       (update :remove-consecutive-blank-lines? not)
                       delete-regex-ks)
        config-with-read-clj (assoc new-config :read-clj-config-files? true)
        load-config #(delete-regex-ks (config/load-config %1 %2))]
    (testing "load-config ignores .clj config files when flag is false 
              (default)"
      (spit clj-file (pr-str new-config))
      (is (= (load-config path config) config)))

    (testing "load-config loads .clj config files when flag is true"
      (spit clj-file (pr-str config-with-read-clj))
      (is (= (load-config path config-with-read-clj)
             config-with-read-clj)))

    (testing "load-config always loads .edn config files"
      (let [edn-file (java.io.File. temp-dir ".cljfmt.edn")]
        (spit edn-file (pr-str new-config))
        (is (= (load-config path new-config)
               new-config))))

    (.delete temp-dir)))