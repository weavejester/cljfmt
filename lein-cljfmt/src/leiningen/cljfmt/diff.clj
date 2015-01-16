(ns leiningen.cljfmt.diff
  (:import [difflib DiffUtils Delta$TYPE])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

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

(def ansi-colors
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})

(defn colorize [s color]
  (str \u001b (ansi-colors color) s \u001b (ansi-colors :reset)))

(defn colorize-diff [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (colorize "$1" :red))))
