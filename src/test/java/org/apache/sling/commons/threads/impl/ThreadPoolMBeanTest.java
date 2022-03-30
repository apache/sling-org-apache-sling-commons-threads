package org.apache.sling.commons.threads.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.sling.commons.threads.jmx.ThreadPoolMBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class ThreadPoolMBeanTest {

    private ThreadPoolMBean threadPoolMBean;

    @Mock
    private DefaultThreadPoolManager.Entry entry;

    @Mock
    private ThreadLocalChangeListener listener;

    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(20);
    final RejectedExecutionHandler rejectionHandler = new ThreadPoolExecutor.AbortPolicy();

    @Before
    public void setUp() throws Exception {
        threadPoolMBean = new ThreadPoolMBeanImpl(entry);
    }

    @Test
    public void testThreadLocalCleanupCount() {
        ThreadPoolExecutor pool = new ThreadPoolExecutorCleaningThreadLocals(
                1, 1, 100, TimeUnit.MILLISECONDS,
                queue, Executors.defaultThreadFactory(), rejectionHandler, listener);
        doReturn(pool).when(entry).getExecutor();
        assertTrue(threadPoolMBean.getThreadLocalCleanupCount() > -1);
    }

    @Test
    public void testThreadLocalCleanupCountWithNormalExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 100, TimeUnit.MILLISECONDS,
                queue, Executors.defaultThreadFactory(), rejectionHandler);
        doReturn(pool).when(entry).getExecutor();
        assertEquals(-1, threadPoolMBean.getThreadLocalCleanupCount());
    }

}
