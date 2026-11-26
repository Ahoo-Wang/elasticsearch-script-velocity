/*
 *  Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.ahoo.elasticsearch.script.velocity;

import java.io.Writer;

/**
 * State of the render running on the current thread, for the components Velocity instantiates once per engine
 * ({@link LoopLimitingUberspector}, {@link EncodingReferenceInsertionHandler}). {@link VelocityExecutableScript#execute()}
 * enters a scope around each merge.
 * <p>
 * The reference-insertion handler is registered engine-wide on purpose: attaching an {@code EventCartridge} to the
 * context instead makes Velocity swallow exceptions thrown by template method calls.
 */
final class RenderScope {

    private static final ThreadLocal<RenderScope> CURRENT = new ThreadLocal<>();

    private final ReferenceEncoder encoder;
    private final Writer output;
    private final int maxLoopIterations;
    private long loopIterations;

    RenderScope(ReferenceEncoder encoder, Writer output, int maxLoopIterations) {
        this.encoder = encoder;
        this.output = output;
        this.maxLoopIterations = maxLoopIterations;
    }

    static RenderScope current() {
        return CURRENT.get();
    }

    void enter() {
        CURRENT.set(this);
    }

    static void exit() {
        CURRENT.remove();
    }

    ReferenceEncoder encoder() {
        return encoder;
    }

    /**
     * The writer the script's output goes to; anything else a reference renders into is an intermediate buffer.
     */
    Writer output() {
        return output;
    }

    void countLoopIteration() {
        if (++loopIterations > maxLoopIterations) {
            throw new VelocityExecutableScript.LimitExceededException("velocity #foreach iterations exceeded the limit of ["
                    + maxLoopIterations + "]; raise [" + VelocityScriptEngine.MAX_LOOP_ITERATIONS.getKey() + "] if this is expected");
        }
    }
}
