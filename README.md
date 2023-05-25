# cljfmt [![Build Status](https://github.com/weavejester/cljfmt/actions/workflows/test.yml/badge.svg)](https://github.com/weavejester/cljfmt/actions/workflows/test.yml) [![Release Status](https://dl.circleci.com/status-badge/img/gh/weavejester/cljfmt/tree/master.svg?style=shield)](https://dl.circleci.com/status-badge/redirect/gh/weavejester/cljfmt/tree/master)

cljfmt is a tool for detecting and fixing formatting errors in
[Clojure][] code.

Its defaults are based on the [Clojure Style Guide][], but it also has
many customization options to suit a particular project or team.

It is not the goal of the project to provide a one-to-one mapping
between a Clojure syntax tree and formatted text; rather the intent is
to correct formatting errors with minimal changes to the existing
structure of the text.

If you want format completely unstructured Clojure code, the [zprint][]
project may be more suitable.

[clojure]: https://clojure.org/
[clojure style guide]: https://github.com/bbatsov/clojure-style-guide
[zprint]: https://github.com/kkinnear/zprint

## Usage

cljfmt integrates with many existing build tools, or can be used as a
library. As an end user, you have the choice of:

### Standalone

The fastest way to run cljfmt is via a precompiled binary. If you're using
Linux or MacOS, you can run the following command to install the binary into
`/usr/local/bin`:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/weavejester/cljfmt/HEAD/install.sh)"
```

If you're using Windows, there's a [zipped up binary][] you can download and
extract manually.

To use cljfmt to check for formatting errors in your project, run:

```bash
cljfmt check
```

And to fix those errors:

```bash
cljfmt fix
```

[zipped up binary]: https://github.com/weavejester/cljfmt/releases/download/0.10.2/cljfmt-0.10.2-win-amd64.zip

### Clojure Tools

The official Clojure CLI supports installation of thirdparty [tools][].
To install cljfmt as a tool, run:

```bash
clj -Ttools install io.github.weavejester/cljfmt '{:git/tag "0.10.2"}' :as cljfmt
```

To use the tool to check for formatting errors in your project, run:

```bash
clj -Tcljfmt check
```

And to fix those errors:

```bash
clj -Tcljfmt fix
```

[tools]: https://clojure.org/reference/deps_and_cli#tool_install

### Leiningen

[Leiningen][] is a popular Clojure build tool. To use cljfmt with
Leiningen, add the following plugin to your `project.clj` file:

```clojure
:plugins [[dev.weavejester/lein-cljfmt "0.10.2"]]
```

To use the plugin to check code for formatting errors, run:

```bash
lein cljfmt check
```

And to fix those errors:

```bash
lein cljfmt fix
```

[leiningen]: https://github.com/technomancy/leiningen

### Babashka

[Babashka][] is a native Clojure interpreter with a very fast startup.
Running cljfmt via Babashka can be several times faster than running it
as a tool or via Leiningen.

To use cljfmt with Babashka, add the following dependency and task to
your `bb.edn` file:

```edn
{:deps
 {dev.weavejester/cljfmt {:mvn/version "0.10.2"}}
 :tasks
 {cljfmt {:doc "Run cljfmt"
          :requires ([cljfmt.main :as fmt])
          :task (binding [fmt/*command* "bb cljfmt"]
                  (apply fmt/-main *command-line-args*))}}}
```

To use the Babashka task to check code for formatting errors, run:

```bash
bb cljfmt check
```

And to fix those errors:

```bash
bb cljfmt fix
```

[babashka]: https://babashka.org/

### Library

cljfmt can be run as a library that formats a string of Clojure code.
First, add the dependency:

```edn
{:deps {dev.weavejester/cljfmt {:mvn/version "0.10.2"}}}
```

Then use the library:

```clojure
(require '[cljfmt.core :as fmt])

(fmt/reformat-string "(defn sum [x y]\n(+ x y))")
;; => "(defn sum [x y]\n  (+ x y))"
```

To use load the configuration for the current directory:

```clojure
(require '[cljfmt.config :as cfg])

(fmt/reformat-string "(+ x\ny)" (cfg/load-config))
;; => "(+ x\n   y)"
```

### Editor Integration

You can also use cljfmt via your editor. Several Clojure editing
environments have support for cljfmt baked in:

* [Calva](https://github.com/BetterThanTomorrow/calva) (Visual Studio Code)
* [CIDER](https://github.com/clojure-emacs/cider) (Emacs)
* [clojureVSCode](https://github.com/avli/clojureVSCode) (Visual Studio Code)
* [vim-cljfmt](https://github.com/venantius/vim-cljfmt) (Vim)


## Configuration

In most environments, cljfmt will look for the following configuration
files in the current and parent directories:

* `.cljfmt.edn`
* `.cljfmt.clj`
* `cljfmt.edn`
* `cljfmt.clj`

The configuration file should contain a map of options.

In Leiningen, the configuration is found in on the `:cljfmt` key in the
project map:

```clojure
:cljfmt {}
```

### Formatting Options

* `:indentation?` -
  true if cljfmt should correct the indentation of your code.
  Defaults to true.

* `:indents` -
  a map of var symbols to indentation rules, i.e. `{symbol [& rules]}`.
  See [INDENTS.md][] for a complete explanation.

* `:alias-map` -
  a map of namespace alias strings to fully qualified namespace
  names. This option is unnecessary in most cases, because cljfmt
  will parse the `ns` declaration in each file. See [INDENTS.md][].

* `:remove-surrounding-whitespace?` -
  true if cljfmt should remove whitespace surrounding inner
  forms. This will convert <code>(&nbsp;&nbsp;foo&nbsp;&nbsp;)</code> to `(foo)`.
  Defaults to true.

* `:remove-trailing-whitespace?` -
  true if cljfmt should remove trailing whitespace in lines. This will
  convert <code>(foo)&nbsp;&nbsp;&nbsp;\n</code> to `(foo)\n`. Defaults to true.

* `:insert-missing-whitespace?` -
  true if cljfmt should insert whitespace missing from between
  elements. This will convert `(foo(bar))` to `(foo (bar))`.
  Defaults to true.

* `:remove-consecutive-blank-lines?` -
  true if cljfmt should collapse consecutive blank lines. This will
  convert `(foo)\n\n\n(bar)` to `(foo)\n\n(bar)`. Defaults to true.

* `:remove-multiple-non-indenting-spaces?` -
  true if cljfmt should remove multiple non indenting spaces. For
  example, this will convert <code>{:a 1&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:b 2}</code>
  to `{:a 1 :b 2}`. Defaults to false.

* `:split-keypairs-over-multiple-lines?` -
  true if cljfmt should break hashmaps onto multiple lines. This will
  convert `{:a 1 :b 2}` to `{:a 1\n:b 2}`. Defaults to false.

* `:sort-ns-references?` -
  true if cljfmt should alphanumerically sort the requires, imports and
  other references in the `ns` forms at the top of your namespaces.
  Defaults to false.

[indents.md]: docs/INDENTS.md

### Runtime Options

* `:file-pattern` -
  a regular expression to decide which files to scan. Defaults to
  `#”\.clj[csx]?$”`.

* `:parallel?` -
  true if cljfmt should process files in parallel. Defaults to false.

* `:paths` -
  determines which files and directories to recursively search for
  Clojure files. Defaults to checking `src` and `test`, except in
  Leiningen where the `:source-paths` and `:test-paths` keys are used
  instead.

## License

Copyright © 2023 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
