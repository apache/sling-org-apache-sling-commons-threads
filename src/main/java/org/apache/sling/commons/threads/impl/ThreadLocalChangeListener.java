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

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Interface for listeners being attached to {@link ThreadLocalCleaner}.
 */
public interface ThreadLocalChangeListener {

    Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Invoked when the cleaner detects that a thread-local value was added of removed 
     * after an execution has completed
     * 
     * @param the mode
     * @param the thread
     * @param the thread local, possibly null
     * @param the value, possibly null
     */
    
    void changed(Mode mode, Thread thread, ThreadLocal<?> threadLocal, Object value);
    
    boolean isEnabled();

    enum Mode {
        ADDED, REMOVED
    }
}
