(ns cljfmt.io
  (:import java.io.File))

(defprotocol FileEntity
  (read-file [f])
  (update-file [f s changed?])
  (exists? [f])
  (directory? [f])
  (list-files [f])
  (relative-path [f dir]))

(extend-protocol FileEntity
  File
  (read-file [f] (slurp f))
  (update-file [f s changed?] (when changed? (spit f s)))
  (exists? [f] (.exists f))
  (directory? [f] (.isDirectory f))
  (list-files [f] (file-seq f))
  (relative-path [f ^File dir]
    (-> (.toAbsolutePath (.toPath dir))
        (.relativize (.toAbsolutePath (.toPath f)))
        (.toString))))

(deftype StdIO [in out]
  FileEntity
  (read-file [_] (slurp in))
  (update-file [_ s _] (binding [*out* out] (print s)) (flush))
  (exists? [_] true)
  (directory? [_] false)
  (list-files [_] nil)
  (relative-path [_ _] "STDIN"))

(defn file-entity [path]
  (cond
    (instance? File path) path
    (= "-" path) (->StdIO *in* *out*)
    :else (File. path)))
