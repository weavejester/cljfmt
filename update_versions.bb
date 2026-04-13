#!/usr/bin/env bb

(require '[clojure.string :as str])
(import 'java.time.format.DateTimeFormatter
        'java.time.LocalDateTime)

(def project-files
  ["cljfmt/project.clj"
   "lein-cljfmt/project.clj"])

(def version (first *command-line-args*))

(when-not version
  (println "Error: requires version as first argument.")
  (System/exit 1))

(doseq [f project-files]
  (-> (slurp f)
      (str/replace #"\(defproject (.*?) \"(.*?)\""
                   (format "(defproject $1 \"%s\"" version))
      (str/replace #"\[dev\.weavejester/cljfmt \"(.*?)\"\]"
                   (format "[dev.weavejester/cljfmt \"%s\"]" version))
      (as-> s (spit f s)))
  (println (format "Updated '%s'." f)))

(-> (slurp "README.md")
    (str/replace
     #"releases/download/(.*?)/cljfmt-(.*?)-win-amd64.zip"
     (format "releases/download/%s/cljfmt-%s-win-amd64.zip" version version))
    (str/replace
     #"cljfmt '\{:git/tag \"(.*?)\"\}\'"
     (format "cljfmt '{:git/tag \"%s\"}'" version))
    (str/replace
     #"\[dev\.weavejester/lein-cljfmt \"(.*?)\"\]"
     (format "[dev.weavejester/lein-cljfmt \"%s\"]" version))
    (str/replace
     #"dev\.weavejester/cljfmt \{:mvn/version \"(.*?)\"\}"
     (format "dev.weavejester/cljfmt {:mvn/version \"%s\"}" version))
    (as-> s (spit "README.md" s)))
(println "Updated 'README.md'.")

(-> (slurp "install.sh")
    (str/replace #"VERSION=(.*?)\n" (format "VERSION=%s\n" version))
    (as-> s (spit "install.sh" s)))
(println "Updated 'install.sh'.")

(-> (slurp "cljfmt/src/cljfmt/main.clj")
    (str/replace
     #"\(def \^:const VERSION \"(.*?)\"\)"
     (format "(def ^:const VERSION \"%s\")" version))
    (as-> s (spit "cljfmt/src/cljfmt/main.clj" s)))
(println "Updated 'cljfmt/src/cljfmt/main.clj'.")

(def now (LocalDateTime/now))
(def formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(->> (slurp "CHANGELOG.md")
     (str "## " version " (" (.format now formatter) ")\n\n"
          "* TBD\n\n")
     (spit "CHANGELOG.md"))

(println "Updated 'CHANGELOG.md'.")
(newline)
(println "Remember to update the CHANGELOG!")
