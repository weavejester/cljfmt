(ns leiningen.cljfmt.diff
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [difflib DiffUtils Delta$TYPE]))

(defn lines [s]
  (str/split s #"\n"))

(defn unlines [ss]
  (str/join "\n" ss))

(defn unified-diff
  ([filename original revised]
   (unified-diff filename original revised 3))
  ([filename original revised context]
   (unlines (DiffUtils/generateUnifiedDiff
             (str (io/file "a" filename))
             (str (io/file "b" filename))
             (lines original)
             (DiffUtils/diff (lines original) (lines revised))
             context))))
