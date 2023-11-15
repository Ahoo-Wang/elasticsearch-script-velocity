package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
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
        Assertions.assertEquals(velocityScriptEngine.getType(), VelocityScriptEngine.NAME);
    }

    @Test
    public void compile() {
        String code = PRODUCT_SEARCH_TEMPLATE_CONTENT;
        org.elasticsearch.script.TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap());
        Assertions.assertNotNull(factory);
    }

    @Test
    public void compileWhenIllegalArgumentException() {
        String code = "#if";

        try {
            velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, new ScriptContext<>("error", WrongFactoryType.class), Collections.emptyMap());
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void compileWhenScriptException() {
        String code = "#if";

        try {
            velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap());
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof ScriptException);
        }
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
        org.elasticsearch.script.TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE_NAME, code, TemplateScript.CONTEXT, Collections.emptyMap());
        var params = new HashMap<String, Object>();
        params.put("params", new Params());
        var templateScript = factory.newInstance(params);
        try {
            templateScript.execute();
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof ScriptException);
        }
    }

    @Test
    public void getSupportedContexts() {
        Assertions.assertEquals(velocityScriptEngine.getSupportedContexts(), Collections.singleton(TemplateScript.CONTEXT));
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