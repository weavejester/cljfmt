(ns cljfmt.tool
  (:require [cljfmt.lib :as lib]
            [clojure.stacktrace :as st]))

(def ^:dynamic *no-output* false)
(def ^:dynamic *verbose* false)

(defn- warn [& args]
  (when-not *no-output*
    (binding [*out* *err*]
      (apply println args))))

(defn- trace [& args]
  (when *verbose* (apply warn args)))

(defn- print-stack-trace [ex]
  (when-not *no-output*
    (binding [*out* *err*]
      (st/print-stack-trace ex))))

(defn- print-file-status [{:keys [path] :as status}]
  (when-let [ex (:exception status)]
    (warn "Failed to format file:" path)
    (print-stack-trace ex))
  (when (:reformatted status)
    (warn "Reformatted" path))
  (when-let [diff (:diff status)]
    (warn path "has incorrect formatting")
    (println diff)))

(defn- exit [counts]
  (when-not (zero? (:error counts 0))
    (System/exit 2))
  (when-not (zero? (:incorrect counts 0))
    (System/exit 1)))

(defn- print-final-count [counts]
  (let [error     (:error counts 0)
        incorrect (:incorrect counts 0)]
    (when-not (zero? error)
      (warn error "file(s) could not be parsed for formatting"))
    (when-not (zero? incorrect)
      (warn incorrect "file(s) formatted incorrectly"))
    (when (and (zero? incorrect) (zero? error))
      (warn "All source files formatted correctly"))))

(defn check-no-config
  [options]
  (let [options* (assoc options :cljfmt/report print-file-status
                        :cljfmt/trace trace)
        counts   (lib/check-no-config options*)]
    (print-final-count counts)
    (exit counts)))

(defn check
  [options]
  (let [options* (assoc options :cljfmt/report print-file-status
                        :cljfmt/trace trace)
        counts   (lib/check options*)]
    (print-final-count counts)
    (exit counts)))

(defn fix-no-config
  [options]
  (let [options* (assoc options :cljfmt/report print-file-status
                        :cljfmt/trace trace)]
    (lib/fix-no-config options*)))

(defn fix
  "Fixes the formatting for all files specified by the :paths option."
  [options]
  (let [options* (assoc options :cljfmt/report print-file-status
                        :cljfmt/trace trace)]
    (lib/fix options*)))
