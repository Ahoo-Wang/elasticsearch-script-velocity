package me.ahoo.elasticsearch.script.velocity;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author ahoo wang
 * Creation time: 2020/5/12 15:13
 */
public class VelocityScriptEngine implements ScriptEngine {
    public static final String NAME = "velocity";
    private final VelocityEngine velocityEngine;
    private final StringResourceRepository stringResourceRepository;

    public VelocityScriptEngine() {
        SpecialPermission.check();
        velocityEngine = AccessController.doPrivileged((PrivilegedAction<VelocityEngine>) () -> {
            final VelocityEngine engine = new VelocityEngine();
            engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "string");
            engine.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
            engine.setProperty("resource.loader.string.repository.class", StringResourceRepositoryImpl.class.getName());
            engine.init();
            return engine;
        });
        stringResourceRepository = StringResourceLoader.getRepository();
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public <FactoryType> FactoryType compile(String name, String code, ScriptContext<FactoryType> context, Map<String, String> params) {
        if (context.instanceClazz.equals(TemplateScript.class) == false) {
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
