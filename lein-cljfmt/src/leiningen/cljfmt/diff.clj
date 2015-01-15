(ns leiningen.cljfmt.diff
  (:require [clojure.string :as str])
  (:import [difflib DiffUtils Delta$TYPE]))

(defn lines [s]
  (str/split s #"\n"))

(def delta-types
  {Delta$TYPE/CHANGE :change
   Delta$TYPE/DELETE :delete
   Delta$TYPE/INSERT :insert})

(defn chunk->map [chunk]
  (let [lines (vec (.getLines chunk))
        start (.getPosition chunk)]
    {:start start
     :end   (+ start (count lines))
     :lines lines}))

(defn diff [original revised]
  (let [patch (DiffUtils/diff (lines original) (lines revised))]
    (for [d (.getDeltas patch)]
      {:type     (-> d .getType delta-types)
       :original (some-> d .getOriginal chunk->map)
       :revised  (some-> d .getRevised chunk->map)})))

(defn line-diff [{:keys [type original revised] :as delta}]
  (case type
    :insert (map (fn [num content] [:insert num content])
                 (range (:start revised) (:end revised))
                 (:lines revised))
    :delete (map (fn [num content] [:delete num content])
                 (range (:start original) (:end original))
                 (:lines original))
    :change (concat (line-diff (assoc delta :type :delete))
                    (line-diff (assoc delta :type :insert)))))
