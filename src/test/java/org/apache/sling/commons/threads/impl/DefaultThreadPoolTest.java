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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.sling.commons.threads.ModifiableThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPool;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public class DefaultThreadPoolTest {

    // SLING-7407
    @Test
    public void unboundedQueueMinSizeCorrection() {
        final BundleContext bc = Mockito.mock(BundleContext.class);
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Thread Pool Manager");
        props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
        props.put(Constants.SERVICE_PID, DefaultThreadPool.class.getName() + ".factory");
        DefaultThreadPoolManager dtpm = new DefaultThreadPoolManager(bc, props);
        ModifiableThreadPoolConfig config = new ModifiableThreadPoolConfig();
        config.setMinPoolSize(1);
        config.setMaxPoolSize(5);
        config.setQueueSize(-1);
        ThreadPool tp = dtpm.create(config);
        
        final Semaphore blocker = new Semaphore(0);
        final Semaphore counter = new Semaphore(0);
        final Runnable r = new Runnable() {

            @Override
            public void run() {
                counter.release();
                try {
                    blocker.tryAcquire(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            
        };
        tp.execute(r);
        try {
            assertTrue(counter.tryAcquire(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("got interrupted");
        }
        tp.execute(r);
        try {
            assertTrue(counter.tryAcquire(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("got interrupted");
        }
        blocker.release(2);
    }

}
