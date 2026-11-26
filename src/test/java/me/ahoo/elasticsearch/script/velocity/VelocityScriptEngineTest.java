package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.common.logging.internal.LoggerFactoryImpl;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.logging.internal.spi.LoggerFactory;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;
import org.elasticsearch.xcontent.DeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VelocityScriptEngineTest {

    static {
        // Since Elasticsearch 9.2, ElasticsearchException's class initialization goes through
        // org.elasticsearch.logging, whose LoggerFactory must be wired by the host. A real ES node
        // does this in Bootstrap; unit tests have to provide it before any ES class initializes.
        LoggerFactory.setInstance(new LoggerFactoryImpl());
    }

    private final VelocityScriptEngine velocityScriptEngine = (VelocityScriptEngine) new VelocityPlugin().getScriptEngine(Settings.EMPTY, null);
    private static final String PRODUCT_SEARCH_TEMPLATE_NAME = "product_search";
    private static final String PRODUCT_SEARCH_TEMPLATE_CONTENT = getSearchTemplate();
    private final TemplateScript.Factory PRODUCT_SEARCH_FACTORY = velocityScriptEngine
            .compile(PRODUCT_SEARCH_TEMPLATE_NAME, PRODUCT_SEARCH_TEMPLATE_CONTENT, TemplateScript.CONTEXT, Collections.emptyMap());

    private static String getSearchTemplate() {
        try (InputStream inputStream = VelocityScriptEngineTest.class.getClassLoader().getResourceAsStream(PRODUCT_SEARCH_TEMPLATE_NAME + ".vm")) {
            return new String(inputStream.readAllBytes(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getType() {
        Assertions.assertEquals(VelocityScriptEngine.NAME, velocityScriptEngine.getType());
    }

    @Test
    public void compile() {
        String code = PRODUCT_SEARCH_TEMPLATE_CONTENT;
        TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap());
        Assertions.assertNotNull(factory);
    }

    @Test
    public void compileWhenIllegalArgumentException() {
        String code = "#if";
        var context = new ScriptContext<>("error", WrongFactoryType.class);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, context, Collections.emptyMap()));
    }

    @Test
    public void compileWhenScriptException() {
        String code = "#if";

        Assertions.assertThrows(ScriptException.class,
                () -> velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap()));
    }

    @Test
    public void executeSearchTemplateScript() {
        var templateOutput = executeSearchTemplate();
        Assertions.assertEquals("    {\n" +
                "      \"from\": 0,\n" +
                "      \"size\": 10,\n" +
                "      \"track_total_hits\": true,\n" +
                "      \"sort\":\n" +
                "      [\n" +
                "        ],\n" +
                "        \"query\": {\n" +
                "          \"bool\": {\n" +
                "            \"filter\": [\n" +
                "              {     \"term\": {       \"deleted\": 0     }   }\n" +
                "              ]\n" +
                "          }\n" +
                "        }\n" +
                "    }", templateOutput);
    }

    public String executeSearchTemplate() {
        var params = new HashMap<String, Object>();
        params.put("from", 0);
        params.put("size", 10);
        var templateScript = PRODUCT_SEARCH_FACTORY.newInstance(params);
        return templateScript.execute();
    }

    @Test
    public void executeScriptWhenScriptException() {
        String code = "$params.throwError()";
        TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap());
        var params = new HashMap<String, Object>();
        params.put("params", new Params());
        var templateScript = factory.newInstance(params);

        Assertions.assertThrows(ScriptException.class, templateScript::execute);
    }

    @Test
    public void getSupportedContexts() {
        Assertions.assertEquals(Collections.singleton(TemplateScript.CONTEXT), velocityScriptEngine.getSupportedContexts());
    }

    @Test
    public void executeWhenTemplateReflection() {
        String code = "#set($rt = $str.getClass().forName(\"java.lang.Runtime\"))$!rt";

        var factory = velocityScriptEngine.compile("reflection_test", code, TemplateScript.CONTEXT, Collections.emptyMap());
        var out = factory.newInstance(Map.of("str", "x")).execute();
        Assertions.assertFalse(out.contains("java.lang.Runtime"), "template must not reach arbitrary classes, got: [" + out + "]");
    }

    @Test
    public void executeWhenNormalMethodCall() {
        String code = "$str.toUpperCase()";

        var factory = velocityScriptEngine.compile("normal_call_test", code, TemplateScript.CONTEXT, Collections.emptyMap());
        var out = factory.newInstance(Map.of("str", "hello")).execute();
        Assertions.assertEquals("HELLO", out);
    }

    @Test
    public void executeSearchTemplateWhenKeywords() throws IOException {
        var params = new HashMap<String, Object>();
        params.put("from", 0);
        params.put("size", 10);
        params.put("keywords", "phone");
        var output = PRODUCT_SEARCH_FACTORY.newInstance(params).execute();

        var productNameQuery = findProductNameMatch(parseJson(output));
        Assertions.assertEquals("phone", productNameQuery.get("query"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findProductNameMatch(Map<String, Object> json) {
        var query = (Map<String, Object>) json.get("query");
        var bool = (Map<String, Object>) query.get("bool");
        var must = (List<Object>) bool.get("must");
        var disMax = (Map<String, Object>) ((Map<String, Object>) must.get(0)).get("dis_max");
        var queries = (List<Object>) disMax.get("queries");
        for (Object clause : queries) {
            var match = (Map<String, Object>) ((Map<String, Object>) clause).get("match");
            if (match != null && match.containsKey("productName")) {
                return (Map<String, Object>) match.get("productName");
            }
        }
        throw new AssertionError("productName match clause not found");
    }

    @Test
    public void executeWhenScriptExceptionEmptyScriptStack() {
        var factory = velocityScriptEngine.compile("error_stack_test", "$params.throwError()", TemplateScript.CONTEXT, Collections.emptyMap());
        var params = new HashMap<String, Object>();
        params.put("params", new Params());
        var ex = Assertions.assertThrows(ScriptException.class, () -> factory.newInstance(params).execute());
        Assertions.assertTrue(ex.getScriptStack().isEmpty(), "script stack should not carry the Java stack trace");
    }

    @Test
    public void executeWhenParamNeedsJsonEscaping() throws IOException {
        String keyword = "27\" monitor \\ C:\\path\nnext\tline";
        var output = render(velocityScriptEngine, "{\"q\": \"$kw\"}", Collections.emptyMap(), Map.of("kw", keyword));
        Assertions.assertEquals(keyword, parseJson(output).get("q"));
    }

    @Test
    public void executeWhenParamTriesToInjectJson() throws IOException {
        String injection = "x\", \"size\": 10000, \"y\": \"";
        var output = render(velocityScriptEngine, "{\"q\": \"$kw\"}", Collections.emptyMap(), Map.of("kw", injection));
        Assertions.assertEquals(Map.of("q", injection), parseJson(output));
    }

    @Test
    public void executeWhenInterpolatedParamIsEscapedOnce() throws IOException {
        var output = render(velocityScriptEngine, "#set($q = \"*$kw*\"){\"q\": \"$q\"}", Collections.emptyMap(), Map.of("kw", "a\"b"));
        Assertions.assertEquals("*a\"b*", parseJson(output).get("q"));
    }

    @Test
    public void executeWhenMacroAndBlockRenderParams() throws IOException {
        String code = "#macro(term $field $value){\"$field\": \"$value\"}#end"
                + "#define($block)\"$kw\"#end"
                + "{\"macro\": #term(\"brand\" $kw), \"macroLiteral\": #term(\"brand\" \"x$kw\"), \"block\": $block}";
        var json = parseJson(render(velocityScriptEngine, code, Collections.emptyMap(), Map.of("kw", "a\"b")));
        Assertions.assertEquals(Map.of("brand", "a\"b"), json.get("macro"));
        Assertions.assertEquals(Map.of("brand", "xa\"b"), json.get("macroLiteral"));
        Assertions.assertEquals("a\"b", json.get("block"));
    }

    @Test
    public void executeWhenTextPlainContentType() {
        var output = render(velocityScriptEngine, "$kw", Map.of("content_type", "text/plain"), Map.of("kw", "a\"b"));
        Assertions.assertEquals("a\"b", output);
    }

    @Test
    public void executeWhenFormUrlEncodedContentType() {
        var output = render(velocityScriptEngine, "q=$kw", Map.of("content_type", "application/x-www-form-urlencoded"), Map.of("kw", "a b&c"));
        Assertions.assertEquals("q=a+b%26c", output);
    }

    @Test
    public void compileWhenUnknownContentType() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> velocityScriptEngine.compile("ct_test", "$kw", TemplateScript.CONTEXT, Map.of("content_type", "text/html")));
    }

    @Test
    public void executeWhenTemplatesDefineSameMacro() {
        var tenantA = velocityScriptEngine.compile("tenant_a", "#macro(filter)A#end#filter()", TemplateScript.CONTEXT, Collections.emptyMap());
        var tenantB = velocityScriptEngine.compile("tenant_b", "#macro(filter)B#end#filter()", TemplateScript.CONTEXT, Collections.emptyMap());
        Assertions.assertEquals("A", tenantA.newInstance(Map.of()).execute());
        Assertions.assertEquals("B", tenantB.newInstance(Map.of()).execute());
    }

    @Test
    public void executeWhenStoredScriptMacroChanges() {
        var v1 = velocityScriptEngine.compile("stored_macro", "#macro(m)v1#end#m()", TemplateScript.CONTEXT, Collections.emptyMap());
        var v2 = velocityScriptEngine.compile("stored_macro", "#macro(m)v2#end#m()", TemplateScript.CONTEXT, Collections.emptyMap());
        Assertions.assertEquals("v2", v2.newInstance(Map.of()).execute());
        Assertions.assertEquals("v1", v1.newInstance(Map.of()).execute());
    }

    @Test
    public void executeWhenTemplateIncludesAnotherScript() {
        velocityScriptEngine.compile("other_script", "secret", TemplateScript.CONTEXT, Collections.emptyMap());
        for (String directive : List.of("#include(\"other_script\")", "#parse(\"other_script\")")) {
            var factory = velocityScriptEngine.compile("includer", directive, TemplateScript.CONTEXT, Collections.emptyMap());
            Assertions.assertThrows(ScriptException.class, () -> factory.newInstance(Map.of()).execute(), directive);
        }
    }

    @Test
    public void executeWhenLoopLimitExceeded() {
        var engine = new VelocityScriptEngine(Settings.builder().put("velocity.max_loop_iterations", 100).build());
        var atLimit = engine.compile("loop_ok", "#foreach($i in [1..100])#set($last = $i)#end$last", TemplateScript.CONTEXT, Collections.emptyMap());
        Assertions.assertEquals("100", atLimit.newInstance(Map.of()).execute());
        // the budget is per render, not per factory or engine
        Assertions.assertEquals("100", atLimit.newInstance(Map.of()).execute());

        var ex = Assertions.assertThrows(ScriptException.class,
                () -> render(engine, "#foreach($i in [1..101])#end", Collections.emptyMap(), Map.of()));
        Assertions.assertTrue(ex.getMessage().contains("velocity.max_loop_iterations"), ex.getMessage());
        // nested loops share one budget: 20 + 20 * 20 > 100
        Assertions.assertThrows(ScriptException.class,
                () -> render(engine, "#foreach($i in [1..20])#foreach($j in [1..20])#end#end", Collections.emptyMap(), Map.of()));
    }

    @Test
    public void executeWhenOutputLimitExceeded() {
        var engine = new VelocityScriptEngine(Settings.builder().put("velocity.max_output_size_bytes", "100b").build());
        Assertions.assertEquals(100, render(engine, "#foreach($i in [1..10])0123456789#end", Collections.emptyMap(), Map.of()).length());

        var ex = Assertions.assertThrows(ScriptException.class,
                () -> render(engine, "#foreach($i in [1..11])0123456789#end", Collections.emptyMap(), Map.of()));
        Assertions.assertTrue(ex.getMessage().contains("velocity.max_output_size_bytes"), ex.getMessage());
    }

    @Test
    public void compileWhenTemplateNestsTooDeeply() {
        String code = "#set($x = " + "(".repeat(50_000) + "1" + ")".repeat(50_000) + ")";
        var ex = Assertions.assertThrows(ScriptException.class,
                () -> velocityScriptEngine.compile("deep", code, TemplateScript.CONTEXT, Collections.emptyMap()));
        // Elasticsearch halts the node when it finds an Error anywhere in a cause chain
        for (Throwable t = ex; t != null; t = t.getCause()) {
            Assertions.assertFalse(t instanceof Error, "cause chain must not carry an Error: " + t);
        }
        // the parser pool is still usable afterwards
        Assertions.assertEquals("ok", render(velocityScriptEngine, "ok", Collections.emptyMap(), Map.of()));
    }

    @Test
    public void executeWhenTemplateSetsNestedParam() {
        Map<String, Object> filter = new HashMap<>(Map.of("field", "orig"));
        List<Object> ids = new java.util.ArrayList<>(List.of("a"));
        var params = new HashMap<String, Object>(Map.of("filter", filter, "ids", ids));
        var output = render(velocityScriptEngine, "#set($filter.field = 'changed')#set($ok = $ids.add('b'))$filter.field $ids",
                Map.of("content_type", "text/plain"), params);
        Assertions.assertEquals("changed [a, b]", output);
        Assertions.assertEquals(Map.of("field", "orig"), filter, "nested caller map must not change");
        Assertions.assertEquals(List.of("a"), ids, "nested caller list must not change");
    }

    @Test
    public void executeTwiceWhenTemplateSetsVariable() {
        var script = velocityScriptEngine.compile("set_state", "#if($x)second#else#set($x = 1)first#end", TemplateScript.CONTEXT, Collections.emptyMap())
                .newInstance(new HashMap<>());
        Assertions.assertEquals("first", script.execute());
        Assertions.assertEquals("first", script.execute());
    }

    @Test
    public void executeWhenMethodOverflowsStack() {
        var ex = Assertions.assertThrows(ScriptException.class,
                () -> render(velocityScriptEngine, "$deep.dive(0)", Collections.emptyMap(), Map.of("deep", new Recursive())));
        // Velocity wraps the overflow from inside the method call; it must still not reach Elasticsearch as an Error
        for (Throwable t = ex; t != null; t = t.getCause()) {
            Assertions.assertFalse(t instanceof Error, "cause chain must not carry an Error: " + t);
        }
    }

    @Test
    public void executeWhenExceptionHasNoMessage() {
        var ex = Assertions.assertThrows(ScriptException.class,
                () -> render(velocityScriptEngine, "$obj", Collections.emptyMap(), Map.of("obj", new SilentFailure())));
        Assertions.assertEquals("Error running render_test", ex.getMessage());
    }

    @Test
    public void executeWhenToStringReturnsNull() {
        Assertions.assertEquals("$obj", render(velocityScriptEngine, "$obj", Collections.emptyMap(), Map.of("obj", new NullString())));
    }

    @Test
    public void executeWhenForeachOverNonIterable() {
        Assertions.assertEquals("", render(velocityScriptEngine, "#foreach($x in $n)x#end", Collections.emptyMap(), Map.of("n", 5)));
    }

    @Test
    public void compileWhenNameIsNull() {
        String code = "$p.throwError()";
        var factory = velocityScriptEngine.compile(null, code, TemplateScript.CONTEXT, Collections.emptyMap());
        var ex = Assertions.assertThrows(ScriptException.class, () -> factory.newInstance(Map.of("p", new Params())).execute());
        // like Elasticsearch's inline scripts, an anonymous script is identified by its source
        Assertions.assertEquals(code, ex.getScript());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/json; charset=UTF-8", "application/json;charset=utf-8"})
    public void executeWhenJsonContentType(String contentType) {
        Assertions.assertEquals("a\\\"b", render(velocityScriptEngine, "$kw", Map.of("content_type", contentType), Map.of("kw", "a\"b")));
    }

    @Test
    public void executeWhenOptionsHaveNoContentType() {
        var factory = velocityScriptEngine.compile("no_options", "$kw", TemplateScript.CONTEXT, null);
        Assertions.assertEquals("a\\\"b", factory.newInstance(Map.of("kw", "a\"b")).execute());
        Assertions.assertEquals("a\\\"b", render(velocityScriptEngine, "$kw", Map.of("other", "x"), Map.of("kw", "a\"b")));
    }

    private static String render(VelocityScriptEngine engine, String code, Map<String, String> options, Map<String, Object> params) {
        return engine.compile("render_test", code, TemplateScript.CONTEXT, options).newInstance(params).execute();
    }

    private static Map<String, Object> parseJson(String json) throws IOException {
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
            return parser.map();
        }
    }

    public static class Params {

        public void throwError() {
            throw new RuntimeException("error");
        }
    }

    public static class Recursive {
        public int dive(int depth) {
            return dive(depth + 1);
        }
    }

    public static class SilentFailure {
        @Override
        public String toString() {
            throw new IllegalStateException();
        }
    }

    public static class NullString {
        @Override
        public String toString() {
            return null;
        }
    }

    public static class WrongFactoryType {
        public Object newInstance(Map<String, Object> params) {
            return new Object();
        }
    }
}