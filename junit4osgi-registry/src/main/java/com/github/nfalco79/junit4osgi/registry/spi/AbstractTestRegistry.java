/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.nfalco79.junit4osgi.registry.spi;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import com.github.nfalco79.junit4osgi.registry.internal.JUnit4BundleListener;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistryEvent.TestRegistryEventType;

public abstract class AbstractTestRegistry implements TestRegistry {

	private LogService log;
	private JUnit4BundleListener bundleListener;

	protected final Set<TestRegistryChangeListener> listeners = new CopyOnWriteArraySet<TestRegistryChangeListener>();
	protected final Map<Bundle, Set<TestBean>> tests = new ConcurrentHashMap<Bundle, Set<TestBean>>();

	protected LogService getLog() {
		return log;
	}

	protected void setLog(LogService log) {
		this.log = log;
	}

	protected void activate(BundleContext bundleContext) {
		bundleListener = new JUnit4BundleListener(this);
		bundleContext.addBundleListener(bundleListener);
		// parse current bundles
		for (Bundle bundle : bundleContext.getBundles()) {
			bundleListener.addBundle(bundle);
		}
	}

	protected void deactivate(BundleContext bundleContext) {
		try {
			bundleContext.removeBundleListener(bundleListener);
		} finally {
			dispose();
		}
	}

	protected void fireEvent(TestRegistryEvent event) {
		for (TestRegistryChangeListener listener : listeners) {
			try {
				listener.registryChanged(event);
			} catch (Exception t) {
				getLog().log(LogService.LOG_INFO, "Listener " + listener.getClass() //
					+ " fails on event " + event.getType() //
					+ " for the test " + event.getTest().getId());
			}
		}
	}

	@Override
	public void dispose() {
		tests.clear();
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.registry.TestRegistry#addTestListener(com.github.nfalco79.junit4osgi.registry.TestRegistryListener)
	 */
	@Override
	public void addTestRegistryListener(TestRegistryChangeListener listener) {
		if (listener == null) {
			throw new NullPointerException("Cannot add a null listener");
		}
		listeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.registry.TestRegistry#removeTestListener(com.github.nfalco79.junit4osgi.registry.TestRegistryListener)
	 */
	@Override
	public void removeTestRegistryListener(TestRegistryChangeListener listener) {
		if (listener == null) {
			throw new NullPointerException("Cannot remove a null listener");
		}
		listeners.remove(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.registry.spi.TestRegistry#removeTests(org.osgi.framework.Bundle)
	 */
	@Override
	public void removeTests(Bundle contributor) {
		Set<TestBean> bundleTests = tests.remove(contributor);
		if (bundleTests != null) {
			for (TestBean test : bundleTests) {
				fireEvent(new TestRegistryEvent(TestRegistryEventType.REMOVE, test));
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.registry.TestRegistry#getTests()
	 */
	@Override
	public Set<TestBean> getTests() {
		Set<TestBean> allTests = new LinkedHashSet<TestBean>();
		for (Set<TestBean> foo : tests.values()) {
			allTests.addAll(foo);
		}
		return Collections.unmodifiableSet(allTests);
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.registry.spi.TestRegistry#getTests(java.lang.String[])
	 */
	@Override
	public Set<TestBean> getTests(String[] testIds) {
		Set<TestBean> testBucket = new LinkedHashSet<TestBean>();

		if (testIds != null) {
			for (Set<TestBean> bundleTests : tests.values()) {
				for (TestBean test : bundleTests) {
					for (String testId : testIds) {
						if (testId.equals(test.getId())) {
							testBucket.add(test);
							break;
						}
					}
				}
			}
		}
		return testBucket;
	}

}