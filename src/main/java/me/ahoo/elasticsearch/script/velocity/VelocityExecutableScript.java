package me.ahoo.elasticsearch.script.velocity;

import lombok.SneakyThrows;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 * Creation time: 2020/5/12 15:42
 */
public class VelocityExecutableScript extends TemplateScript {

    private final Template template;
    private final VelocityContext velocityContext;

    public VelocityExecutableScript(Template template, Map<String, Object> params) {
        super(params);
        this.template = template;
        this.velocityContext = new VelocityContext(params);
    }

    @SneakyThrows
    @Override
    public String execute() {
        try (final StringWriter writer = new StringWriter()) {
            // crazy reflection here
            SpecialPermission.check();
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                template.merge(velocityContext, writer);
                return null;
            });
            return writer.toString();
        } catch (Exception e) {
            String errMsg = e.getMessage();
            if (Objects.isNull(errMsg)) {
                errMsg = "Error running " + template.getName();
            }
            List<String> errorStack = Arrays.stream(e.getStackTrace()).map(stackTraceElement -> stackTraceElement.toString()).collect(Collectors.toList());
            throw new ScriptException(errMsg, e, errorStack, template.getName(), VelocityScriptEngine.NAME);
        }
    }
}
