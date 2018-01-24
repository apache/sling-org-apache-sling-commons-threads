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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.sling.commons.threads.impl.ThreadLocalChangeListener.Mode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ThreadPoolExecutorCleaningThreadLocalsTest {
    
    public ThreadPoolExecutorCleaningThreadLocals pool;
    
    @Mock
    public ThreadLocalChangeListener listener;
    
    
    @Before
    public void setUp() {
        final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(20);
        final RejectedExecutionHandler rejectionHandler = new ThreadPoolExecutor.AbortPolicy();
        pool = new ThreadPoolExecutorCleaningThreadLocals(
                    1, 1, 100, TimeUnit.MILLISECONDS,
                    queue, Executors.defaultThreadFactory(), rejectionHandler, listener);
    }

    @Test(timeout = 10000)
    public void threadLocalCleanupWorksWithResize() throws Exception {
        
        // configure thread local counts to make sure that
        // 1. the ThreadLocalMap is initialized with the default size
        // 2. the ThreadLocalMap size is expanded
        int[] tlcount = new int[] { 16, 32 };
        for (int count : tlcount) {
            Future<?> result = pool.submit(new RunnableImplementation(count));
            result.get();
        }
    }

    @Test
    public void testThreadLocalBeingCleanedUp() throws InterruptedException, ExecutionException {
        assertTaskDoesNotSeeOldThreadLocals("test");
        assertTaskDoesNotSeeOldThreadLocals("test2");
        // verify mock interactions (at least the additions from the first task should be visible to the listener now)
        Mockito.verify(listener).changed(ArgumentMatchers.eq(Mode.ADDED), ArgumentMatchers.any(Thread.class), ArgumentMatchers.eq(ThreadLocalTask.threadLocalVariable), ArgumentMatchers.eq("test"));
        // no thread locals should have been removed
        Mockito.verify(listener, Mockito.times(0)).changed(ArgumentMatchers.eq(Mode.REMOVED), ArgumentMatchers.any(Thread.class), ArgumentMatchers.eq(ThreadLocalTask.threadLocalVariable), ArgumentMatchers.anyString());
    }

    private void assertTaskDoesNotSeeOldThreadLocals(String value) throws InterruptedException, ExecutionException {
        ThreadLocalTask task = new ThreadLocalTask(value);
        pool.submit(task).get();
        // when get returns the task may not have been cleaned up (i.e. afterExecute() was no necessarily executed), therefore wait a littlebit here
        Thread.sleep(500);
        Assert.assertNull(task.getOldValue());
    }

    private static class RunnableImplementation implements Runnable {
        private final int threadLocalCount;
        private final List<ThreadLocal<String>> threadLocals = new ArrayList<>();
        
        
        public RunnableImplementation(int threadLocalCount) {
            this.threadLocalCount = threadLocalCount;
        }
        
        @Override
        public void run() {
            for ( int i = 0 ; i < threadLocalCount; i++) {
                ThreadLocal<String> tl = new ThreadLocal<>();
                tl.set("val");
                
                threadLocals.add(tl);
            }

            for ( ThreadLocal<String> tl : threadLocals ) {
                String val = tl.get();
                val.toString();
            }
        }
    }

    private static class ThreadLocalTask implements Runnable {
        static final ThreadLocal<String> threadLocalVariable = new ThreadLocal<String>();

        private final String newValue;
        private volatile String oldValue;

        public ThreadLocalTask(String newValue) {
            this.newValue = newValue;
        }

        @Override
        public void run() {
            oldValue = threadLocalVariable.get();
            // set thread local to a new value
            threadLocalVariable.set(newValue);
        }

        public String getOldValue() {
            return oldValue;
        }
    }
}

