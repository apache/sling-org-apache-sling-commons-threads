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

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.sling.commons.threads.impl.ThreadLocalChangeListener.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Notifies a {@link ThreadLocalChangeListener} about changes on a thread local storage. In addition it removes all references to variables
 * being added to the thread local storage while the cleaner was running with its {@link cleanup} method.
 * 
 * @see <a href="http://www.javaspecialists.eu/archive/Issue229.html">JavaSpecialist.eu - Cleaning ThreadLocals</a> */
public class ThreadLocalCleaner {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThreadLocalCleaner.class);
    
    /* Reflection fields */
    /** this field is in class {@link ThreadLocal} and is of type {@code ThreadLocal.ThreadLocalMap} */
    private static final Field threadLocalsField;
    /** this field is in class {@link ThreadLocal} and is of type {@code ThreadLocal.ThreadLocalMap} */
    private static final Field inheritableThreadLocalsField;
    private static final Class<?> threadLocalMapClass;
    /** this field is in class {@code ThreadLocal.ThreadLocalMap} and contains an array of {@code ThreadLocal.ThreadLocalMap.Entry's} */
    private static final Field tableField;
    private static final Class<?> threadLocalMapEntryClass;
    /** this field is in class {@code ThreadLocal.ThreadLocalMap.Entry} and contains an object referencing the actual thread local
     * variable */
    private static final Field threadLocalEntryValueField;
    /** this field is in the class {@code ThreadLocal.ThreadLocalMap} and contains the number of the entries */
    private static final Field threadLocalMapSizeField;
    /** this field is in the class {@code ThreadLocal.ThreadLocalMap} and next resize threshold */
    private static final Field threadLocalMapThresholdField;

    static  {
        try {
            threadLocalsField = field(Thread.class, "threadLocals");
            inheritableThreadLocalsField = field(Thread.class, "inheritableThreadLocals");
            threadLocalMapClass = inner(ThreadLocal.class, "ThreadLocalMap");
            tableField = field(threadLocalMapClass, "table");
            threadLocalMapEntryClass = inner(threadLocalMapClass, "Entry");
            threadLocalEntryValueField = field(threadLocalMapEntryClass, "value");
            threadLocalMapSizeField = field(threadLocalMapClass, "size");
            threadLocalMapThresholdField = field(threadLocalMapClass, "threshold");
        } catch (NoSuchFieldException e) {
            ExceptionInInitializerError error = new ExceptionInInitializerError(
                    "Unable to access ThreadLocal class information using reflection");
            error.initCause(e);
            throw error;
        }
    }
    
    /** @param c the class containing the field
     * @param name the name of the field
     * @return the field from the given class with the given name (made accessible)
     * @throws NoSuchFieldException */
    private static Field field(Class<?> c, String name)
            throws NoSuchFieldException {
        Field field = c.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** @param clazz the class containing the inner class
     * @param name the name of the inner class
     * @return the class with the given name, declared as inner class of the given class */
    private static Class<?> inner(Class<?> clazz, String name) {
        for (Class<?> c : clazz.getDeclaredClasses()) {
            if (c.getSimpleName().equals(name)) {
                return c;
            }
        }
        throw new IllegalStateException(
                "Could not find inner class " + name + " in " + clazz);
    }
    
    private static Reference<?>[] copy(Field field) {
        try {
            Thread thread = Thread.currentThread();
            Object threadLocals = field.get(thread);
            if (threadLocals == null)
                return null;
            Reference<?>[] table = (Reference<?>[]) tableField.get(threadLocals);
            return Arrays.copyOf(table, table.length);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access denied", e);
        }
    }

    private static Integer size(Field field, Field sizeField) {
        try {
            Thread thread = Thread.currentThread();
            Object threadLocals = field.get(thread);
            if (threadLocals == null)
                return null;
            return (Integer) sizeField.get(threadLocals);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access denied", e);
        }
    }

    private static void restore(Field field, Object[] value, Integer size, Integer threshold) {
        try {
            Thread thread = Thread.currentThread();
            if (value == null) {
                field.set(thread, null);
                LOG.debug("Restored {} to a null value", field.getName());
            } else {
                final Object threadLocals = field.get(thread);
                tableField.set(threadLocals, value);
                threadLocalMapSizeField.set(threadLocals, size);
                threadLocalMapThresholdField.set(threadLocals, threshold);
                LOG.debug("Restored {} with to {} references, size {}, threshold {}" ,field.getName(), value.length, size, threshold);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access denied", e);
        }
    }

    private final ThreadLocalChangeListener listener;
    private ThreadLocalMapCopy threadLocalsCopy;
    private ThreadLocalMapCopy inheritableThreadLocalsCopy;
    
    public ThreadLocalCleaner(ThreadLocalChangeListener listener) {
        this.listener = listener;
        saveOldThreadLocals();
    }

    public void cleanup() {
        // the first two diff calls are only to notify the listener, the actual cleanup is done by restoreOldThreadLocals
        diff(threadLocalsField, threadLocalsCopy.references);
        diff(inheritableThreadLocalsField, inheritableThreadLocalsCopy.references);
        restoreOldThreadLocals();
    }

    /** Notifies the {@link ThreadLocalChangeListener} about changes on thread local variables for the current thread.
     * 
     * @param field is a field containing a ThreadLocalMap
     * @param backup */
    private void diff(Field field, Reference<?>[] backup) {
        try {
            Thread thread = Thread.currentThread();
            Object threadLocals = field.get(thread);
            if (threadLocals == null) {
                if (backup != null) {
                    for (Reference<?> reference : backup) {
                        changed(thread, reference, Mode.REMOVED);
                    }
                }
                return;
            }

            Reference<?>[] current = (Reference<?>[]) tableField.get(threadLocals);
            if (backup == null) {
                for (Reference<?> reference : current) {
                    changed(thread, reference, Mode.ADDED);
                }
            } else {
                // nested loop - both arrays *should* be relatively small
                next: for (Reference<?> curRef : current) {
                    if (curRef != null) {
                        if (curRef.get() == this.threadLocalsCopy ||
                                curRef.get() == this.inheritableThreadLocalsCopy) {
                            continue next;
                        }
                        for (Reference<?> backupRef : backup) {
                            if (curRef == backupRef)
                                continue next;
                        }
                        // could not find it in backup - added
                        changed(thread, curRef, Mode.ADDED);
                    }
                }
                next: for (Reference<?> backupRef : backup) {
                    for (Reference<?> curRef : current) {
                        if (curRef == backupRef)
                            continue next;
                    }
                    // could not find it in current - removed
                    changed(thread, backupRef, Mode.REMOVED);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access denied", e);
        }
    }

    private void changed(Thread thread, Reference<?> reference,
            ThreadLocalChangeListener.Mode mode)
            throws IllegalAccessException {
        // just skip null reference entries (may happen if array has been resized)
        if (reference != null) {
            listener.changed(mode,
                    thread, (ThreadLocal<?>) reference.get(),
                    threadLocalEntryValueField.get(reference));
        }
    }

    private void saveOldThreadLocals() {
        
        threadLocalsCopy = new ThreadLocalMapCopy(copy(threadLocalsField), 
                size(threadLocalsField, threadLocalMapSizeField),
                size(threadLocalsField, threadLocalMapThresholdField));
        threadLocalsCopy.debug("saved", "Thread locals");
        
        inheritableThreadLocalsCopy = new ThreadLocalMapCopy(copy(inheritableThreadLocalsField),
                size(inheritableThreadLocalsField, threadLocalMapSizeField),
                size(inheritableThreadLocalsField, threadLocalMapThresholdField));
        inheritableThreadLocalsCopy.debug("saved", "Inheritable thread locals");
    }

    private void restoreOldThreadLocals() {
        try {
            restore(inheritableThreadLocalsField, inheritableThreadLocalsCopy.references,
                inheritableThreadLocalsCopy.size, inheritableThreadLocalsCopy.threshold);
            restore(threadLocalsField, threadLocalsCopy.references,
                threadLocalsCopy.size, threadLocalsCopy.threshold);
        } finally {
            threadLocalsCopy = null;
            inheritableThreadLocalsCopy = null;
        }
    }

    /**
     * Helper class that encapsulates the state from a <tt>ThreadLocalMap</tt>
     *
     */
    static class ThreadLocalMapCopy {
        
        private final Reference<?>[] references;
        private final Integer size;
        private final Integer threshold;
        
        private ThreadLocalMapCopy(Reference<?>[] references, Integer size, Integer threshold) {
            this.references = references;
            this.size = size;
            this.threshold = threshold;
        }
        
        void debug(String event, String mapName) {
            if ( references != null ) {
                ThreadLocalCleaner.LOG.debug("{}: {} {} references, size: {}, threshold: {}",
                    mapName, event, references.length, size, threshold);
            } else {
                ThreadLocalCleaner.LOG.debug("{}: {} null references", mapName, event);
            }
        }
    }
}