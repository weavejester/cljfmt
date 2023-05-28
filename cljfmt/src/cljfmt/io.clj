(ns cljfmt.io
  (:import java.io.File))

(defprotocol FileEntity
  (read-file [f])
  (write-file [f s])
  (exists? [f])
  (directory? [f])
  (list-files [f])
  (relative-path [f dir]))

(extend-protocol FileEntity
  File
  (read-file [f] (slurp f))
  (write-file [f s] (spit f s))
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
  (write-file [_ s] (binding [*out* out] (print s)) (flush))
  (exists? [_] true)
  (directory? [_] false)
  (list-files [_] nil)
  (relative-path [_ _] "STDIN"))

(defn file-entity [^String path]
  (if (= "-" path)
    (->StdIO *in* *out*)
    (File. path)))
