package me.ahoo.elasticsearch.script.velocity;

import org.apache.velocity.runtime.Renderable;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.util.introspection.Info;
import org.elasticsearch.common.logging.internal.LoggerFactoryImpl;
import org.elasticsearch.logging.internal.spi.LoggerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Unit tests for the pieces the engine wires into Velocity, exercised directly rather than through a template.
 */
public class RenderSupportTest {

    static {
        // see VelocityScriptEngineTest: ES classes need the logging factory wired before they initialize
        LoggerFactory.setInstance(new LoggerFactoryImpl());
    }

    @Test
    public void noneEncoderLeavesValueRaw() {
        Assertions.assertEquals("a\"b", ReferenceEncoder.NONE.encode("a\"b"));
    }

    @Test
    public void handlerPassesValuesThroughOutsideRender() {
        Assertions.assertEquals("a\"b", new EncodingReferenceInsertionHandler().referenceInsert(null, "$v", "a\"b"));
    }

    @Test
    public void handlerEncodesOnlyWhatReachesTheOutput() throws IOException {
        StringWriter output = new StringWriter();
        new RenderScope(ReferenceEncoder.JSON, output, 10).enter();
        try {
            Object inserted = new EncodingReferenceInsertionHandler().referenceInsert(null, "$v", "a\"b");
            Assertions.assertEquals("a\"b", inserted.toString());

            StringWriter intermediate = new StringWriter();
            ((Renderable) inserted).render(null, intermediate);
            Assertions.assertEquals("a\"b", intermediate.toString());

            ((Renderable) inserted).render(null, output);
            Assertions.assertEquals("a\\\"b", output.toString());
        } finally {
            RenderScope.exit();
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void uberspectorBudgetsIteratorsOnlyInsideRender() {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, LoopLimitingUberspector.class.getName());
        runtime.init();
        Info info = new Info("test", 1, 1);
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3));

        Iterator outsideRender = runtime.getUberspect().getIterator(list, info);
        outsideRender.next();
        outsideRender.next();
        outsideRender.next();

        new RenderScope(ReferenceEncoder.NONE, new StringWriter(), 1).enter();
        try {
            Iterator budgeted = runtime.getUberspect().getIterator(list, info);
            budgeted.next();
            budgeted.remove();
            Assertions.assertEquals(List.of(2, 3), list);
            Assertions.assertTrue(budgeted.hasNext());
            Assertions.assertThrows(VelocityExecutableScript.LimitExceededException.class, budgeted::next);
        } finally {
            RenderScope.exit();
        }
    }

    @Test
    public void sizeLimitingWriterLimitsEveryWritePath() throws IOException {
        var writer = new VelocityExecutableScript.SizeLimitingWriter(5);
        writer.write('a');
        writer.write(new char[]{'b', 'c'}, 0, 2);
        writer.append("de");
        writer.flush();
        writer.close();
        Assertions.assertEquals("abcde", writer.toString());
        Assertions.assertThrows(VelocityExecutableScript.LimitExceededException.class, () -> writer.write('f'));
        Assertions.assertThrows(VelocityExecutableScript.LimitExceededException.class, () -> writer.write(new char[]{'f'}, 0, 1));
    }
}
