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

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VelocityExecutableScript extends TemplateScript {

    private final Template template;
    private final ReferenceEncoder encoder;
    private final int maxOutputSize;
    private final int maxLoopIterations;

    VelocityExecutableScript(Template template, Map<String, Object> params, ReferenceEncoder encoder, int maxOutputSize, int maxLoopIterations) {
        super(params);
        this.template = template;
        this.encoder = encoder;
        this.maxOutputSize = maxOutputSize;
        this.maxLoopIterations = maxLoopIterations;
    }

    @Override
    public String execute() {
        final SizeLimitingWriter writer = new SizeLimitingWriter(maxOutputSize);
        new RenderScope(encoder, writer, maxLoopIterations).enter();
        try {
            // A fresh context per call, built from a deep copy of the params: VelocityContext keeps the map by reference,
            // and templates can #set, nested maps included (#set($filter.field = ...)), so neither the caller's params
            // nor a later execute() may see this run's changes.
            template.merge(new VelocityContext(copyParams(getParams())), writer);
        } catch (StackOverflowError e) {
            // Don't attach the Error: Elasticsearch halts the node when it finds an Error in a cause chain.
            throw new ScriptException("velocity template nests too deeply to render", new IllegalArgumentException("template nesting exceeded the thread stack"),
                    Collections.emptyList(), template.getName(), VelocityScriptEngine.NAME);
        } catch (Exception e) {
            Throwable limit = ExceptionsHelper.unwrap(e, LimitExceededException.class);
            if (limit != null) {
                throw new ScriptException(limit.getMessage(), limit, Collections.emptyList(), template.getName(), VelocityScriptEngine.NAME);
            }
            String errMsg = e.getMessage();
            if (Objects.isNull(errMsg)) {
                errMsg = "Error running " + template.getName();
            }
            throw new ScriptException(errMsg, e, Collections.emptyList(), template.getName(), VelocityScriptEngine.NAME);
        } finally {
            RenderScope.exit();
        }

        return writer.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyParams(Map<String, Object> params) {
        return (Map<String, Object>) deepCopy(params);
    }

    /**
     * Copies the Map/List structure that request params are parsed into (keeping iteration order); other values are
     * shared as they are.
     */
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = LinkedHashMap.newLinkedHashMap(map.size());
            map.forEach((key, item) -> copy.put(key, deepCopy(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(deepCopy(item)));
            return copy;
        }
        return value;
    }

    /**
     * Thrown when a render goes over one of the engine's limits (output size, loop iterations).
     */
    static final class LimitExceededException extends RuntimeException {
        LimitExceededException(String message) {
            super(message);
        }
    }

    /**
     * Collects the output and fails once it would grow past the limit, like mustache's {@code SizeLimitingStringWriter}
     * (the limit counts characters).
     */
    static final class SizeLimitingWriter extends Writer {
        private final StringBuilder buffer = new StringBuilder();
        private final int maxSize;

        SizeLimitingWriter(int maxSize) {
            this.maxSize = maxSize;
        }

        // Writer routes write(int), write(String) and append(...) through the two methods below.

        @Override
        public void write(char[] cbuf, int off, int len) {
            ensureRoomFor(len);
            buffer.append(cbuf, off, len);
        }

        @Override
        public void write(String str, int off, int len) {
            ensureRoomFor(len);
            buffer.append(str, off, off + len);
        }

        private void ensureRoomFor(int len) {
            if ((long) buffer.length() + len > maxSize) {
                throw new LimitExceededException("velocity script output exceeded the limit of [" + maxSize + "] characters; raise ["
                        + VelocityScriptEngine.MAX_OUTPUT_SIZE.getKey() + "] if this is expected");
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }
}
