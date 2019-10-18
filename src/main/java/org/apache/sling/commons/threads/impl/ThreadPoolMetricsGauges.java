/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.commons.threads.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;

import org.apache.sling.commons.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ThreadPoolMetricsGauges {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolMetricsGauges.class);

    // not exposing MaxThreadAge as bean.getMaxThreadAge() is deprecated due
    // to SLING-6261 and always returns -1
    private static final List<String> IGNORED_ATTRIBUTES = Collections.singletonList("MaxThreadAge");

    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();

    static {
        WRAPPER_TO_PRIMITIVE.put(Integer.class, int.class);
        WRAPPER_TO_PRIMITIVE.put(Long.class, long.class);
        WRAPPER_TO_PRIMITIVE.put(Boolean.class, boolean.class);
    }

    private static final Map<Class<?>, Object> CLASS_TO_DEFAULT_VALUE = new HashMap<>();

    static {
        CLASS_TO_DEFAULT_VALUE.put(Integer.class, -1);
        CLASS_TO_DEFAULT_VALUE.put(Long.class, -1L);
        CLASS_TO_DEFAULT_VALUE.put(Boolean.class, false);
        CLASS_TO_DEFAULT_VALUE.put(String.class, "");
    }

    private ThreadPoolMetricsGauges() {
    }

    private static boolean isTypeOfClassOrPrimitive(MBeanAttributeInfo attr, Class<?> clazz) {
        if (clazz.getName().equals(attr.getType())) {
            return true;
        } else {
            Class<?> primitive = WRAPPER_TO_PRIMITIVE.get(clazz);
            return primitive != null && primitive.getName().equals(attr.getType());
        }
    }

    private static <T> Gauge<T> createGauge(final DynamicMBean bean, final String name, final Object defaultValue) {
        return new Gauge<T>() {
            @SuppressWarnings("unchecked")
            public T getValue() {
                try {
                    return (T) bean.getAttribute(name);
                } catch (Exception e) {
                    LOGGER.warn("cannot obtain MBean attribute named " + name, e);
                    return (T) defaultValue;
                }
            }
        };
    }

    public static Map<String, Gauge<?>> create(final DynamicMBean bean) {
        Map<String, Gauge<?>> gauges = new HashMap<>();
        for (MBeanAttributeInfo attr : bean.getMBeanInfo().getAttributes()) {
            String name = attr.getName();
            if (IGNORED_ATTRIBUTES.contains(name)) {
                LOGGER.debug("ignoring MBean attribute {}", name);
            } else {
                boolean gaugeForAttributeCreated = false;

                final Set<Class<?>> supportedClasses = CLASS_TO_DEFAULT_VALUE.keySet();
                for (Class<?> clazz : supportedClasses) {
                    if (isTypeOfClassOrPrimitive(attr, clazz)) {
                        gauges.put(name, createGauge(bean, name, CLASS_TO_DEFAULT_VALUE.get(clazz)));
                        gaugeForAttributeCreated = true;
                    }
                }

                if (!gaugeForAttributeCreated) {
                    LOGGER.warn("no gauge for attribute {} created as type {} is not supported", name, attr.getType());
                }
            }
        }
        return gauges;
    }
}
