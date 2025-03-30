(defproject dev.weavejester/cljfmt "0.13.0"
  :description "A library for formatting Clojure code"
  :url "https://github.com/weavejester/cljfmt"
  :scm {:dir ".."}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.4"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/tools.reader "1.5.0"]
                 [com.googlecode.java-diff-utils/diffutils "1.3.0"]
                 [rewrite-clj "1.1.49"]]
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
          "-H:ReflectionConfigurationFiles=reflection.json"
          "--initialize-at-build-time"
          "--diagnostics-mode"
          "--report-unsupported-elements-at-runtime"
          "-H:Log=registerResource:"
          "--no-fallback"
          "-J-Xmx3g"]}
  :profiles
  {:uberjar {:main cljfmt.main
             :aot :all}
   :native-image {:aot :all
                  :main cljfmt.main
                  :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                             "-Dclojure.spec.skip-macros=true"]}
   :static {:native-image
            {:name "cljfmt-static"
             :opts ["--static"
                    "--libc=musl"
                    "-H:CCompilerOption=-Wl,-z,stack-size=2097152"]}}
   :provided {:dependencies [[org.clojure/clojurescript "1.11.132"]]}})
