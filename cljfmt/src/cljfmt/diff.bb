(ns cljfmt.diff
  (:require [borkdude.text-diff :as td]))

(defn unified-diff
  ([filename original revised]
   (unified-diff filename original revised 3))
  ([filename original revised context]
   (td/unified-diff original revised {:filename filename :context context})))

(defn- ansi-colors? []
  (and (System/console)
       (System/getenv "TERM")
       (not (System/getenv "NO_COLOR"))))

(defn colorize-diff [diff-text]
  (if (ansi-colors?)
    (td/colorize-unified-diff diff-text)
    diff-text))
