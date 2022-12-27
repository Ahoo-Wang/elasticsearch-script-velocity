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
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.TemplateScript;

import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.stream.Collectors;

public class VelocityExecutableScript extends TemplateScript {
    
    private final Template template;
    private final VelocityContext velocityContext;
    
    public VelocityExecutableScript(Template template, Map<String, Object> params) {
        super(params);
        this.template = template;
        velocityContext = new VelocityContext(new HashMap<>(params));
    }
    
    @SuppressWarnings("removal")
    @Override
    public String execute() {
        final StringWriter writer = new StringWriter();
        SpecialPermission.check();
        try {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                template.merge(velocityContext, writer);
                return null;
            });
        } catch (Exception e) {
            String errMsg = e.getMessage();
            if (Objects.isNull(errMsg)) {
                errMsg = "Error running " + template.getName();
            }
            List<String> errorStack = Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.toList());
            throw new ScriptException(errMsg, e, errorStack, template.getName(), VelocityScriptEngine.NAME);
        }
        
        return writer.toString();
    }
}
