package me.ahoo.elasticsearch.script.velocity;

import org.elasticsearch.common.logging.internal.LoggerFactoryImpl;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.logging.internal.spi.LoggerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class VelocityPluginTest {

    static {
        // see VelocityScriptEngineTest: ES classes need the logging factory wired before they initialize
        LoggerFactory.setInstance(new LoggerFactoryImpl());
    }

    @Test
    public void getScriptEngine() {
        org.elasticsearch.script.ScriptEngine scriptEngine;
        try (VelocityPlugin velocityPlugin = new VelocityPlugin()) {
            scriptEngine = velocityPlugin.getScriptEngine(Settings.EMPTY, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Assertions.assertNotNull(scriptEngine);
    }

    @Test
    public void getSettings() throws IOException {
        try (VelocityPlugin velocityPlugin = new VelocityPlugin()) {
            Assertions.assertEquals(List.of(VelocityScriptEngine.MAX_OUTPUT_SIZE, VelocityScriptEngine.MAX_LOOP_ITERATIONS), velocityPlugin.getSettings());
        }
    }
}
