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

import org.apache.velocity.app.event.ReferenceInsertionEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.runtime.Renderable;
import org.apache.velocity.util.DuckType;

import java.io.IOException;
import java.io.Writer;

/**
 * Encodes every {@code $reference} written to the script's output with the current render's {@link ReferenceEncoder}.
 * <p>
 * References rendered anywhere else, such as string-literal interpolation ({@code #set($q = "*$keywords*")}), are left
 * raw, so a value is encoded exactly once: when it finally reaches the output. Registered engine-wide (see
 * {@link RenderScope}), so Velocity instantiates it through the public no-arg constructor.
 */
public class EncodingReferenceInsertionHandler implements ReferenceInsertionEventHandler {

    @Override
    public Object referenceInsert(Context context, String reference, Object value) {
        RenderScope scope = RenderScope.current();
        // null keeps Velocity's own handling of missing references; Renderables (#define blocks, $bodyContent)
        // render their inner references themselves, and those pass through here individually.
        if (scope == null || scope.encoder() == ReferenceEncoder.NONE || value == null || value instanceof Renderable) {
            return value;
        }
        String text = DuckType.asString(value);
        if (text == null) {
            return value;
        }
        return new EncodedText(text, scope);
    }

    private static final class EncodedText implements Renderable {
        private final String text;
        private final RenderScope scope;

        private EncodedText(String text, RenderScope scope) {
            this.text = text;
            this.scope = scope;
        }

        @Override
        public boolean render(InternalContextAdapter context, Writer writer) throws IOException {
            writer.write(writer == scope.output() ? scope.encoder().encode(text) : text);
            return true;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
