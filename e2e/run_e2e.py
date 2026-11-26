#!/usr/bin/env python3
#
# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#      http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""End-to-end tests against a real Elasticsearch node.

Installs the plugin zip into the official Elasticsearch Docker image (same version as `elastic.version` in
gradle.properties), starts a single node and exercises the plugin over REST: the README examples, output
encoding, script isolation, render limits and the sandbox.

    ./gradlew bundlePlugin && python3 e2e/run_e2e.py

Needs Docker and Python 3.9+ (standard library only).
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
PLUGIN = "elasticsearch-script-velocity"

def readme_template(readme):
    """The product-search template from a README's "Store the template" example."""
    text = (ROOT / readme).read_text(encoding="utf-8")
    match = re.search(r'PUT _scripts/product-search\n.*?"source": """\n(.*?)\n"""', text, re.S)
    if match is None:
        raise ValueError(f"no product-search template found in {readme}")
    return match.group(1)


# The README example is the template under test, so the documentation cannot drift from what CI checks.
PRODUCT_SEARCH = readme_template("README.md")

PRODUCTS = [
    {"name": "27\" 4K monitor", "brandId": "dell", "price": 399, "deleted": False, "createdAt": "2026-01-10"},
    {"name": "24\" monitor", "brandId": "lg", "price": 159, "deleted": False, "createdAt": "2026-03-01"},
    {"name": "monitor arm", "brandId": "dell", "price": 49, "deleted": False, "createdAt": "2026-02-01"},
    {"name": "old monitor", "brandId": "dell", "price": 99, "deleted": True, "createdAt": "2025-01-01"},
]


# _render/template and _search/template parse the rendered output as a search request body, so the small test
# templates put the value under test into a term query.
TERM = '{"query": {"term": {"field": %s}}}'


def term_value(rendered):
    return rendered["template_output"]["query"]["term"]["field"]


def gradle_property(key):
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip()
    raise KeyError(key)


class Node:
    """A disposable single-node cluster with the plugin installed."""

    def __init__(self, es_version, plugin_zip, keep):
        self.image = f"docker.elastic.co/elasticsearch/elasticsearch:{es_version}"
        self.plugin_zip = plugin_zip
        self.keep = keep
        self.name = f"velocity-e2e-{int(time.time())}"
        self.url = None

    def start(self):
        install_and_run = (
            "bin/elasticsearch-plugin install --batch file:///tmp/plugin.zip"
            " && exec /bin/tini -- /usr/local/bin/docker-entrypoint.sh eswrapper"
        )
        subprocess.run([
            "docker", "run", "-d", "--name", self.name, "-p", "127.0.0.1::9200",
            "-e", "discovery.type=single-node",
            "-e", "xpack.security.enabled=false",
            "-e", "ES_JAVA_OPTS=-Xms512m -Xmx512m",
            "-v", f"{self.plugin_zip}:/tmp/plugin.zip:ro",
            "--entrypoint", "/bin/bash", self.image, "-c", install_and_run,
        ], check=True, stdout=subprocess.DEVNULL)
        port = subprocess.run(["docker", "port", self.name, "9200/tcp"], check=True, capture_output=True,
                              text=True).stdout.split()[0].rsplit(":", 1)[1]
        self.url = f"http://127.0.0.1:{port}"
        deadline = time.time() + 240
        while time.time() < deadline:
            if not self._running():
                raise RuntimeError("Elasticsearch exited during startup:\n" + self.logs())
            try:
                status, _ = request(self.url, "GET", "/_cluster/health?wait_for_status=yellow&timeout=1s")
                if status == 200:
                    return
            except OSError:
                pass
            time.sleep(2)
        raise RuntimeError("Elasticsearch did not become ready in time:\n" + self.logs())

    def _running(self):
        out = subprocess.run(["docker", "inspect", "-f", "{{.State.Running}}", self.name], capture_output=True, text=True)
        return out.stdout.strip() == "true"

    def logs(self):
        return subprocess.run(["docker", "logs", self.name], capture_output=True, text=True).stdout

    def stop(self):
        if self.keep:
            print(f"keeping container {self.name} at {self.url}", file=sys.stderr)
            return
        subprocess.run(["docker", "rm", "-f", self.name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def request(base_url, method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base_url + path, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")


class VelocityPluginE2E(unittest.TestCase):
    node = None

    def call(self, method, path, body=None, expect=200):
        status, payload = request(self.node.url, method, path, body)
        self.assertEqual(expect, status, f"{method} {path} -> {json.dumps(payload)}")
        return payload

    def store(self, script_id, source, options=None):
        script = {"lang": "velocity", "source": source}
        if options:
            script["options"] = options
        self.call("PUT", f"/_scripts/{script_id}", {"script": script})

    def render(self, script_id, params=None, expect=200):
        return self.call("POST", f"/_render/template/{script_id}", {"params": params or {}}, expect)

    def test_plugin_installs_cleanly(self):
        logs = self.node.logs()
        self.assertIn(f"Installed {PLUGIN}", logs)
        self.assertNotIn("legacy Security Policy", logs)
        plugins = self.call("GET", "/_cat/plugins?format=json")
        self.assertIn(PLUGIN, [p["component"] for p in plugins])

    def test_readme_template_renders_conditionals_and_loops(self):
        self.store("product-search", PRODUCT_SEARCH)
        out = self.render("product-search", {"brandIds": ["dell", "lg"], "minPrice": 50, "sort": "price_asc"})["template_output"]
        self.assertEqual(0, out["from"])
        self.assertEqual(10, out["size"])
        self.assertEqual([{"term": {"deleted": False}}, {"terms": {"brandId": ["dell", "lg"]}}, {"range": {"price": {"gte": 50}}}],
                         out["query"]["bool"]["filter"])
        self.assertNotIn("must", out["query"]["bool"])
        self.assertEqual([{"price": "asc"}], out["sort"])

        bare = self.render("product-search")["template_output"]
        self.assertEqual([{"term": {"deleted": False}}], bare["query"]["bool"]["filter"])
        self.assertNotIn("sort", bare)

    def test_readme_template_searches(self):
        self.store("product-search", PRODUCT_SEARCH)
        self.call("PUT", "/products", {"mappings": {"properties": {
            "name": {"type": "text"}, "brandId": {"type": "keyword"}, "price": {"type": "double"},
            "deleted": {"type": "boolean"}, "createdAt": {"type": "date"}}}})
        for i, doc in enumerate(PRODUCTS):
            self.call("PUT", f"/products/_doc/{i + 1}?refresh=true", doc, expect=201)
        result = self.call("POST", "/products/_search/template", {"id": "product-search", "params": {
            "keywords": "monitor", "brandIds": ["dell"], "minPrice": 50}})
        self.assertEqual(["1"], [h["_id"] for h in result["hits"]["hits"]])
        # the keyword's double quote is JSON-escaped, so it is searched for rather than breaking the request
        quoted = self.call("POST", "/products/_search/template", {"id": "product-search", "params": {
            "keywords": "27\" 4K", "brandIds": ["dell"]}})
        self.assertEqual("1", quoted["hits"]["hits"][0]["_id"])

    def test_readmes_document_the_same_template(self):
        self.assertEqual(PRODUCT_SEARCH, readme_template("README.zh-CN.md"))

    def test_params_are_json_escaped(self):
        self.store("escape", TERM % '"$q"')
        for value in ['27" monitor', "C:\\temp\\new", 'x"}}, "size": 10000, "x": {"y": "', "line\nbreak\ttab"]:
            self.assertEqual(value, term_value(self.render("escape", {"q": value})), value)

    def test_missing_param_renders_literally_unless_quiet(self):
        self.store("loud", TERM % '"$kw"')
        self.store("quiet", TERM % '"$!kw"')
        self.assertEqual("$kw", term_value(self.render("loud")))
        self.assertEqual("", term_value(self.render("quiet")))

    def test_text_plain_content_type_writes_raw(self):
        self.store("raw", '{"query": $query}', options={"content_type": "text/plain"})
        out = self.render("raw", {"query": '{"match_all": {}}'})["template_output"]
        self.assertEqual({"query": {"match_all": {}}}, out)

    def test_macros_are_local_to_their_script(self):
        self.store("macro-a", '#macro(tag)"a"#end' + TERM % '#tag()')
        self.store("macro-b", '#macro(tag)"b"#end' + TERM % '#tag()')
        self.assertEqual("a", term_value(self.render("macro-a")))
        self.assertEqual("b", term_value(self.render("macro-b")))

    def test_parse_and_include_cannot_load_other_scripts(self):
        self.store("escape", TERM % '"$q"')
        for directive in ('#parse("escape")', '#include("escape")'):
            self.store("includer", directive)
            error = self.render("includer", expect=400)["error"]
            self.assertEqual("script_exception", error["type"], directive)

    def test_store_with_context_validates_eagerly(self):
        error = self.call("PUT", "/_scripts/broken/template", {"script": {"lang": "velocity", "source": "#if("}},
                          expect=400)["error"]
        self.assertEqual("script_exception", error["type"])

    def test_loop_limit(self):
        self.store("loop", '{"n": #foreach($i in [1..100001])#end 1}')
        error = self.render("loop", expect=400)["error"]
        self.assertIn("velocity.max_loop_iterations", error["reason"])

    def test_output_limit(self):
        self.store("big", '{"s": "#foreach($i in [1..99999])0123456789A#end"}')
        error = self.render("big", expect=400)["error"]
        self.assertIn("velocity.max_output_size_bytes", error["reason"])

    def test_sandbox_blocks_reflection(self):
        self.store("reflect", TERM % '"$!s.getClass().forName(\'java.lang.Runtime\')"')
        self.assertEqual("", term_value(self.render("reflect", {"s": "x"})))

    def test_inline_search_templates_stay_mustache(self):
        out = self.call("POST", "/_render/template", {"source": TERM % '"#if(true)velocity#end"', "params": {}})
        self.assertEqual("#if(true)velocity#end", term_value(out))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    es_version = gradle_property("elastic.version")
    default_zip = ROOT / "build" / "distributions" / f"{PLUGIN}-{es_version}-{gradle_property('plugin.version')}.zip"
    parser.add_argument("--plugin", type=pathlib.Path, default=default_zip, help="plugin zip (default: %(default)s)")
    parser.add_argument("--es-version", default=es_version, help="Elasticsearch image version (default: %(default)s)")
    parser.add_argument("--keep", action="store_true", help="leave the container running afterwards")
    args = parser.parse_args()
    if not args.plugin.is_file():
        parser.error(f"{args.plugin} not found; run ./gradlew bundlePlugin first")

    node = Node(args.es_version, args.plugin.resolve(), args.keep)
    print(f"starting {node.image} with {args.plugin.name}", file=sys.stderr)
    try:
        node.start()
        VelocityPluginE2E.node = node
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(VelocityPluginE2E)
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        if not result.wasSuccessful():
            print(node.logs()[-20000:], file=sys.stderr)
        return 0 if result.wasSuccessful() else 1
    finally:
        node.stop()


if __name__ == "__main__":
    sys.exit(main())
