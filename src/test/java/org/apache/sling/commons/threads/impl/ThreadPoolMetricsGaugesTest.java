/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.commons.threads.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.management.DynamicMBean;
import javax.management.JMRuntimeException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.apache.sling.commons.metrics.Gauge;
import org.junit.Test;

public class ThreadPoolMetricsGaugesTest {

    private static final String ATTR_A_NAME = "a";
    private static final Integer ATTR_A_VALUE = Integer.MIN_VALUE;

    private static final String ATTR_B_NAME = "b";
    private static final String ATTR_B_VALUE = "foo";

    private static final String ATTR_C_NAME = "c";
    private static final Character ATTR_C_VALUE = '_';

    private static final String ATTR_D_NAME = "d";
    private static final String ATTR_D_VALUE = "";

    private static final class Attributes {

        Integer getA() {
            return ATTR_A_VALUE;
        }

        String getB() {
            return ATTR_B_VALUE;
        }

        Character getC() {
            return ATTR_C_VALUE;
        }

        @SuppressWarnings("unused")
        String getD() {
            throw new IllegalStateException("should not get even called");
        }
    }

    private static String attrToGetter(String attrName) {
        return "get" + attrName.toUpperCase();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGaugesCreation() throws Exception {
        Class<Attributes> c = Attributes.class;
        MBeanInfo info = new MBeanInfo("dummy class name", null,
                new MBeanAttributeInfo[] {
                        new MBeanAttributeInfo(ATTR_A_NAME, null, c.getDeclaredMethod(attrToGetter(ATTR_A_NAME)), null),
                        new MBeanAttributeInfo(ATTR_B_NAME, null, c.getDeclaredMethod(attrToGetter(ATTR_B_NAME)), null),
                        new MBeanAttributeInfo(ATTR_C_NAME, null, c.getDeclaredMethod(attrToGetter(ATTR_C_NAME)), null),
                        new MBeanAttributeInfo(ATTR_D_NAME, null, c.getDeclaredMethod(attrToGetter(ATTR_D_NAME)), null),
                //
                }, null, null, null);

        DynamicMBean bean = mock(DynamicMBean.class);
        when(bean.getMBeanInfo()).thenReturn(info);
        when(bean.getAttribute(eq(ATTR_A_NAME))).thenReturn(new Attributes().getA());
        when(bean.getAttribute(eq(ATTR_B_NAME))).thenReturn(new Attributes().getB());
        when(bean.getAttribute(eq(ATTR_C_NAME))).thenReturn(new Attributes().getC());
        when(bean.getAttribute(eq(ATTR_D_NAME)))
                .thenThrow(new JMRuntimeException("this exception is for unit test only"));

        Map<String, Gauge<?>> gauges = ThreadPoolMetricsGauges.create(bean);

        Gauge<Integer> gaugeA = (Gauge<Integer>) gauges.get(ATTR_A_NAME);
        assertNotNull(gaugeA);
        assertEquals(ATTR_A_VALUE, gaugeA.getValue());

        Gauge<Integer> gaugeB = (Gauge<Integer>) gauges.get(ATTR_B_NAME);
        assertNotNull(gaugeA);
        assertEquals(ATTR_B_VALUE, gaugeB.getValue());

        Gauge<?> gaugeC = gauges.get(ATTR_C_NAME);
        assertNull("type " + Character.class.getName() + " should not be supported", gaugeC);

        Gauge<String> gaugeD = (Gauge<String>) gauges.get(ATTR_D_NAME);
        assertNotNull(gaugeD);
        assertEquals(ATTR_D_VALUE, gaugeD.getValue());
    }
}
