(ns cljfmt.diff)

(defn throw-not-implemented []
  (throw (ex-info "Not implemented in the babashka version of cljfmt yet" {})))

(defn unified-diff
  ([_filename _original _revised]
   (throw-not-implemented))
  ([_filename _original _revised _context]
   (throw-not-implemented)))

(defn colorize-diff [_diff-text]
  (throw-not-implemented))
