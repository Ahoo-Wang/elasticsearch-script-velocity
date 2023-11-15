package me.ahoo.elasticsearch.script.velocity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class VelocityPluginTest {

    @Test
    public void getScriptEngine() {
        org.elasticsearch.script.ScriptEngine scriptEngine;
        try (VelocityPlugin velocityPlugin = new VelocityPlugin()) {
            scriptEngine = velocityPlugin.getScriptEngine(null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Assertions.assertNotNull(scriptEngine);
    }
}