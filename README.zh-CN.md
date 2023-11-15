# Velocity for Elasticsearch

[![codecov](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity/graph/badge.svg?token=QK8XZXHBZN)](https://codecov.io/gh/Ahoo-Wang/elasticsearch-script-velocity)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/elasticsearch-script-velocity.svg)](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)

*Search Template* 是 *Elasticsearch* 中一项非常实用的功能。通过这一特性，搜索请求的查询结构可以事先定义好，然后在实际请求时传入搜索参数。这样既使得请求体更加简洁，也避免了在客户端拼接查询结构时可能出现的错误。

在需要进行搜索调优时，可以直接在 *Elasticsearch* 服务端修改搜索脚本，而无需重新发布客户端。这显著提高了搜索调优的效率。

然而，*Elasticsearch* [默认支持的脚本语言](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting.html#scripting-available-languages)（`mustache`/`painless`/`expression`）的语法逻辑相对较为有限，不支持逻辑判断，从而对 *Search Template* 的使用带来了一定的限制。

通过引入 *Velocity* 到 *Elasticsearch* 中，可以支持任何逻辑判断，使得 *Search Template* 的使用更加灵活。这为用户提供了更强大、更灵活的搜索定制能力。

## 安装

> 方式 1 - 使用 `elasticsearch-plugin `安装

```shell script
./bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v8.11.0/elasticsearch-script-velocity-8.11.0.zip
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
