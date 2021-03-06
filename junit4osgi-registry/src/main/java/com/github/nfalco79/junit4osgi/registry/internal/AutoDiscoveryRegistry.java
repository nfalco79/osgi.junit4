/*
 * Copyright 2017 Nikolas Falco
 * Licensed under the Apache License, Version 2.0 (the
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
package com.github.nfalco79.junit4osgi.registry.internal;

import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.osgi.framework.Bundle;

import com.github.nfalco79.junit4osgi.registry.internal.asm.BundleTestClassVisitor;
import com.github.nfalco79.junit4osgi.registry.spi.AbstractTestRegistry;
import com.github.nfalco79.junit4osgi.registry.spi.TestBean;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistryEvent;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistryEvent.TestRegistryEventType;
import com.j256.simplejmx.common.JmxAttributeMethod;
import com.j256.simplejmx.common.JmxOperation;
import com.j256.simplejmx.common.JmxResource;

@JmxResource(domainName = "org.osgi.junit4osgi", folderNames = "type=registry", beanName = "AutoDiscoveryRegistry", description = "The JUnit4 registry that discovers test using the same maven surefure test naming convention")
public final class AutoDiscoveryRegistry extends AbstractTestRegistry {

	private interface EntryPathsVisitor {
		void visit(String entryPath);
		boolean accept(String entryPath);
	}

	private static final int EXT_LENGHT = ".class".length();

	@JmxOperation(description = "Dispose the registry")
	@Override
	public void dispose() {
		super.dispose();
	}

	@JmxAttributeMethod(description = "Returns a list of all tests id in the registry")
	public String[] getTestIds() {
		Set<String> allTests = new LinkedHashSet<String>();
		for (Set<TestBean> bundleTests : tests.values()) {
			for (TestBean test : bundleTests) {
				allTests.add(test.getId());
			}
		}
		return allTests.toArray(new String[allTests.size()]);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.github.nfalco79.junit4osgi.registry.spi.TestRegistry#registerTests(
	 * org.osgi.framework.Bundle)
	 */
	@Override
	public void registerTests(final Bundle bundle) {
		if (tests.containsKey(bundle)) {
			return;
		}

		final Set<TestBean> bundleTest = new LinkedHashSet<TestBean>();
		tests.put(bundle, bundleTest);

		navigate(bundle, "/", new EntryPathsVisitor() {
			@Override
			public void visit(String entryPath) {
				String className = toClassName(entryPath);
				String simpleClassName = toClassSimpleName(className);
				if (isTestCase(simpleClassName) || isIntegrationTest(simpleClassName)) {
					TestBean bean = new TestBean(bundle, className);

					BundleTestClassVisitor visitor = new BundleTestClassVisitor(bundle);
					visitor.setLog(getLog());

					if (isTestClass(bundle, bean, visitor)) {
						bundleTest.add(bean);

						fireEvent(new TestRegistryEvent(TestRegistryEventType.ADD, bean));
					}
				}
			}

			@Override
			public boolean accept(String entryPath) {
				return entryPath.endsWith(".class");
			}
		});
	}

	private void navigate(Bundle bundle, String path, EntryPathsVisitor visitor) {
		Enumeration<String> entries = bundle.getEntryPaths(path);
		while (entries != null && entries.hasMoreElements()) {
			String entry = entries.nextElement();
			if (visitor.accept(entry)) {
				visitor.visit(entry);
			} else {
				navigate(bundle, entry, visitor);
			}
		}

	}

	private String toClassName(final String entry) {
		String className = entry;
		if (className.startsWith("/")) {
			className = className.substring(1);
		}
		className = className.substring(0, className.length() - EXT_LENGHT);
		return className.replace('/', '.').replace('/', '.');
	}

	private String toClassSimpleName(final String className) {
		int idxInnerClass = className.lastIndexOf('$');
		if (idxInnerClass != -1) {
			return className.substring(idxInnerClass + 1);
		} else {
			return className.substring(className.lastIndexOf('.') + 1);
		}
	}

	private boolean isIntegrationTest(final String className) {
		return className.startsWith("IT") || className.endsWith("IT") || className.endsWith("ITCase");
	}

	private boolean isTestCase(final String className) {
		return className.startsWith("Test") || className.endsWith("Test") || className.endsWith("Tests")
				|| className.endsWith("TestCase");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.github.nfalco79.junit4osgi.registry.spi.TestRegistry#removeTests(org.
	 * osgi.framework.Bundle)
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

}