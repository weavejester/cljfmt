(ns leiningen.cljfmt.diff
  (:require [clojure.string :as str])
  (:import [difflib DiffUtils Delta$TYPE]))

(defn lines [s]
  (str/split s #"\n"))

(def delta-types
  {Delta$TYPE/CHANGE :change
   Delta$TYPE/DELETE :delete
   Delta$TYPE/INSERT :insert})

(defn diff [a b]
  (let [patch (DiffUtils/diff (lines a) (lines b))]
    (for [delta (.getDeltas patch)]
      [(-> delta .getType delta-types)
       (-> delta .getOriginal .getPosition)
       (-> delta .getOriginal .getLines seq)
       (-> delta .getRevised  .getLines seq)])))

