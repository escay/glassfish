/*
 * Copyright (c) 2024 Eclipse Foundation and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.resource.pool;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.notNull;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.easymock.IExpectationSetters;
import org.glassfish.api.admin.ProcessEnvironment;
import org.glassfish.api.naming.SimpleJndiName;
import org.glassfish.internal.api.DelegatingClassLoader;
import org.glassfish.resourcebase.resources.api.PoolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.sun.appserv.connectors.internal.api.ConnectorRuntimeException;
import com.sun.appserv.connectors.internal.api.PoolingException;
import com.sun.enterprise.connectors.ConnectorConnectionPool;
import com.sun.enterprise.connectors.ConnectorRuntime;
import com.sun.enterprise.connectors.util.ConnectionPoolObjectsUtils;
import com.sun.enterprise.deployment.ConnectorDescriptor;
import com.sun.enterprise.resource.ResourceHandle;
import com.sun.enterprise.resource.ResourceSpec;
import com.sun.enterprise.resource.ResourceState;
import com.sun.enterprise.resource.allocator.LocalTxConnectorAllocator;
import com.sun.enterprise.resource.allocator.ResourceAllocator;
import com.sun.enterprise.resource.pool.datastructure.DataStructure;
import com.sun.enterprise.transaction.api.JavaEETransaction;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.transaction.Transaction;

public class ConnectionPoolTest {

    private static final String NO_AVAILABLE_RESOURCES_AND_WAIT_TIME = "No available resources and wait time ";
    private static final String MS_EXPIRED = " ms expired.";
    
    private ManagedConnection managedConnection;
    private ManagedConnectionFactory managedConnectionFactory;
    private JavaEETransaction javaEETransaction;

    private ConnectionPool connectionPool;

    private ResourceSpec resourceSpec;
    
    // TODO: Look at tests like: org.glassfish.jdbc.devtests.v3.test.MaxConnectionUsageTest and the others in the package, they can probably also be made
    // as a unit test here / or in a similar unit test

    @BeforeEach
    public void createAndPopulateMocks() throws PoolingException, ResourceException {
        List<Object> mocks = new ArrayList<>();

        // Mock ManagedConnection
        managedConnection = createNiceMock(ManagedConnection.class);
        mocks.add(managedConnection);

        // Mock ManagedConnectionFactory
        ManagedConnectionFactory localConnectionFactory = createMock(ManagedConnectionFactory.class);
        IExpectationSetters<ManagedConnection> createConnectionExpectation = expect(
                localConnectionFactory.createManagedConnection(isNull(), isNull()));
        createConnectionExpectation.andReturn(managedConnection)
                .atLeastOnce();

        // Must return a not null object in matchManagedConnections to ensure matching in the ConnectionPool is 'true'
        IExpectationSetters<ManagedConnection> matchExpectation = expect(localConnectionFactory.matchManagedConnections(notNull(), isNull(), isNull()));
        matchExpectation.andReturn(managedConnection)
                .atLeastOnce();

        managedConnectionFactory = localConnectionFactory;
        mocks.add(managedConnectionFactory);

        // Mock JavaEETransaction
        javaEETransaction = createNiceMock(JavaEETransaction.class);
        mocks.add(javaEETransaction);

        replay(mocks.toArray());

        // Make sure ConnectorRuntime singleton is initialized
        MyConnectorRuntime connectorRuntime = new MyConnectorRuntime();
        ProcessEnvironment processEnvironment = new ProcessEnvironment();
        connectorRuntime.setProcessEnvironment(processEnvironment);
        connectorRuntime.postConstruct();
    }

    private void createConnectionPool(int maxPoolSize, int maxWaitTimeInMillis, int poolResizeQuantity) throws PoolingException {
        PoolInfo poolInfo = ConnectionPoolTest.getPoolInfo();
        MyConnectionPool.myMaxPoolSize = maxPoolSize;
        MyConnectionPool.maxWaitTimeInMillis = maxWaitTimeInMillis;
        MyConnectionPool.poolResizeQuantity = poolResizeQuantity;
        
        connectionPool = new MyConnectionPool(poolInfo);
        assertEquals(0, connectionPool.getSteadyPoolSize());
        assertEquals(maxPoolSize, connectionPool.getMaxPoolSize());

        resourceSpec = new ResourceSpec(new SimpleJndiName("myResourceSpec"), ResourceSpec.JNDI_NAME);
        resourceSpec.setPoolInfo(poolInfo);
    }

    /**
     * Basic test to show ConnectionPool can be instantiated in a unit test
     */
    @Test
    void basicConnectionPoolTest() throws Exception {
        // Use the lowest maxWaitTimeInMillis, because this test knows the pool is exhausted on the 3rd call 
        // for a resource, and we want it to fail asap. Do not use 0, because it means wait indefinately. 
        final int maxWaitTimeInMillis = 1;
        
        // Use poolResizeQuantity 1, to have the easiest understanding and predictability of the test 
        final int poolResizeQuantity = 1;
        
        createConnectionPool(2, maxWaitTimeInMillis, poolResizeQuantity);

        ResourceAllocator alloc = new LocalTxConnectorAllocator(null, managedConnectionFactory, resourceSpec, null,
                null, null, null, false);
        Transaction transaction = javaEETransaction;

        // Test how many resources are available
        // Expect 0 because the pool is not initialized yet. It will be initialized when the first resource is requested
        assertResourcesSize(0);

        // Test getting 3 resources from the pool of 2
        ResourceHandle resource1 = connectionPool.getResource(resourceSpec, alloc, transaction);
        assertNotNull(resource1);
        assertResourceIsBusy(resource1);
        // Test the "pool-resize-quantity" logic. 1 new resource should have been added to the pool.
        // TODO test with value "3" or a value higher than the pool size.
        assertResourcesSize(1);

        ResourceHandle resource2 = connectionPool.getResource(resourceSpec, alloc, transaction);
        assertNotNull(resource2);
        assertResourceIsBusy(resource2);
        assertResourcesSize(2);

        PoolingException assertThrows = assertThrows(PoolingException.class, () -> {
            connectionPool.getResource(resourceSpec, alloc, transaction);
        });
        assertEquals(NO_AVAILABLE_RESOURCES_AND_WAIT_TIME + maxWaitTimeInMillis + MS_EXPIRED, assertThrows.getMessage());

        // Test returning 2 resources to the pool
        connectionPool.resourceClosed(resource2);
        assertResourceIsNotBusy(resource2);

        // Test issue #24843: make the state of resource1 not busy anymore (it should not happen but it happens in rare cases),
        // resource should still be closed without throwing an exception.
        resource1.getResourceState().setBusy(false);
        connectionPool.resourceClosed(resource1);
        assertResourceIsNotBusy(resource1);

        // Test how many resources are available
        assertResourcesSize(2);

        connectionPool.emptyPool();
        assertResourcesSize(0);
    }

    @Test
    @Timeout(value = 10)
    void basicConnectionPoolMultiThreadedTest() throws Exception {
        int maxConnectionPoolSize = 5;
        
        final int maxWaitTimeInMillis = 2000;
        
        // Use poolResizeQuantity 1, to have the easiest understanding and predictability of the test
        final int poolResizeQuantity = 1;
        
        createConnectionPool(maxConnectionPoolSize, maxWaitTimeInMillis, poolResizeQuantity);

        ResourceAllocator alloc = new LocalTxConnectorAllocator(null, managedConnectionFactory, resourceSpec, null,
                null, null, null, false);

        // Keep track of resources and number of tasks executed
        final Set<ResourceHandle> resources = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger numberOfThreadsFinished = new AtomicInteger();
        
        // Make sure there are no resources in the pool before we start processing tasks
        assertResourcesSize(0);
        
        int taskCount = 1000;
        List<Callable<Void>> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            tasks.add(() -> {
                // Get resource. PoolingException should only be possible if the maxWaitTimeInMillis is too low for the amount of tasks.
                ResourceHandle resource = connectionPool.getResource(resourceSpec, alloc, javaEETransaction);
                
                assertNotNull(resource);
                assertResourceIsBusy(resource);

                // Keep track of unique resources, we want to validate if resources are being reused
                // which is the basic usage of the pool.
                boolean isNewlyAdded = resources.add(resource);
                if (isNewlyAdded) {
                    // Adding should only be ok for the first '30' tasks that start
                    // TODO: But we see that later on sometimes a new resource is still added.
                    // That is when the assertTrue(resources.size() <= maxConnectionPoolSize, "failed, size=" + resources.size()); fails.
                    System.out.println("Adding resource: " + resource);
                } else {
                    System.out.println("Reusing resource: " + resource);
                }

                // Keep resource a bit occupied, to ensure more than 1 resource from the
                // pool is used at the same time
                Thread.sleep(0, 10);
                
                // Return resource, this also eventually calls connectionPool.notifyWaitingThreads(), 
                // which allows waiting threads to continue when a resource is available. 
                connectionPool.resourceClosed(resource);
                
                // Cannot check the resource result here, it will already be re-used by another thread!
                // assertResourceIsNotBusy(resource);
                
                numberOfThreadsFinished.incrementAndGet();
                return null;
            });
        }
        runTheTasks(tasks);
        assertResourcesSize(maxConnectionPoolSize);
        connectionPool.emptyPool();
        assertResourcesSize(0);
        
        // Just another proof that all 1000 threads finished, just to show unstable problem below:
        assertEquals(taskCount, numberOfThreadsFinished.get());
        
        // No new resources are being used, check the remaining ones for the state.
        for (ResourceHandle resource : resources) {
            assertResourceIsNotBusy(resource);
        }
        
        // Validate there are no more resources created, than the maximum pool size, because
        // they should be reused
        // TODO: This fails: resources size is sometimes 34 and sometimes 50. 
        assertTrue(resources.size() <= maxConnectionPoolSize, "failed, size=" + resources.size());
    }

    @Test
    @Timeout(value = 10)
    void resourceErrorOccurredMultiThreadedTest() throws Exception {
        int maxConnectionPoolSize = 50;
        
        // Let a Thread wait for a maximum of 1 second. Within 1 second 
        // all threads should have received a resource from the pool of 50.
        final int maxWaitTimeInMillis = 500;

        // Use poolResizeQuantity 1, to have the easiest understanding and predictability of the test
        final int poolResizeQuantity = 1;

        createConnectionPool(maxConnectionPoolSize, maxWaitTimeInMillis, poolResizeQuantity);

        ResourceAllocator alloc = new LocalTxConnectorAllocator(null, managedConnectionFactory, resourceSpec, null,
                null, null, null, false);

        // Keep track of resources and number of tasks executed
        final Set<ResourceHandle> resources = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger numberOfThreadsFinished = new AtomicInteger();
        
        // Make sure there are no resources in the pool before we start processing tasks
        assertResourcesSize(0);
        
        int taskCount = 1000;
        List<Callable<Void>> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            tasks.add(() -> {
                // Get resource. PoolingException should only be possible if the maxWaitTimeInMillis is too low for the amount of tasks.
                ResourceHandle resource = connectionPool.getResource(resourceSpec, alloc, javaEETransaction);
                assertNotNull(resource);
                assertResourceIsBusy(resource);
                
                // Keep track of unique resources, we want to validate if resources are NOT being reused
                // because the resource is marked "error occurred", we expect it to be replaced with a new resource.
                boolean isNewlyAdded = resources.add(resource);
                if (isNewlyAdded) {
                    System.out.println("Add resource: " + resource);
                } else {
                    System.err.println("Add resource failed for: " + resource);
                    fail("Resource may not be reused in this test, we only return error occurred resources");
                }

                // No need to keep resource busy, since we will return it as failed and may not be reused again

                // Return resource
                connectionPool.resourceErrorOccurred(resource); 
                // TODO -> notifyWaitingThreads() is not called by resourceErrorOccurred?! is this a bug? I think it is.
                // while notifyWaitingThreads() is called via connectionPool.resourceClosed().
                // Other waiting threads will fail while they could just receive a new clean resource. 
                // As shown with this test when you leave out the next line:
                connectionPool.notifyWaitingThreads();
                                
                // We can test the resource results, because the resource should not be reused by another thread
                assertResourceIsNotBusy(resource);
              
                numberOfThreadsFinished.incrementAndGet();
                return null;
            });
        }
        runTheTasks(tasks);

        connectionPool.emptyPool();
        assertResourcesSize(0);

        // Just another proof that all 1000 threads finished, just to show unstable problem below:
        assertEquals(taskCount, numberOfThreadsFinished.get());
        // Failed connections should not be reused in the connection pool.
        // New resources should have been created. Since this tests returns all resources using resourceErrorOccurred
        // the amount must be equal to the number of threads started.
        // TODO: This test does not seem to be stable, sometimes 999 resources are in the Set. BUG ?!?!!
        assertEquals(taskCount, resources.size());
    }

    private void runTheTasks(List<Callable<Void>> tasks) throws Exception {
        ExecutorService threadPool = Executors.newFixedThreadPool(1000);
        List<Future<Void>> futures = threadPool.invokeAll(tasks, 30, TimeUnit.SECONDS);
        assertAll(
                () -> assertTrue(futures.stream().allMatch(f -> f.isDone()), "All done."),
                () -> assertFalse(futures.stream().anyMatch(f -> f.isCancelled()), "None cancelled."));
        
        // Call get() to see if there was any assertion error in the thread execution, this call 
        // forces the ExecutionException to be thrown, if the Callable execution failed.
        for (Future future : futures) {
            future.get();
        }
    }

    private void assertResourceIsBusy(ResourceHandle resource) {
        // Resource must be marked as isBusy right after call to getResource
        ResourceState resourceState = resource.getResourceState();
        assertTrue(resourceState.isBusy());
        assertFalse(resourceState.isFree());
    }

    private void assertResourceIsNotBusy(ResourceHandle resource) {
        // Resource must be marked as not isBusy / isFree right after call to resourceClosed
        ResourceState resourceState = resource.getResourceState();
        assertTrue(resourceState.isFree(), "assertResourceIsNotBusy - Expected isFree for resource" + resource);
        assertFalse(resourceState.isBusy(), "assertResourceIsNotBusy - Expected !isBusy for resource" + resource);
    }

    private void assertResourcesSize(int expectedSize) {
        DataStructure dataStructure = (DataStructure) connectionPool.dataStructure;
        assertEquals(expectedSize, dataStructure.getResourcesSize());
        assertEquals(expectedSize, dataStructure.getAllResources()
                .size());
    }

    public static class MyConnectionPool extends ConnectionPool {

        public static int myMaxPoolSize;
        public static int maxWaitTimeInMillis;
        public static int poolResizeQuantity;

        public MyConnectionPool(PoolInfo poolInfo) throws PoolingException {
            super(ConnectionPoolTest.getPoolInfo(), new Hashtable<>());
        }

        @Override
        protected ConnectorConnectionPool getPoolConfigurationFromJndi(Hashtable env) throws PoolingException {
            // Note: this Util has other defaults than the real pool.
            // Example: maxWaitTimeInMillis is: 7889, while "max-wait-time-in-millis" is documented as 60.000ms
            ConnectorConnectionPool connectorConnectionPool = ConnectionPoolObjectsUtils
                    .createDefaultConnectorPoolObject(poolInfo, null);

            // Override some defaults
            connectorConnectionPool.setSteadyPoolSize("0");
            connectorConnectionPool.setMaxPoolSize("" + myMaxPoolSize);
            connectorConnectionPool.setMaxWaitTimeInMillis("" + maxWaitTimeInMillis);
            connectorConnectionPool.setPoolResizeQuantity("" + poolResizeQuantity);

            return connectorConnectionPool;
        }
    }

    public class MyConnectorRuntime extends ConnectorRuntime {

        public void setProcessEnvironment(ProcessEnvironment processEnvironment) {
            this.processEnvironment = processEnvironment;
        }

        @Override
        public PoolType getPoolType(PoolInfo poolInfo) throws ConnectorRuntimeException {
            return PoolType.STANDARD_POOL;
        }

        @Override
        public ConnectorDescriptor getConnectorDescriptor(String rarName) throws ConnectorRuntimeException {
            throw new ConnectorRuntimeException("No rar in unit test");
        }

        @Override
        public DelegatingClassLoader getConnectorClassLoader() {
            // Return null, system classloader will be used
            return null;
        }
    }

    private static PoolInfo getPoolInfo() {
        SimpleJndiName jndiName = new SimpleJndiName("myPool");
        return new PoolInfo(jndiName);
    }

}
