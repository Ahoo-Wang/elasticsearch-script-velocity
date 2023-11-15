package me.ahoo.elasticsearch.script.velocity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class VelocityScriptEngineBenchmark {
    VelocityScriptEngineTest velocityScriptEngineTest = new VelocityScriptEngineTest();

    @Benchmark
    public String executeSearchTemplate() {
        return velocityScriptEngineTest.executeSearchTemplate();
    }

}
