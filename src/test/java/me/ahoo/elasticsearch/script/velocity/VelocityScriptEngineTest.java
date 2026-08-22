package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;
import org.elasticsearch.xcontent.DeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VelocityScriptEngineTest {
    private final VelocityScriptEngine velocityScriptEngine = (VelocityScriptEngine) new VelocityPlugin().getScriptEngine(null, null);
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
        Assertions.assertEquals(templateOutput, "    {\n" +
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
                "    }");
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
        Assertions.assertEquals(velocityScriptEngine.getSupportedContexts(), Collections.singleton(TemplateScript.CONTEXT));
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

        Map<String, Object> json;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                new java.io.ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
            json = parser.map();
        }
        var productNameQuery = findProductNameMatch(json);
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

    public static class Params {

        public void throwError() {
            throw new RuntimeException("error");
        }
    }

    public static class WrongFactoryType {
        public Object newInstance(Map<String, Object> params) {
            return new Object();
        }
    }
}