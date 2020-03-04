(defproject cljfmt "0.6.7"
  :description "A library for formatting Clojure code"
  :url "https://github.com/weavejester/cljfmt"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/tools.reader "1.3.2"]
                 [com.googlecode.java-diff-utils/diffutils "1.3.0"]
                 [rewrite-clj "0.6.1"]
                 [rewrite-cljs "0.4.4"]]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds
              {"dev" {:source-paths ["src" "test"]
                      :compiler {:main cljfmt.test-runner
                                 :output-to "target/out/tests.js"
                                 :output-dir "target/out"
                                 :target :nodejs
                                 :optimizations :none}}}
              :test-commands
              {"dev" ["node" "target/out/tests.js"]}}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "1.10.520"]]}})
