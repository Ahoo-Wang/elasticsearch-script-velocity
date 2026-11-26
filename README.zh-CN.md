# Velocity for Elasticsearch

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/elasticsearch-script-velocity.svg)](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)
[![codecov](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity/graph/badge.svg?token=QK8XZXHBZN)](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity)
![Integration Test Status](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/actions/workflows/integration-test.yml/badge.svg)

[English](README.md)

一个 Elasticsearch 插件，为存储的[搜索模板](https://www.elastic.co/docs/solutions/search/search-templates)增加 [Apache Velocity](https://velocity.apache.org/) 模板语言（`"lang": "velocity"`）。

## 为什么需要它

搜索模板把查询结构留在服务端：客户端只传参数，调整查询无需重新发布客户端。Elasticsearch 自带的模板语言 mustache 刻意保持"无逻辑"：区块只能判断某个值是否存在，无法比较值（`$sort == "newest"`）、无法在多个分支间选择，也无法做任何计算。

Velocity 正好补上这些能力：

| 需求 | Velocity 写法 |
|---|---|
| 按值分支 | `#if($sort == "price_asc") … #elseif($sort == "newest") … #else … #end` |
| 可选子句 | `#if($minPrice) … #end`（缺失、`null`、`false`、空值以及 **0** 都视为假；0 有意义时用 `#if($minPrice \|\| $minPrice == 0)`） |
| 生成列表 | `#foreach($id in $brandIds)"$id"#if($foreach.hasNext),#end#end` |
| 默认值与变量 | `#set($size = 10)` |
| 复用片段 | `#macro(range $field $from) … #end` |

## 版本兼容

发布版本命名为 `<Elasticsearch 版本>-<插件版本>`，例如 `9.5.7-2.0.0`。Elasticsearch 只加载为其精确版本构建的插件，因此请选择前半部分与集群版本完全一致的 [Release](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)。`main` 分支面向 Elasticsearch 9.x，更早的版本线在其他分支维护。

## 安装

在**每个**节点上执行，然后重启该节点：

```shell
bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v9.5.7-2.0.0/elasticsearch-script-velocity-9.5.7-2.0.0.zip
```

把两处 `9.5.7-2.0.0` 替换成与集群匹配的版本。无法联网时，先下载 zip，再用 `file:///path/to/elasticsearch-script-velocity-<version>.zip` 安装。安装结果可用 `GET _cat/plugins` 查看。

## 使用

Velocity 模板必须是**存储脚本**。`_search/template` 和 `_render/template` 总是把内联的 `source` 当作 mustache 处理，所以 Velocity 模板要先存储，再通过 `id` 引用。

示例使用 Kibana Dev Tools 语法，其中 `"""…"""` 表示多行字符串。使用 `curl` 时，`source` 需写成普通 JSON 字符串（转义引号和换行）。

### 1. 存储模板

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

`source` 必须是字符串，因为 Velocity 指令不是 JSON。`PUT _scripts/product-search` 只存储、不编译，语法错误要到搜索时才暴露。改用 `PUT _scripts/product-search/template` 存储会立即编译，模板有错时直接返回 `400`。

### 2. 查看渲染结果

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

渲染结果必须是合法的搜索请求体，否则（例如出现未知的顶层字段）`_render/template` 会返回 `parsing_exception`。

### 3. 搜索

```
GET products/_search/template
{
  "id": "product-search",
  "params": { "keywords": "27\" 4K", "brandIds": ["dell"] }
}
```

`27" 4K` 中的双引号会被自动转义（见下文），因此它会被当作搜索内容，而不会破坏请求。

存储的模板用常规的[存储脚本 API](https://www.elastic.co/docs/api/doc/elasticsearch/group/endpoint-script) 管理：`GET _scripts/<id>`、`DELETE _scripts/<id>`。

## 参数如何渲染

**编码。** `$引用` 写出的每个值都按脚本的 `content_type` 选项编码，规则与 mustache 相同：

| `content_type` 选项 | 编码方式 |
|---|---|
| 未设置、`application/json`（默认） | JSON 字符串转义：转义 `"`、`\` 和控制字符 |
| `application/x-www-form-urlencoded` | 表单 URL 编码 |
| `text/plain` | 不编码，原样写出 |

转义只在值写入输出时发生一次：`#set($q = "*$keywords*")` 中，`$keywords` 会在写出 `$q` 时转义，而不是转义两次。如果模板有意把参数中的原始 JSON 直接插入，需要以 `"options": {"content_type": "text/plain"}` 存储该脚本；这会关闭整个脚本的编码，它写出的所有参数都将被原样信任。

**引号由模板负责。** 编码只转义字符，不会添加引号。字符串参数一律加引号（`"$keywords"`），只有数字所在的位置才不加引号（`$minPrice`）。数字请以 JSON 数字传入，像 `size=abc` 这样的值会让请求因解析错误而失败。

**缺失的参数。** Velocity 会原样写出缺失的引用：`"$keywords"` 会变成字符串 `"$keywords"`，而 mustache 会输出空。使用静默写法 `$!keywords` 可不输出任何内容，或用 `#if($keywords) … #end` 包裹该子句。

**隔离。** 每个脚本单独编译：`#macro` 定义只在定义它的脚本内可见，`#parse` / `#include` 无法加载其他脚本或文件（会以 `script_exception` 失败）。

## 限制

每次渲染受两个节点设置约束，在 `elasticsearch.yml` 中配置：

| 设置 | 默认值 | 限制对象 |
|---|---|---|
| `velocity.max_output_size_bytes` | `1mb` | 单次渲染结果的大小，与 `mustache.max_output_size_bytes` 一样按字符计数 |
| `velocity.max_loop_iterations` | `100000` | 单次渲染中 `#foreach` 的迭代总次数（含嵌套循环） |

超出限制时请求以 `400` 失败，而不是截断查询，例如：

```
script_exception: velocity #foreach iterations exceeded the limit of [100000]; raise [velocity.max_loop_iterations] if this is expected
```

## 安全

- **模板即代码。** Velocity 是图灵完备的，能存储脚本的人（`PUT _scripts`，需要集群 `manage` 权限）就能决定搜索做什么。请像授予集群管理权限一样谨慎授予。
- **参数只是数据。** 搜索用户只能提供 `params`。参数值会按上文规则转义，因此只要模板给每个字符串参数都加了引号，参数就无法引入新的字段或子句。
- **沙箱。** 模板运行在 Velocity 的 [`SecureUberspector`](https://velocity.apache.org/engine/2.4/apidocs/org/apache/velocity/util/introspection/SecureUberspector.html) 之下：禁止对 `java.lang.Class`、`ClassLoader`、`Runtime`、`System`、`Thread`、反射等类调用方法，模板无法触达任意 JVM 代码。渲染还受上述[限制](#限制)约束。
- **Entitlements。** Elasticsearch 9.x 用 entitlements 约束插件。本插件不申请任何权限：没有文件、网络或进程访问。

## 升级到 2.0

2.0.0 改变了模板的渲染方式，升级前请检查你的模板：

- **参数默认进行 JSON 转义。** 直接插入参数中原始 JSON 的模板（例如 `"sort": $sortJson`）现在会得到转义后的字符串，导致解析失败。请在模板里构建该结构，或以 `"options": {"content_type": "text/plain"}` 存储该脚本。
- **`#parse` 和 `#include` 不再可用。** 它们过去解析的是节点碰巧编译过的那些脚本。请改为在模板内使用 `#macro`。
- **`#macro` 定义只在所属脚本内有效。** 过去同名宏的第一个定义会被所有模板共享，即使定义它的脚本更新了也不会变。
- **渲染受到限制：** 输出最多 `1mb`，循环最多 `100000` 次；模板确需更多时可调大[设置](#限制)。
- 插件不再附带 `plugin-security.policy`，安装时不会再提示旧版安全策略的警告。

## 性能

渲染 [`product_search.vm`](src/test/resources/product_search.vm)（参数 `{from: 0, size: 10}`）：单线程约 **每秒 137 万次**（JMH，1 次预热和 1 次测量迭代，各 10 秒；Apple M4 Pro，JDK 21.0.13）。这只是一次短时运行，请把它当作数量级参考，并在自己的硬件上测量：

```shell
./gradlew jmh -PjmhIncludes=VelocityScriptEngineBenchmark
```

## 开发

```shell
./gradlew clean check            # 单元测试、覆盖率、jarHell
./gradlew bundlePlugin           # build/distributions/elasticsearch-script-velocity-<version>.zip
python3 e2e/run_e2e.py           # 需要 Docker：把 zip 安装进官方 Elasticsearch 镜像，并通过 REST 测试
```

CI 会在每个 PR 上运行这三步。Elasticsearch 版本取自 `gradle.properties` 中的 `elastic.version`。
