(ns cljfmt.diff
  (:import [difflib DiffUtils]
           [java.io File])
  (:require [clojure.string :as str]))

(def ^:private missing-nl-marker "\u0000")

(defn- lines [s]
  (if (not-empty s)
    (str/split s #"\n")
    []))

(defn- unlines [ss]
  (str/join "\n" ss))

(defn- missing-nl? [file-content]
  (and (not-empty file-content)
       (not (str/ends-with? file-content "\n"))))

(defn- annotate-missing-nl
  [file-a-content
   file-b-content]
  (let [file-a-lines (lines file-a-content)]
    (cond
      ;; if file-a is missing a newline:
      ;; (this triggers when annotating original lines)
      (missing-nl? file-a-content)
      ;; appending the null-character marker
      ;; to the last existing line of file-a,
      ;; to make `DiffUtils` see it as a modified line
      ;; in the original lines:
      (update file-a-lines (dec (count file-a-lines)) str missing-nl-marker)

      ;; if file-a ends with a newline, but file-b doesn't:
      ;; (this triggers when annotating revised lines)
      (missing-nl? file-b-content)
      ;; adding a trailing "" to the lines of file-a,
      ;; (which represent the line containing only "\n" character
      ;;  which was removed by `str/split`)
      ;; that will make `DiffUtils` show a `+` empty line diff
      ;; in the revised lines:
      (conj file-a-lines "")

      :else file-a-lines)))

(defn- render-newline-marker [line]
  (cond
    ;; if it is an affected diff line which has a marker:
    (and (str/includes? line missing-nl-marker)
         (or (str/starts-with? line "-") (str/starts-with? line "+")))
    ;; then removing the null-character marker
    ;; and adding git-like "\ No newline .." line:
    [(str/replace line missing-nl-marker "") "\\ No newline at end of file"]

    ;; if the line includes the marker,
    ;; but somehow it is not part of the affected diffs:
    (str/includes? line missing-nl-marker)
    ;; this ensures that the marker is always removed:
    [(str/replace line missing-nl-marker "")]

    :else [line]))

(defn unified-diff
  ([filename original revised]
   (unified-diff filename original revised 3))
  ([filename original revised context]
   (let [original-lines (annotate-missing-nl original revised)
         revised-lines (annotate-missing-nl revised original)]
     (->> (DiffUtils/generateUnifiedDiff
           (str "a" File/separator filename)
           (str "b" File/separator filename)
           original-lines
           (DiffUtils/diff original-lines revised-lines)
           context)
          (mapcat render-newline-marker)
          unlines))))

(def ^:private ansi-colors
  {:reset "[0m"
   :red   "[031m"
   :green "[032m"
   :cyan  "[036m"})

(defn- ansi-colors? []
  (and (System/console)
       (System/getenv "TERM")
       (not (System/getenv "NO_COLOR"))))

(defn- colorize [s color]
  (if (ansi-colors?)
    (str \u001b (ansi-colors color) s \u001b (ansi-colors :reset))
    s))

(defn colorize-diff [diff-text]
  (-> diff-text
      (str/replace #"(?m)^(@@.*@@)$"       (colorize "$1" :cyan))
      (str/replace #"(?m)^(\+(?!\+\+).*)$" (colorize "$1" :green))
      (str/replace #"(?m)^(-(?!--).*)$"    (colorize "$1" :red))))
