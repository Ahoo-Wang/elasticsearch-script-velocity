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
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class VelocityScriptEngine implements ScriptEngine {
    public static final String NAME = "velocity";
    private final VelocityEngine velocityEngine;
    private final StringResourceRepository stringResourceRepository;
    
    public VelocityScriptEngine() {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "string");
        velocityEngine.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
        velocityEngine.setProperty("resource.loader.string.repository.class", StringResourceRepositoryImpl.class.getName());
        velocityEngine.init();
        stringResourceRepository = StringResourceLoader.getRepository();
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
        
        try {
            stringResourceRepository.putStringResource(name, code);
            Template template = velocityEngine.getTemplate(name);
            TemplateScript.Factory compiled = _params -> new VelocityExecutableScript(template, _params);
            return context.factoryClazz.cast(compiled);
        } catch (VelocityException ex) {
            throw new ScriptException(ex.getMessage(), ex, Collections.emptyList(), code, NAME);
        }
    }
    
    @Override
    public Set<ScriptContext<?>> getSupportedContexts() {
        return Collections.singleton(TemplateScript.CONTEXT);
    }
}
