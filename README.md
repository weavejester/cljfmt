# cljfmt

[![Build Status](https://travis-ci.org/weavejester/cljfmt.svg?branch=master)](https://travis-ci.org/weavejester/cljfmt)

cljfmt is a tool for formatting Clojure code.

It can turn something like this:

```clojure
( let [x 3
    y 4]
  (+ (* x x
  )(* y y)
  ))
```

Into nicely formatted Clojure code like this:

```clojure
(let [x 3
      y 4]
  (+ (* x x) (* y y)))
```

## Installation

The easiest way to get started with cljfmt is to add the lein-cljfmt
plugin to your [Leiningen][] project map:

```clojure
:plugins [[lein-cljfmt "0.5.4"]]
```

cljfmt has tested on Leiningen 2.5, but may not work on older
versions, particularly versions prior to Leiningen 2.4.

[leiningen]: https://github.com/technomancy/leiningen

## Usage

To check the formatting of your source files, use:

    lein cljfmt check

If the formatting of any source file is incorrect, a diff will be
supplied showing the problem, and what cljfmt thinks it should be.

If you want to check only a specific file, or several specific files,
you can do that, too:

    lein cljfmt check src/foo/core.clj

Once you've identified formatting issues, you can choose to ignore
them, fix them manually, or let cljfmt fix them with:

    lein cljfmt fix

As with the `check` task, you can choose to fix a specific file:

    lein cljfmt fix src/foo/core.clj

## Editor Support

* [vim-cljfmt](https://github.com/venantius/vim-cljfmt)
* [CIDER 0.9+](https://github.com/clojure-emacs/cider)

## Configuration

You can configure lein-cljfmt by adding a `:cljfmt` map to your
project:

```clojure
:cljfmt {}
```

cljfmt has several different formatting rules, and these can be
selectively enabled or disabled:

* `:indentation?` -
  true if cljfmt should correct the indentation of your code.
  Defaults to true.

* `:remove-surrounding-whitespace?` -
  true if cljfmt should remove whitespace surrounding inner
  forms. This will convert `(  foo  )` to `(foo)`.
  Defaults to true.

* `:remove-trailing-whitespace?` -
  true if cljfmt should remove trailing whitespace in lines. This will
  convert `(foo)   \n` to `(foo)\n`. Defaults to true.

* `:insert-missing-whitespace?` -
  true if cljfmt should insert whitespace missing from between
  elements. This will convert `(foo(bar))` to `(foo (bar))`.
  Defaults to true.

* `:remove-consecutive-blank-lines?` -
  true if cljfmt should collapse consecutive blank lines. This will
  convert `(foo)\n\n\n(bar)` to `(foo)\n\n(bar)`. Defaults to true.

* `:align-associative?` -
  true if cljfmt should left align the values of maps and binding
  special forms (let, loop, binding). This will convert 
  `{:foo 1\n:barbaz 2}` to `{:foo    1\n :barbaz 2}`
  and `(let [foo 1\n barbaz 2])` to `(let [foo    1\n      barbaz 2])`.
  Defaults to true.

You can also configure the behavior of cljfmt:

* `:file-pattern` -
  determines which files to scan, `#”\.clj[sx]?$”` by default.

* `:indents` -
  a map of var symbols to indentation rules, i.e. `{symbol [& rules]}`.
  See the next section for a detailed explanation.

As with Leiningen profiles, you can add metadata hints. If you want to
override all existing indents, instead of just supplying new indents
that are merged with the defaults, you can use the `:replace` hint:

```clojure
:cljfmt {:indents ^:replace {#".*" [[:inner 0]]}}
```


### Indentation rules

There are two types of indentation rule, `:inner` and `:block`.

#### Inner rules

An `:inner` rule will apply a constant indentation to all elements at
a fixed depth. So an indent rule:

```clojure
{foo [[:inner 0]]}
```

Will indent all elements inside a `foo` form by two spaces:

```clojure
(foo bar
  baz
  bang)
  ```

While an indent rule like:

```clojure
{foo [[:inner 1]]}
```

Will indent all subforms one level in:

```clojure
(foo bar
 baz
 (bang
   quz
   qoz))
```

Sometimes it's useful to limit indentation to one argument of the
surrounding form. For example, `letfn` uses inner indentation only in
its binding vector:

```clojure
(letfn [(double [x]
          (* x 2))]   ;; special indentation here
  (let [y (double 2)
        z (double 3)]
    (println y
             z)))     ;; but not here
```

To achieve this, an additional index argument may be used:

```clojure
{letfn [[:inner 2 0]]}
```

This will limit the inner indent to depth 2 in argument 0.

#### Block rules

A `:block` rule is a little smarter. This will act like an inner
indent only if there's a line break before a certain number of
arguments, otherwise it acts like a normal list form.

For example, an indent rule:

```clojure
{foo [[:block 0]]}
```

Indents like this, if there are more than 0 arguments on the same line
as the symbol:

```clojure
(foo bar
     baz
     bang)
```

But indents at a constant two spaces otherwise:

```clojure
(foo
  bar
  baz
  bang)
```

## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
