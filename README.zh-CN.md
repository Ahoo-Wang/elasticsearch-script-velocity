# Velocity for Elasticsearch

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/elasticsearch-script-velocity.svg)](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)
[![codecov](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity/graph/badge.svg?token=QK8XZXHBZN)](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity)
![Integration Test Status](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/actions/workflows/integration-test.yml/badge.svg)

*Search Template* 是 *Elasticsearch* 中一项非常实用的功能。通过这一特性，搜索请求的查询结构可以事先定义好，然后在实际请求时传入搜索参数。这样既使得请求体更加简洁，也避免了在客户端拼接查询结构时可能出现的错误。

在需要进行搜索调优时，可以直接在 *Elasticsearch* 服务端修改搜索脚本，而无需重新发布客户端。这显著提高了搜索调优的效率。

然而，*Elasticsearch* [默认支持的脚本语言](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting.html#scripting-available-languages)（`mustache`/`painless`/`expression`）的语法逻辑相对较为有限，不支持逻辑判断，从而对 *Search Template* 的使用带来了一定的限制。

通过引入 *Velocity* 到 *Elasticsearch* 中，可以支持任何逻辑判断，使得 *Search Template* 的使用更加灵活。这为用户提供了更强大、更灵活的搜索定制能力。

## 版本约定

> version = `elasticsearchVersion`-`pluginVersion`

## 安装

> 方式 1 - 使用 `elasticsearch-plugin `安装.
> 
> Tip: 将链接中的 [version] 替换为你的 Elasticsearch 版本号

```shell script
./bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v[version]/elasticsearch-script-velocity-[version].zip
```

> 方式 2 - 从 [Releases](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases) 下载构建包

1. 创建插件目录 `cd your-es-root/plugins/ && mkdir elasticsearch-script-velocity`
2. 解压到该目录 `your-es-root/plugins/elasticsearch-script-velocity`

## 使用

### 存储脚本

[create-stored-script-api](https://www.elastic.co/guide/en/elasticsearch/reference/current/create-stored-script-api.html)

```http request
POST _scripts/templateid
{
    "script": {
        "lang": "velocity",
        "source": {
            "query": {
                "match": {
                    "title": "$query_string"
                }
            }
        }
    }
}
```

### 获取已存储的脚本

[get-stored-script-api](https://www.elastic.co/guide/en/elasticsearch/reference/current/get-stored-script-api.html)

```http request
GET _scripts/templateid
```

> 请求响应结果：

```json
{
  "_id": "templateid",
  "found": true,
  "script": {
    "lang": "velocity",
    "source": """{"query":{"match":{"title":"$query_string"}}}""",
    "options": {
      "content_type": "application/json;charset=utf-8"
    }
  }
}
```

### 删除已存储的脚本

[delete-stored-script-api](https://www.elastic.co/guide/en/elasticsearch/reference/current/delete-stored-script-api.html)

```http request
DELETE _scripts/templateid
```

### 模板搜索

[search-template](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-template.html)

```http request
GET _search/template
{
    "id": "templateid",
    "params": {
        "query_string": "search for these words"
    }
}
```

### 验证搜索模板

```http request
GET _render/template/templateid
{
  "params": {
    "query_string": "search for these words"
  }
}
```

> 请求响应结果：

```json
{
  "template_output": {
    "query": {
      "match": {
        "title": "search for these words"
      }
    }
  }
}
```

## 基准测试

```
Benchmark                                             Mode  Cnt        Score   Error  Units
VelocityScriptEngineBenchmark.executeSearchTemplate  thrpt       1020003.447          ops/s
```
## 安全

模板执行启用了 Velocity 的 [`SecureUberspector`](https://velocity.apache.org/engine/2.4/apidocs/org/apache/velocity/util/introspection/SecureUberspector.html)：禁止模板对 `java.lang.Class`、`java.lang.ClassLoader`、`java.lang.Runtime`、`java.lang.System` 等敏感类进行方法调用，模板无法逃逸到任意 JVM 代码。

### 输出编码

`$引用` 写出的值会按脚本的 `content_type` 选项编码，规则与 mustache 引擎一致：

| `content_type` | 编码方式 |
|---|---|
| 未设置、`application/json`（默认） | JSON 字符串转义，例如参数 `27" monitor` 无法跳出 `"$keywords"` |
| `application/x-www-form-urlencoded` | 表单 URL 编码 |
| `text/plain` | 不编码（原样输出） |

每个值只在写入输出时编码一次：`#set($q = "*$keywords*")` 中的值会在写出 `$q` 时编码。如果模板有意把参数中的原始 JSON 直接插入，需要以 `"options": {"content_type": "text/plain"}` 存储该脚本，这会关闭整个脚本的编码。

### 隔离与限制

各脚本相互隔离：`#macro` 定义只在定义它的模板内有效，`#parse` / `#include` 无法加载其他脚本或文件。

每次渲染受两个节点设置（`elasticsearch.yml`）约束，超出任一限制时请求会以 `script_exception` 失败，而不是截断输出：

| 设置 | 默认值 | 限制对象 |
|---|---|---|
| `velocity.max_output_size_bytes` | `1mb` | 单次渲染输出的大小（按字符计数，与 `mustache.max_output_size_bytes` 一致） |
| `velocity.max_loop_iterations` | `100000` | 单次渲染中 `#foreach` 的迭代总次数（含嵌套循环） |

Velocity 模板仍是图灵完备的，请仅将存储脚本写入权限（`PUT _scripts`）授予可信方——与集群管理相同的信任级别。
