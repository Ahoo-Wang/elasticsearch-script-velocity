# Velocity for Elasticsearch

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/elasticsearch-script-velocity.svg)](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)
[![codecov](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity/graph/badge.svg?token=QK8XZXHBZN)](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity)
![Integration Test Status](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/actions/workflows/integration-test.yml/badge.svg)

[中文文档](README.zh-CN.md)

An Elasticsearch plugin that adds [Apache Velocity](https://velocity.apache.org/) as a language for stored [search templates](https://www.elastic.co/docs/solutions/search/search-templates) (`"lang": "velocity"`).

## Why

A search template keeps the shape of a query on the server: clients send only parameters, and you can tune the query without redeploying them. Mustache, the template language Elasticsearch ships with, is deliberately logic-less: a section can test whether a value is present, but it cannot compare values (`$sort == "newest"`), choose between several branches, or compute anything.

Velocity provides exactly those pieces:

| Need | Velocity |
|---|---|
| Branch on a value | `#if($sort == "price_asc") … #elseif($sort == "newest") … #else … #end` |
| Optional clause | `#if($minPrice) … #end` (missing, `null`, `false`, empty and **zero** are false; use `#if($minPrice \|\| $minPrice == 0)` when `0` is meaningful) |
| Build a list | `#foreach($id in $brandIds)"$id"#if($foreach.hasNext),#end#end` |
| Defaults and variables | `#set($size = 10)` |
| Reuse a fragment | `#macro(range $field $from) … #end` |

## Compatibility

Releases are named `<Elasticsearch version>-<plugin version>`, for example `9.5.7-2.0.0`. Elasticsearch only loads a plugin built for its exact version, so pick the [release](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases) whose first part equals your cluster's version. `main` targets Elasticsearch 9.x; older lines are maintained on branches.

## Install

On **every** node, then restart the node:

```shell
bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v9.5.7-2.0.0/elasticsearch-script-velocity-9.5.7-2.0.0.zip
```

Replace both occurrences of `9.5.7-2.0.0` with the release that matches your cluster. Without network access, download the zip and install it with `file:///path/to/elasticsearch-script-velocity-<version>.zip`. Check the result with `GET _cat/plugins`.

## Use

Velocity templates are **stored scripts**. `_search/template` and `_render/template` always treat an inline `source` as mustache, so a Velocity template has to be stored first and then referenced by `id`.

The examples use Kibana Dev Tools syntax, where `"""…"""` holds a multi-line string. With `curl`, send `source` as an ordinary JSON string instead (escape quotes and newlines).

### 1. Store the template

```
PUT _scripts/product-search
{
  "script": {
    "lang": "velocity",
    "source": """
{
  "from": #if($from)$from#{else}0#end,
  "size": #if($size)$size#{else}10#end,
  "query": {
    "bool": {
      "filter": [
        { "term": { "deleted": false } }
        #if($brandIds)
        , { "terms": { "brandId": [#foreach($id in $brandIds)"$id"#if($foreach.hasNext), #end#end] } }
        #end
        #if($minPrice)
        , { "range": { "price": { "gte": $minPrice } } }
        #end
      ]
      #if($keywords)
      , "must": { "match": { "name": "$keywords" } }
      #end
    }
  }
  #if($sort == "price_asc")
  , "sort": [ { "price": "asc" } ]
  #elseif($sort == "newest")
  , "sort": [ { "createdAt": "desc" } ]
  #end
}
"""
  }
}
```

`source` must be a string: Velocity directives are not JSON. `PUT _scripts/product-search` stores the template without compiling it, so syntax errors only show up at search time. Store it as `PUT _scripts/product-search/template` instead to compile it immediately and get a `400` for a broken template.

### 2. Check what it renders

```
POST _render/template/product-search
{
  "params": { "keywords": "monitor", "brandIds": ["dell", "lg"], "minPrice": 50, "sort": "price_asc" }
}
```

```json
{
  "template_output": {
    "from": 0,
    "size": 10,
    "query": {
      "bool": {
        "filter": [
          { "term": { "deleted": false } },
          { "terms": { "brandId": ["dell", "lg"] } },
          { "range": { "price": { "gte": 50 } } }
        ],
        "must": { "match": { "name": "monitor" } }
      }
    },
    "sort": [ { "price": "asc" } ]
  }
}
```

The rendered output has to be a valid search request body; `_render/template` rejects anything else (for example an unknown top-level key) with a `parsing_exception`.

### 3. Search

```
GET products/_search/template
{
  "id": "product-search",
  "params": { "keywords": "27\" 4K", "brandIds": ["dell"] }
}
```

The double quote inside `27" 4K` is escaped automatically (see below), so it is searched for instead of breaking the request.

Stored templates are managed with the usual [stored script APIs](https://www.elastic.co/docs/api/doc/elasticsearch/group/endpoint-script): `GET _scripts/<id>`, `DELETE _scripts/<id>`.

## How parameters are rendered

**Encoding.** Every value a `$reference` writes is encoded according to the script's `content_type` option, the same way mustache does it:

| `content_type` option | Encoding |
|---|---|
| not set, `application/json` (default) | JSON string escaping (`"`, `\`, control characters), plus the JSON punctuation `{ } [ ] ,` and `/` as `\u007b`-style escapes |
| `application/x-www-form-urlencoded` | form URL encoding |
| `text/plain` | none: values are written as they are |

**Structure comes only from the template.** Inside a JSON string the `\uXXXX` escapes decode back to the original characters, so a quoted value means exactly what it did: `_render/template` shows them escaped, the search sees `{`. Outside a string they are invalid JSON, so a parameter can never open or close an object or array, add a member, or start a comment, whether or not the template quotes it. Without this, a string sent where the template expects a number could drop every clause after it: Elasticsearch accepts `/* */` comments and ignores anything after the request's root object.

Escaping happens once, when the value reaches the output. In `#set($q = "*$keywords*")`, `$keywords` is escaped when `$q` is written, not twice. A template that deliberately inserts raw JSON taken from a parameter has to be stored with `"options": {"content_type": "text/plain"}`, which turns encoding off for that whole script — every parameter it writes is then trusted.

**Quotes are yours.** Encoding escapes characters, it does not add quotes. Quote every string (`"$keywords"`) and leave a parameter unquoted only where a number belongs (`$minPrice`); a value that is not a number there, such as `size=abc`, makes the request fail with a parse error. Build arrays with `#foreach` as in the example: writing a list parameter directly (`$ids`) produces an escaped string, not a JSON array.

**Missing parameters.** Velocity writes a missing reference literally: `"$keywords"` becomes the string `"$keywords"`. Mustache would write nothing. Use the quiet form `$!keywords` to write nothing, or wrap the clause in `#if($keywords) … #end`.

**Isolation.** Each script is compiled on its own: `#macro` definitions are visible only inside the script that defines them, and `#parse` / `#include` cannot load other scripts or files (they fail with `script_exception`).

## Limits

Each render is bounded by two node settings, set in `elasticsearch.yml`:

| Setting | Default | What it limits |
|---|---|---|
| `velocity.max_output_size_bytes` | `1mb` | size of one rendered template, counted in characters like `mustache.max_output_size_bytes` |
| `velocity.max_loop_iterations` | `100000` | `#foreach` iterations in one render, nested loops included |

Going over a limit fails the request with a `400` instead of truncating the query, for example:

```
script_exception: velocity #foreach iterations exceeded the limit of [100000]; raise [velocity.max_loop_iterations] if this is expected
```

## Security

- **Templates are code.** Velocity is Turing-complete, so whoever can store scripts (`PUT _scripts`, the cluster `manage` privilege) controls what a search does. Grant it like cluster administration.
- **Parameters are data.** Search users only supply `params`. Their values are escaped as described above, so a parameter cannot change the structure of the query (add keys or clauses, close objects early, comment parts out), quoted or not.
- **Sandbox.** Templates run with Velocity's [`SecureUberspector`](https://velocity.apache.org/engine/2.4/apidocs/org/apache/velocity/util/introspection/SecureUberspector.html): method calls on `java.lang.Class`, `ClassLoader`, `Runtime`, `System`, `Thread`, reflection and similar classes are blocked, so a template cannot reach arbitrary JVM code. Rendering is bounded by the [limits](#limits) above.
- **Entitlements.** Elasticsearch 9.x confines plugins with entitlements. This plugin requests none: no file, network or process access.

## Upgrading to 2.0

2.0.0 changes how templates render. Check your templates before upgrading:

- **Parameters are JSON-escaped by default.** A template that inserted raw JSON from a parameter (for example `"sort": $sortJson`) now gets an escaped string and fails to parse. Build the structure in the template, or store that script with `"options": {"content_type": "text/plain"}`.
- **`#parse` and `#include` no longer work.** They used to resolve against whatever scripts the node happened to have compiled. Use `#macro` inside the template instead.
- **`#macro` definitions are local to their script.** Previously the first definition of a name was shared by every template and survived updates to the script that defined it.
- **Renders are limited** to `1mb` of output and `100000` loop iterations; raise the [settings](#limits) if a template needs more.
- The plugin no longer ships `plugin-security.policy`, so the installer no longer warns about a legacy security policy.
- **Since 2.0.1**, JSON encoding also escapes `{ } [ ] ,` and `/` (2.0.0 let a parameter written unquoted restructure the query). Quoted values are unaffected; a list parameter written directly (`$ids`) no longer renders as a JSON array, so build it with `#foreach`.

## Performance

Rendering [`product_search.vm`](src/test/resources/product_search.vm) with `{from: 0, size: 10}`: about **1.37 million renders per second** on one thread (JMH, 1 warmup and 1 measurement iteration of 10 s; Apple M4 Pro, JDK 21.0.13). It is a single short run: treat it as an order of magnitude and measure on your own hardware:

```shell
./gradlew jmh -PjmhIncludes=VelocityScriptEngineBenchmark
```

## Development

```shell
./gradlew clean check            # unit tests, coverage, jarHell
./gradlew bundlePlugin           # build/distributions/elasticsearch-script-velocity-<version>.zip
python3 e2e/run_e2e.py           # needs Docker: installs the zip into the official Elasticsearch image and tests it over REST
```

CI runs all three on every pull request. The Elasticsearch version comes from `elastic.version` in `gradle.properties`.
