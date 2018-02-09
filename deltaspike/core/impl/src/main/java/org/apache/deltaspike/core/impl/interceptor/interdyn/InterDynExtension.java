/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.core.impl.interceptor.interdyn;


import org.apache.deltaspike.core.api.config.ConfigResolver;
import org.apache.deltaspike.core.api.config.base.CoreBaseConfig;
import org.apache.deltaspike.core.spi.activation.Deactivatable;
import org.apache.deltaspike.core.util.ClassDeactivationUtils;
import org.apache.deltaspike.core.util.ClassUtils;
import org.apache.deltaspike.core.util.metadata.AnnotationInstanceProvider;
import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * <p>InterDyn is a CDI (JSR-299) Extension for dynamically
 * attaching annotations (e.g. CDI interceptors) to a class.</p>
 *
 */
public class InterDynExtension implements Deactivatable, Extension
{
    private List<AnnotationRule> interceptorRules = new ArrayList<AnnotationRule>();


    private Logger logger = Logger.getLogger(InterDynExtension.class.getName());

    private Map<String, Annotation> usedInterceptorBindings = new HashMap<String, Annotation>();

    private boolean enabled = false;

    @SuppressWarnings("UnusedDeclaration")
    protected void init(@Observes BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager)
    {
        if (!ClassDeactivationUtils.isActivated(getClass()))
        {
            return;
        }

        enabled = CoreBaseConfig.InterDynCustomization.INTERDYN_ENABLED.getValue();

        if (enabled)
        {
            logger.info("Starting with deltaspike.interdyn instrumentation");
            init();
        }
    }

    public void init()
    {
        Set<String> ruleConfigKeys = new HashSet<String>();

        // first we collect all the rule property names
        for (String propertyName : ConfigResolver.getAllProperties().keySet())
        {
            if (propertyName.startsWith(CoreBaseConfig.InterDynCustomization.INTERDYN_RULE_PREFIX) &&
                propertyName.contains(".match"))
            {
                ruleConfigKeys.add(propertyName.substring(0, propertyName.indexOf(".match")));
            }
        }

        for (String ruleConfigKey : ruleConfigKeys)
        {
            String match = ConfigResolver.getPropertyValue(ruleConfigKey + ".match");
            String annotationClassName = ConfigResolver.getPropertyValue(ruleConfigKey + ".annotation");

            if (match != null && annotationClassName != null &&
                match.length() > 0 && annotationClassName.length() > 0)
            {
                Annotation anno = getAnnotationImplementation(annotationClassName);
                interceptorRules.add(new AnnotationRule(match, anno));
            }
        }


        if (interceptorRules.isEmpty())
        {
            enabled = false;
        }
    }

    public void processAnnotatedType(@Observes ProcessAnnotatedType pat)
    {
        if (enabled)
        {
            String beanClassName = pat.getAnnotatedType().getJavaClass().getName();
            AnnotatedType at = pat.getAnnotatedType();
            AnnotatedTypeBuilder atb = null;
            for (AnnotationRule rule : interceptorRules)
            {
                if (beanClassName.matches(rule.getRule()))
                {
                    if (atb == null)
                    {
                        atb = new AnnotatedTypeBuilder();
                        atb.readFromType(at);
                    }
                    atb.addToClass(rule.getAdditionalAnnotation());
                    logger.info("Adding Dynamic Interceptor " + rule.getAdditionalAnnotation()
                            + " to class " + beanClassName );
                }
            }
            if (atb != null)
            {
                pat.setAnnotatedType(atb.create());
            }
        }
    }

    private Annotation getAnnotationImplementation(String interceptorBindingClassName)
    {
        Annotation ann = usedInterceptorBindings.get(interceptorBindingClassName);

        if (ann == null)
        {
            Class<? extends Annotation> annClass;
            try
            {
                annClass = (Class<? extends Annotation>)
                        ClassUtils.getClassLoader(null).loadClass(interceptorBindingClassName);
            }
            catch (ClassNotFoundException e)
            {
                throw new RuntimeException("Error while picking up dynamic InterceptorBindingType for class" +
                                           interceptorBindingClassName, e);
            }
            ann = AnnotationInstanceProvider.of(annClass);
            usedInterceptorBindings.put(interceptorBindingClassName, ann);
        }
        return ann;
    }
}
