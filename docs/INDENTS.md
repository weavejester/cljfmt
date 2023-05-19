# Indentation

## Overview

Indentation in Clojure can be difficult to get right, as each macro may
have different indentation conventions.

By default, cljfmt indents according to the [Clojure Style Guide][]. Any
function or macro that differs needs specific indent rules. These can be
defined using the `:indents` option. For example:

```edn
{:indents {when [[:block 1]]}}
```

The key can be either a symbol or a regular expression. For example:

```clojure
{:indents {#"^with-" [[:inner 0]]}}
```

Note that edn files do not support regular expressions.

If the symbol is unqualified, it will match all symbols regardless of
namespace. If the symbol is qualified, it will match only the symbol
with that namespace. In most cases, cljfmt can infer the namespace of
any symbol by reading the `ns` declaration.

In the cases where it cannot, you can supply an optional alias map. For
example:

```edn
{:indents   {com.example/foo [[:inner 0]]}
 :alias-map {ex com.example}}
```

This rule would match both `com.example/foo` and `ex/foo`.

By default, new indentation rules are merged with the defaults. If you
want to replace the defaults, use the `:replace` metadata hint. For
example, to replace all indentation rules with a constant 2-space
indentation:

```edn
{:indents ^:replace {#".*" [[:inner 0]]}}
```

[clojure style guide]: https://github.com/bbatsov/clojure-style-guide

## Recipes

If you don't want to read further and just want a quick way of making a
indent rule for a custom macro, here are some quick examples of use.

#### Macro with body

For a macro like:

```clojure
(ns com.example)

(defmacro foo [& body] ...)
```

Use:

```edn
{:indents {com.example/foo [[:block 0]]}}
```

#### Macro with bindings and body

For a macro like:

```clojure
(ns com.example)

(defmacro bar [bindings & body] ...)
```

Use:

```edn
{:indents {com.example/bar [[:block 1]]}}
```

## Concepts

cljfmt format rules use **indexes** and **depth**.

The index is the argument index, starting from zero. In a list, this
means that the second element is 0, the third is 1, etc.

```clojure
(foo bar baz)
;     ^   ^
;     0   1
```

The depth of an element is how deeply it's nested, relative to a chosen
parent. Elements in the parent list have depth 0, its children have
depth 1, its grandchildren depth 2, etc.

```clojure
(foo       ; <- 0
 (bar baz  ; <- 1
  (quz)    ; <- 2
  bang))   ; <- 1
```

For the purpose of indentation, it is the depth of the first element in
the line that matters, and the argument index is always of the form
being indented.

## Rules

There are three types of indentation in cljfmt: **default**, **inner**
and **block**.

### Default

Default indentation is used in absence of any other type. For lists,
it formats differently depending on the number of elements in the first
line.

If there is one or fewer elements on the first line it indents by one
space:

```clojure
(println   ; <= one or fewer elements on first line
 "hello"
 "world")
```

If there is more than one element it indents to the level of the second
element:

```clojure
(println "hello"   ; <= more than one element on first line
         "world")
```

### Inner

Inner indentation always indents by two spaces on every line after the
first, regardless of how many elements there are:

```clojure
(defn greet [name]
  (println "Hello" name))

(defn dismiss
  [name]
  (println "Goodbye" name))
```

The indentation rule for `defn` is:

```edn
{defn [[:inner 0]]}
```

The 0 indicates that only elements at depth 0 should have an inner
indent. We can see that elements at a greater depth use their own
indentation rules:

```clojure
depth │ code               │ indent
──────┼────────────────────┼──────────
      │ (defn greet        │
  0   │   [name]           │ :inner
  0   │   (println "Hello" │ :inner
  1   │            name))  │ :default
```

Note that the depth used to determine the indentation for the line is
is depth of the first element in the line.

We can compare `defn` to another core macro, `reify`. The indentation
rule for `reify` is:

```edn
{reify [[:inner 0] [:inner 1]]}
```

This will use the inner indentation rule for depth 0 and 1. For example:

```clojure
depth │ code                  │ indent
──────┼───────────────────────┼──────────
      │ (reify                │
  0   │   clojure.lang.IDeref │ :inner
  0   │   (deref [_]          │ :inner
  1   │     (str "Hello"      │ :inner
  2   │          "World")))   │ :default
```

We can narrow this rule even further. The indentation rule for `letfn`
is:

```edn
{letfn [[:block 1] [:inner 2 0]]}
```

We'll talk about block indentation in the next section. What's important
in this example is the `[:inner 2 0]`, which has two arguments. The
first is the depth, in this case `2`; the second argument is the index
to restrict this rule to, in this case `0`.

This is best shown with an example:

```clojure
depth │ index │ code                   │ indent
──────┼───────┼────────────────────────┼──────────
      │       │ (letfn [(square [x]    │
  2   │   0   │           (* x x))     │ :inner
  1   │   0   │         (sum [x y]     | :default
  2   │   0   │           (+ x y))]    │ :inner
  0   │   1   │   (let [x 3            │ :block
  2   │   1   │         y 4]           │ :default
  2   │   1   │     (sum (square x)    │ :default
  3   │   1   │          (square y)))) │ :default
```

Note that the index is the argument index of `letfn`; either 0 for the
vector of bindings, or 1 for the inner `let` clause.

If the inner indentation of `letfn` were not restricted to the first
argument, the binding vector, the `sum` function would be incorrectly
formatted.

### Block

Block indentation is a mix of the two. It behaves according to the
default rules up to a particular index. If the argument with that index
is the first element in a line, it switches to use inner indentation.

That may be hard to visualize, so lets illustrate with an example. The
`do` form has the indentation rule:

```edn
{do [[:block 0]]}
```

If the argument 0, the first argument, is at the start of a line, it
uses inner indentation: a constant 2 spaces for each line.

```edn
(do
  (println "Hello")
  (println "World"))
```

However, if argument 0 does not begin a line, the default indentation
is used:

```edn
(do (println "Hello")
    (println "World"))
```

## Defaults

The default indentation for cljfmt are stored in the following resources:

* [cljfmt/indents/clojure.clj](../cljfmt/resources/cljfmt/indents/clojure.clj)
* [cljfmt/indents/compojure.clj](../cljfmt/resources/cljfmt/indents/compojure.clj)
* [cljfmt/indents/fuzzy.clj](../cljfmt/resources/cljfmt/indents/fuzzy.clj)
