# Velocity for Elasticsearch

## Install

### Download
 
#### optional 1 - use elasticsearch-plugin to install

```shell script
./bin/elasticsearch-plugin install https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases/download/v1.0.0/elasticsearch-script-velocity-1.0.zip
```
#### optional 2 - download pre-build package from here: https://github.com/Ahoo-Wang/elasticsearch-script-velocity/releases

1. create plugin folder `cd your-es-root/plugins/ && mkdir elasticsearch-script-velocity`
2. unzip plugin to folder `your-es-root/plugins/elasticsearch-script-velocity`

## Demo

### Store a search template
You can store a search template using the stored scripts API.

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
The template can be retrieved by calling

```http request
GET _scripts/templateid

```
The API returns the following result:

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
This template can be deleted by calling

```http request
DELETE _scripts/templateid

```

### Using a stored search template

To use a stored template at search time send the following request:

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

A template can be rendered in a response with given parameters by using the following request:

```http request
GET _render/template/templateid
{
  "params": {
    "query_string": "search for these words"
  }
}
```
The API returns the rendered template:

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
