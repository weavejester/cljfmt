(defproject cljfmt "0.7.0"
  :description "A library for formatting Clojure code"
  :url "https://github.com/weavejester/cljfmt"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.reader "1.3.3"]
                 [com.googlecode.java-diff-utils/diffutils "1.3.0"]
                 [rewrite-clj "0.6.1"]
                 [rewrite-cljs "0.4.5"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [io.taylorwood/lein-native-image "0.3.1"]]
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
  :native-image
  {:name "cljfmt"
   :opts ["--verbose"
          "-H:+ReportExceptionStackTraces"
          "-J-Dclojure.spec.skip-macros=true"
          "-J-Dclojure.compiler.direct-linking=true"
          "-H:ReflectionConfigurationFiles=reflection.json"
          "--initialize-at-build-time"
          "-H:Log=registerResource:"
          "--verbose"
          "--no-fallback"
          "--no-server"
          "-J-Xmx3g"]}
  :profiles
  {:uberjar
   {:main cljfmt.main
    :aot :all
    :native-image
    {:jvm-opts ["-Dclojure.compiler.direct-linking=true"
                "-Dclojure.spec.skip-macros=true"]}}
   :provided {:dependencies [[org.clojure/clojurescript "1.10.773"]]}})
