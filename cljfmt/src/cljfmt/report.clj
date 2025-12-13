(ns cljfmt.report
  (:require [cljfmt.io :as io]
            [clojure.stacktrace :as st]))

(def ^:dynamic *no-output* false)

(defn warn [& args]
  (when-not *no-output*
    (binding [*out* *err*]
      (apply println args))))

(defn- print-stack-trace [ex]
  (when-not *no-output*
    (binding [*out* *err*]
      (st/print-stack-trace ex))))

(defn- print-file-status [options status]
  (let [path (io/project-path options (:file status))]
    (when-let [ex (:exception status)]
      (warn "Failed to format file:" path)
      (print-stack-trace ex))
    (when (:reformatted status)
      (warn "Reformatted" path))
    (when-let [diff (:diff status)]
      (warn path "has incorrect formatting")
      (println diff))))

(def zero-counts {:okay 0, :incorrect 0, :error 0})

(defn- merge-counts
  ([]    zero-counts)
  ([a]   a)
  ([a b] (merge-with + a b)))

(defn- print-final-count [counts]
  (let [error     (:error counts 0)
        incorrect (:incorrect counts 0)]
    (when-not (zero? error)
      (warn error "file(s) could not be parsed for formatting"))
    (when-not (zero? incorrect)
      (warn incorrect "file(s) formatted incorrectly"))
    (when (and (zero? incorrect) (zero? error))
      (warn "All source files formatted correctly"))))

(defn- exit [counts]
  (when-not (zero? (:error counts 0))
    (System/exit 2))
  (when-not (zero? (:incorrect counts 0))
    (System/exit 1)))

(defmulti console (fn [event _context _data] event))

(defmethod console :check/initial [_ context _] context)

(defmethod console :check/file [_ context data]
  (print-file-status context data)
  (update context :results merge-counts (:counts data)))

(defmethod console :check/summary [_ summary _]
  (let [counts (:results summary)]
    (print-final-count counts)
    (exit counts)))

(defmethod console :fix/initial [_ context _] context)

(defmethod console :fix/file [_ context data]
  (print-file-status context data)
  (update context :results conj data))

(defmethod console :fix/summary [_ context _]
  (when (some :exception (:results context))
    (System/exit 2))
  context)

(defn- merge-statuses [results {:keys [file diff exception counts]}]
  (let [path (some-> file .getPath)]
    (-> results
        (update :counts merge-counts counts)
        (cond-> (and path (> (:incorrect counts) 1))
          (assoc-in [:incorrect-files path] {}))
        (cond-> (and path diff)
          (assoc-in [:incorrect-files path :diff] diff))
        (cond-> (and path exception)
          (assoc-in [:errored-files path :exception] exception)))))

(defmulti clojure (fn [event _context _data] event))

(defmethod clojure :check/initial [_ context _] context)

(defmethod clojure :check/file [_ context data]
  (update context :results #(merge-statuses %1 %2) data))

(defmethod clojure :check/summary [_ context _] context)

(defmethod clojure :fix/initial [_ context _] context)

(defmethod clojure :fix/file [_ context data]
  (update context :results conj data))

(defmethod clojure :fix/summary [_ summary _] summary)
