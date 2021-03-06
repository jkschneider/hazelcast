/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.spi.impl;

import com.hazelcast.client.impl.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.HazelcastClientProxy;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.TestUtil;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.impl.client.MapGetRequest;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class ClientInvocationTest extends HazelcastTestSupport {

    private final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    @After
    public void cleanup() {
        hazelcastFactory.terminateAll();
    }

    @Test
    public void clientInvocationFutureShouldNotHangWithCallbackWhenResponseDeserializedIsSetAndResponseIsNull()
            throws ExecutionException, InterruptedException {
        hazelcastFactory.newHazelcastInstance();
        HazelcastClientProxy clientProxy = (HazelcastClientProxy) hazelcastFactory.newHazelcastClient();
        HazelcastClientInstanceImpl client = clientProxy.client;

        Data key = client.getSerializationService().toData("key");
        int partitionId = client.getPartitionService().getPartition(key).getPartitionId();

        for (int i = 0; i < 100; i++)  {
            ClientInvocation invocation =
                    new ClientInvocation(client, new MapGetRequest("MyMap", key), partitionId);
            ClientInvocationFuture future = invocation.invoke();
            future.setResponseDeserialized(true);
            future.andThen(new ExecutionCallback() {
                @Override
                public void onResponse(Object response) {
                }

                @Override
                public void onFailure(Throwable t) {
                }
            });
            future.get();
        }
    }

    /**
     * When a async operation fails because of a node termination,
     * failure stack trace is copied incrementally for each async invocation/future
     * <p/>
     * see https://github.com/hazelcast/hazelcast/issues/4192
     */
    @Test
    public void executionCallback_TooLongThrowableStackTrace() throws InterruptedException {
        Config config = new Config();
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
        HazelcastInstance server = hazelcastFactory.newHazelcastInstance(config);

        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        IMap<Object, Object> map = client.getMap(randomMapName());

        DummyEntryProcessor ep = new DummyEntryProcessor();

        int count = 100;
        FailureExecutionCallback[] callbacks = new FailureExecutionCallback[count];
        String key = randomString();
        for (int i = 0; i < count; i++) {
            callbacks[i] = new FailureExecutionCallback();
            map.submitToKey(key, ep, callbacks[i]);
        }

        // crash the server
        TestUtil.getNode(server).getConnectionManager().shutdown();
        server.getLifecycleService().terminate();

        int callBackCount = 0;
        for (FailureExecutionCallback callback : callbacks) {
            callBackCount++;
            assertOpenEventually("Callback should be notified on time! callbackCount:" + callBackCount, callback.latch);

            Throwable failure = callback.failure;
            if (failure == null) {
                continue;
            }
            int stackTraceLength = failure.getStackTrace().length;
            assertTrue("Failure stack trace should not be too long! Current: "
                    + stackTraceLength, stackTraceLength < 50);

            Throwable cause = failure.getCause();
            if (cause == null) {
                continue;
            }
            stackTraceLength = cause.getStackTrace().length;
            assertTrue("Cause stack trace should not be too long! Current: "
                    + stackTraceLength, stackTraceLength < 50);
        }
    }

    private static class DummyEntryProcessor implements EntryProcessor {
        @Override
        public Object process(Map.Entry entry) {
            LockSupport.parkNanos(10000);
            return null;
        }

        @Override
        public EntryBackupProcessor getBackupProcessor() {
            return null;
        }
    }


    private static class FailureExecutionCallback implements ExecutionCallback {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile Throwable failure;

        @Override
        public void onResponse(Object response) {
            latch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            failure = t;
            latch.countDown();
        }
    }
}
