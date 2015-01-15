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
    (for [d (.getDeltas patch)]
      {:type     (-> d .getType delta-types)
       :line     (-> d .getOriginal .getPosition)
       :original (-> d .getOriginal .getLines seq)
       :revised  (-> d .getRevised  .getLines seq)})))

(defn line-diff [{:keys [type line original revised] :as delta}]
  (case type
    :insert (map (fn [num content] [:insert num content])
                 (range line (+ line (count revised)))
                 revised)
    :delete (map (fn [num content] [:delete num content])
                 (range line (+ line (count original)))
                 original)
    :change (concat (line-delta (assoc delta :type :delete))
                    (line-delta (assoc delta :type :insert)))))
