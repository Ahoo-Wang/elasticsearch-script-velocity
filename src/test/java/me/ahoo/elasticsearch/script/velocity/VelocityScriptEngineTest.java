package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VelocityScriptEngineTest {
    private final VelocityScriptEngine velocityScriptEngine = (VelocityScriptEngine) new VelocityPlugin().getScriptEngine(null, null);
    private static final String PRODUCT_SEARCH_TEMPLATE = "product_search";

    private static String getSearchTemplate() {
        try (InputStream inputStream = VelocityScriptEngineTest.class.getClassLoader().getResourceAsStream(PRODUCT_SEARCH_TEMPLATE + ".vm")) {
            return new String(inputStream.readAllBytes(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getType() {
        Assert.assertEquals(velocityScriptEngine.getType(), VelocityScriptEngine.NAME);
    }

    @Test
    public void compile() {
        String code = getSearchTemplate();
        org.elasticsearch.script.TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, TemplateScript.CONTEXT, Collections.emptyMap());
        Assert.assertNotNull(factory);
        org.elasticsearch.script.TemplateScript.Factory factory2 = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, TemplateScript.CONTEXT, Collections.emptyMap());
        Assert.assertNotNull(factory2);
    }

    @Test
    public void compileWhenIllegalArgumentException() {
        String code = "#if";

        try {
            velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, new ScriptContext<>("error", WrongFactoryType.class), Collections.emptyMap());
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void compileWhenScriptException() {
        String code = "#if";

        try {
            velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, TemplateScript.CONTEXT, Collections.emptyMap());
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ScriptException);
        }
    }

    @Test
    public void executeScript() {
        String code = getSearchTemplate();
        org.elasticsearch.script.TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, TemplateScript.CONTEXT, Collections.emptyMap());
        var params = new HashMap<String, Object>();
        params.put("from", 0);
        params.put("size", 10);
        var templateScript = factory.newInstance(params);
        var templateOutput = templateScript.execute();
        Assert.assertEquals(templateOutput, "    {\n" +
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

    @Test
    public void executeScriptWhenScriptException() {
        String code = "$params.throwError()";
        org.elasticsearch.script.TemplateScript.Factory factory = velocityScriptEngine.compile(PRODUCT_SEARCH_TEMPLATE, code, TemplateScript.CONTEXT, Collections.emptyMap());
        var params = new HashMap<String, Object>();
        params.put("params", new Params());
        var templateScript = factory.newInstance(params);
        try {
            templateScript.execute();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ScriptException);
        }
    }

    @Test
    public void getSupportedContexts() {
        Assert.assertEquals(velocityScriptEngine.getSupportedContexts(), Collections.singleton(TemplateScript.CONTEXT));
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