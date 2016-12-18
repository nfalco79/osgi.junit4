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
package com.github.nfalco79.junit4osgi.runner.internal;

/**
 * Utility class
 *
 * @author Nikolas Falco
 */
import org.junit.Test;
import org.junit.runners.model.TestClass;

import junit.framework.TestCase;

/*package*/ final class JUnitUtils {

	private JUnitUtils() {
	}

	/**
	 * Return if the given class is a valid JUnit 3/4 class and contains tests.
	 *
	 * @return {@code true} is a JUnit class that contains test method,
	 *         {@code false} otherwise.
	 */
	public static boolean hasTests(Class<?> testClass) {
		return testClass != null && (TestCase.class.isAssignableFrom(testClass)
				|| !new TestClass(testClass).getAnnotatedMethods(Test.class).isEmpty());
	}

}
