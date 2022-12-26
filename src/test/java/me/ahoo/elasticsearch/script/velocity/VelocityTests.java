package me.ahoo.elasticsearch.script.velocity;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

/**
 * @author ahoo wang
 **/
public class VelocityTests {
    private static final VelocityEngine engine;
    private static final StringResourceRepository stringResourceRepository;
    private static final String DEFAULT_TEMPLATE_NAME = "default_template";
    private static final String DEFAULT_TEMPLATE_VALUE = "this is a velocity template.";

    static {
        engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "string");
        engine.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
        engine.setProperty("resource.loader.string.repository.class", StringResourceRepositoryImpl.class.getName());
        engine.init();
        stringResourceRepository = StringResourceLoader.getRepository();
        stringResourceRepository.putStringResource(DEFAULT_TEMPLATE_NAME, DEFAULT_TEMPLATE_VALUE);
    }

    @Test
    public void testDefault() {
        Template template = engine.getTemplate(DEFAULT_TEMPLATE_NAME);
        VelocityExecutableScript velocityExecutableScript = new VelocityExecutableScript(template, new HashMap<>());
        var renderValue = velocityExecutableScript.execute();
        Assert.assertEquals(DEFAULT_TEMPLATE_VALUE, renderValue);
    }
}
