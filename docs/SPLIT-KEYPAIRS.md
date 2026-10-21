# `:split-keypairs-over-multiple-lines`

Puts one keypair per line. The value selects which maps the rule applies to:

| Value | Applies to |
| --- | --- |
| `:none` (default) | nothing |
| `:all` | any map |
| `:only-multiline` | any map that spans more than one line |

## A map on a single line

Only `:all` expands it.

```clojure
;; Input
{:a 1 :b 2}

;; :all
{:a 1
 :b 2}

;; :only-multiline
{:a 1 :b 2}
```

## A map that spans more than one line

Both values split it, including the pairs that share a line.

```clojure
;; Input
{:a 22222222222222 :b 2
 :c 3              :d 4}

;; :all, :only-multiline
{:a 22222222222222
 :b 2
 :c 3
 :d 4}
```

A value that spans lines keeps its own layout, and the remaining pairs are put
on their own lines:

```clojure
;; Input
{:a [1
     2] :b 2 :c 3 :d 4}

;; :all, :only-multiline
{:a [1
     2]
 :b 2
 :c 3
 :d 4}
```

## A map on multiple lines

A pair that is already split stays split, because the rule only inserts
newlines. 

```clojure
;; Input
{:a 1
 :b 2}

;; :all
{:a 1
 :b 2}

;; :only-multiline
{:a 1
 :b 2}
```

## A map with comments

A comment between a key and its value counts the same way:

```clojure
;; Input
{:a ;; note
 1 :b 2}

;; :all, :only-multiline
{:a ;; note
 1
 :b 2}
```
