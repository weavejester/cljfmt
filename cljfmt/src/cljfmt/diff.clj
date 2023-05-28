(ns cljfmt.diff
  (:import [difflib DiffUtils]
           [java.io File])
  (:require [clojure.string :as str]))

(defn- lines [s]
  (str/split s #"\n"))

(defn- unlines [ss]
  (str/join "\n" ss))

(defn unified-diff
  ([filename original revised]
   (unified-diff filename original revised 3))
  ([filename original revised context]
   (unlines (DiffUtils/generateUnifiedDiff
             (str "a" File/separator filename)
             (str "b" File/separator filename)
             (lines original)
             (DiffUtils/diff (lines original) (lines revised))
             context))))

(def ^:private ansi-colors
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})

(defn- ansi-terminal? []
  (and (System/console) (System/getenv "TERM")))

(defn- colorize [s color]
  (if (ansi-terminal?)
    (str \u001b (ansi-colors color) s \u001b (ansi-colors :reset))
    s))

(defn colorize-diff [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (colorize "$1" :red))))
