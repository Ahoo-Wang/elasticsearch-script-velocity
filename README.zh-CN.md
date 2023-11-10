# Velocity for Elasticsearch

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
