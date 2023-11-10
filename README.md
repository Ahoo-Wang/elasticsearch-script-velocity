# Velocity for Elasticsearch

## Install

> optional 1 - use elasticsearch-plugin to install

```shell script
./bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v7.10.2/elasticsearch-script-velocity-7.10.2.zip
```
> optional 2 - download pre-build package from here: [Releases](https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases)

1. create plugin folder `cd your-es-root/plugins/ && mkdir elasticsearch-script-velocity`
2. unzip plugin to folder `your-es-root/plugins/elasticsearch-script-velocity`

## Use

### Store script

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
### Get Stored Script

[get-stored-script-api](https://www.elastic.co/guide/en/elasticsearch/reference/current/get-stored-script-api.html)

```http request
GET _scripts/templateid
```

> Response:

```json
{
  "_id" : "templateid",
  "found" : true,
  "script" : {
    "lang" : "velocity",
    "source" : """{"query":{"match":{"title":"$query_string"}}}""",
    "options" : {
      "content_type" : "application/json; charset=UTF-8"
    }
  }
}
```

### Delete a stored script

[delete-stored-script-api](https://www.elastic.co/guide/en/elasticsearch/reference/current/delete-stored-script-api.html)

```http request
DELETE _scripts/templateid
```

### Search template

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

### Validating a search template

```http request
GET _render/template/templateid
{
  "params": {
    "query_string": "search for these words"
  }
}
```

> Response:

```json
{
  "template_output" : {
    "query" : {
      "match" : {
        "title" : "search for these words"
      }
    }
  }
}
```
