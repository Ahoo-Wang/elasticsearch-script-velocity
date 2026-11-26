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
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.io.StringReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class VelocityScriptEngine implements ScriptEngine {
    public static final String NAME = "velocity";

    /**
     * Maximum size of one rendered script, mirroring {@code mustache.max_output_size_bytes}.
     */
    public static final Setting<ByteSizeValue> MAX_OUTPUT_SIZE = Setting.byteSizeSetting("velocity.max_output_size_bytes",
            ByteSizeValue.ofMb(1), ByteSizeValue.ofBytes(1), ByteSizeValue.ofBytes(Integer.MAX_VALUE), Setting.Property.NodeScope);

    /**
     * Maximum number of {@code #foreach} iterations of one render, all loops (nested ones included) together.
     */
    public static final Setting<Integer> MAX_LOOP_ITERATIONS = Setting.intSetting("velocity.max_loop_iterations", 100_000, 1,
            Setting.Property.NodeScope);

    private final RuntimeInstance runtime;
    private final int maxOutputSize;
    private final int maxLoopIterations;

    @SuppressWarnings("removal")
    public VelocityScriptEngine(Settings settings) {
        maxOutputSize = Math.toIntExact(MAX_OUTPUT_SIZE.get(settings).getBytes());
        maxLoopIterations = MAX_LOOP_ITERATIONS.get(settings);
        runtime = AccessController.doPrivileged((PrivilegedAction<RuntimeInstance>) () -> {
            final RuntimeInstance instance = new RuntimeInstance();
            // A SecureUberspector (blocks Class/ClassLoader/Runtime/System/... access) that also enforces the loop limit.
            instance.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, LoopLimitingUberspector.class.getName());
            // Encodes $references per the script's content_type (JSON by default, like mustache); see RenderScope for
            // why it is registered here rather than on each render's context.
            instance.setProperty(RuntimeConstants.EVENTHANDLER_REFERENCEINSERTION, EncodingReferenceInsertionHandler.class.getName());
            // Keep inline #macro definitions local to the template that defines them. Velocity's default registers them in
            // one engine-wide namespace where the first definition wins, so templates sharing a macro name, or a stored
            // script whose macro changed, would render another template's macro body.
            instance.setProperty(RuntimeConstants.VM_PERM_INLINE_LOCAL, "true");
            // Scripts are parsed straight from their source (see parse()). The only resource loader is a string loader
            // with a private, always-empty repository, so #parse/#include cannot load other scripts, and Velocity's
            // default file loader (rooted at the working directory) is not configured.
            instance.setProperty(RuntimeConstants.RESOURCE_LOADERS, "string");
            instance.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
            instance.setProperty("resource.loader.string.repository.static", "false");
            instance.init();
            return instance;
        });
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public <FactoryType> FactoryType compile(String name, String code, ScriptContext<FactoryType> context, Map<String, String> params) {
        if (!context.instanceClazz.equals(TemplateScript.class)) {
            throw new IllegalArgumentException("velocity engine does not know how to handle context [" + context.name + "]");
        }
        final ReferenceEncoder encoder = ReferenceEncoder.fromOptions(params);

        try {
            Template template = parse(name == null ? code : name, code);
            TemplateScript.Factory compiled = new VelocityExecutableScriptFactory(template, encoder, maxOutputSize, maxLoopIterations);
            return context.factoryClazz.cast(compiled);
        } catch (VelocityException ex) {
            throw new ScriptException(ex.getMessage(), ex, Collections.emptyList(), code, NAME);
        } catch (StackOverflowError e) {
            // Don't attach the Error: Elasticsearch halts the node when it finds an Error in a cause chain.
            throw new ScriptException("velocity template nests too deeply to compile", new IllegalArgumentException("template nesting exceeded the thread stack"),
                    Collections.emptyList(), code, NAME);
        }
    }

    /**
     * Parses {@code code} into a template of its own. Nothing is shared between compiles, so concurrent compiles of
     * different sources under one name (a stored-script update racing a search) cannot pick up each other's source.
     */
    private Template parse(String name, String code) {
        final Template template = new Template();
        template.setName(name);
        template.setRuntimeServices(runtime);
        try {
            template.setData(runtime.parse(new StringReader(code), template));
        } catch (ParseException e) {
            throw new ParseErrorException(e, name);
        }
        template.initDocument();
        return template;
    }

    @Override
    public Set<ScriptContext<?>> getSupportedContexts() {
        return Collections.singleton(TemplateScript.CONTEXT);
    }

    public static class VelocityExecutableScriptFactory implements TemplateScript.Factory {
        private final Template template;
        private final ReferenceEncoder encoder;
        private final int maxOutputSize;
        private final int maxLoopIterations;

        VelocityExecutableScriptFactory(Template template, ReferenceEncoder encoder, int maxOutputSize, int maxLoopIterations) {
            this.template = template;
            this.encoder = encoder;
            this.maxOutputSize = maxOutputSize;
            this.maxLoopIterations = maxLoopIterations;
        }

        @Override
        public TemplateScript newInstance(Map<String, Object> params) {
            return new VelocityExecutableScript(template, params, encoder, maxOutputSize, maxLoopIterations);
        }

    }
}
