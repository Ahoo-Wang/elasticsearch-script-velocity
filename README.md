# Velocity for Elasticsearch

*Search Template* is a highly valuable feature in Elasticsearch. It allows the pre-definition of the query structure for search requests, with the ability to pass search parameters during the actual request. This not only makes the request body more concise but also helps avoid errors that may occur when concatenating query structures on the client side.

When search optimization is necessary, modifications to search scripts can be made directly on the Elasticsearch server without the need to redeploy the client. This significantly enhances the efficiency of search optimization.

However, the [default scripting languages](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting.html#scripting-available-languages) (`mustache`/`painless`/`expression`) supported by *Elasticsearch* have relatively limited syntax logic, lacking support for any conditional statements. This imposes significant constraints on the use of *Search Template*.

By introducing *Velocity* into *Elasticsearch*, support for any conditional statements is enabled, making the usage of *Search Template* more flexible. This provides users with greater customization capabilities for powerful and flexible searches.

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
