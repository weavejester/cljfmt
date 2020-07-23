;;;   Copyright (c) Rich Hickey. All rights reserved.
;;;   The use and distribution terms for this software are covered by the
;;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;;   which can be found in the file epl-v10.html at the root of this distribution.
;;;   By using this software in any fashion, you are agreeing to be bound by
;;;   the terms of this license.
;;;   You must not remove this notice, or any other, from this software.

;;; stacktrace.clj: print Clojure-centric stack traces

;; by Stuart Sierra
;; January 6, 2009

(ns cljfmt.impl.stacktrace)

;;; Bring over the functions from stacktrace because they need to be
;;; type hinted for the native-image to work

(defn print-trace-element
  "Prints a Clojure-oriented view of one element in a stack trace."
  {:added "1.1"}
  [^java.lang.StackTraceElement e]
  (let [class (.getClassName e)
        method (.getMethodName e)
        match (re-matches #"^([A-Za-z0-9_.-]+)\$(\w+)__\d+$" (str class))]
    (if (and match (= "invoke" method))
      (apply printf "%s/%s" (rest match))
      (printf "%s.%s" class method)))
  (printf " (%s:%d)" (or (.getFileName e) "") (.getLineNumber e)))

(defn print-throwable
  "Prints the class and message of a Throwable. Prints the ex-data map
  if present."
  {:added "1.1"}
  [^Throwable tr]
  (printf "%s: %s" (.getName (class tr)) (.getMessage tr))
  (when-let [info (ex-data tr)]
    (newline)
    (pr info)))

(defn print-stack-trace
  "Prints a Clojure-oriented stack trace of tr, a Throwable.
  Prints a maximum of n stack frames (default: unlimited).
  Does not print chained exceptions (causes)."
  {:added "1.1"}
  ([tr] (print-stack-trace tr nil))
  ([^Throwable tr n]
   (let [st (.getStackTrace tr)]
     (print-throwable tr)
     (newline)
     (print " at ")
     (if-let [e (first st)]
       (print-trace-element e)
       (print "[empty stack trace]"))
     (newline)
     (doseq [e (if (nil? n)
                 (rest st)
                 (take (dec n) (rest st)))]
       (print "    ")
       (print-trace-element e)
       (newline)))))
