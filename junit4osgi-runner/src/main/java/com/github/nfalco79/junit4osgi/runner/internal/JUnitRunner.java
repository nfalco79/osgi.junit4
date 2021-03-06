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
package com.github.nfalco79.junit4osgi.runner.internal;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.osgi.service.log.LogService;

import com.github.nfalco79.junit4osgi.registry.TestRegistryUtils;
import com.github.nfalco79.junit4osgi.registry.spi.TestBean;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistry;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistryChangeListener;
import com.github.nfalco79.junit4osgi.registry.spi.TestRegistryEvent;
import com.github.nfalco79.junit4osgi.runner.internal.jmx.JMXServer;
import com.github.nfalco79.junit4osgi.runner.spi.TestRunner;
import com.github.nfalco79.junit4osgi.runner.spi.TestRunnerNotifier;
import com.j256.simplejmx.common.JmxAttributeMethod;
import com.j256.simplejmx.common.JmxOperation;
import com.j256.simplejmx.common.JmxOperationInfo.OperationAction;
import com.j256.simplejmx.common.JmxResource;

@JmxResource(domainName = "org.osgi.junit4osgi", folderNames = "type=runner", beanName = "JUnitRunner", description = "The JUnit4 runner, executes JUnit3/4 test case in any OSGi bundle in the current system")
public class JUnitRunner implements TestRunner {
	private final class QueeueTestListener implements TestRegistryChangeListener {
		private final Queue<TestBean> tests;

		private QueeueTestListener(Queue<TestBean> tests) {
			this.tests = tests;
		}

		@Override
		public void registryChanged(TestRegistryEvent event) {
			TestBean testBean = event.getTest();
			if (testBean == null) {
				throw new IllegalArgumentException("event has a null test bean");
			}
			switch (event.getType()) {
			case ADD:
		        tests.add(testBean);
				break;
			case REMOVE:
				tests.remove(testBean);
				break;
			default:
				logger.log(LogService.LOG_WARNING, "Test registry event type " + event.getType() + " not supported");
				break;
			}
		}
	}

	/**
	 * Guide which kind of JUnit registry this runner have to use. Default is auto(discovery).
	 */
	public static final String RUNNER_REGISTY = "org.osgi.junit.runner.registry";
	/**
	 * This property when set to true start this runner continually. This bundle
	 * listen every time a bundle is started and new tests are found than those test
	 * are executed.
	 */
	public static final String RUNNER_AUTOSTART = "org.osgi.junit.runner.autostart";
	/**
	 * The path on disk where same the Surefire XML reports.
	 */
	public static final String REPORT_PATH = "org.osgi.junit.reportsPath";
	/**
	 * When a test case fails will be re run n-times how many are specified by this property.
	 */
	public static final String RERUN_COUNT = "org.osgi.junit.rerunFailingTestsCount";
	/**
	 * A space or comma separate list of ant glob include patterns against each test
	 * suite name have to matches.
	 */
	public static final String PATH_INCLUDES = "org.osgi.junit.include";
	/**
	 * A space or comma separate list of ant glob exclude patterns against each test
	 * suite name have to matches.
	 */
	public static final String PATH_EXCLUDE = "org.osgi.junit.exclude";

	private static final String DEFAULT_PATH_EXCLUDE = "junit.extensions.*";

	private TestRegistry registry;
	private boolean stop;
	private boolean running;
	LogService logger;
	private TestRegistryChangeListener testListener;
	private ScheduledThreadPoolExecutor executor;
	private Integer reRunCount;
	private final File defaultReportsDirectory;
	private final TestFilter testFilter;
	private final AtomicInteger testCount = new AtomicInteger(0);

	public JUnitRunner() {
		defaultReportsDirectory = new File(System.getProperty(REPORT_PATH, "surefire-reports"));
		reRunCount = Integer.getInteger(RERUN_COUNT, 0);
		stop = true;

		String excludes = System.getProperty(PATH_EXCLUDE, DEFAULT_PATH_EXCLUDE).trim();
		if (!DEFAULT_PATH_EXCLUDE.equals(excludes)) {
		    excludes += "," + DEFAULT_PATH_EXCLUDE;
		}
        testFilter = new TestFilter(System.getProperty(PATH_INCLUDES), excludes);
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#setRegistry(com.github.nfalco79.junit4osgi.registry.spi.TestRegistry)
	 */
	@Override
	public void setRegistry(final TestRegistry registry) {
		bindRegistry(registry, null);
	}

	/**
	 * Binds the registry service reference.
	 *
	 * @param registry
	 *            an implementation of {@link TestRegistry}
	 * @param properties
	 *            component declaration properties registered for the given
	 *            registry instance.
	 */
	public void bindRegistry(final TestRegistry registry, final Map<String, Object> properties) {
		final String defaultRegistry = System.getProperty(RUNNER_REGISTY, "auto");

		if (properties == null || defaultRegistry.equals(properties.get("discovery"))) {
			this.registry = registry;
			jmxServer.register(registry);
		}
	}

	/**
	 * Remove binds of the registry service instance if matches the current.
	 *
	 * @param registry
	 *            the implementation of {@link TestRegistry} that is being
	 *            disabled.
	 */
	public void unbindRegistry(final TestRegistry registry) {
		if (this.registry == registry) {
			this.registry = null;
			jmxServer.unregister(registry);
		}
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#setLog(org.osgi.service.log.LogService)
	 */
	@Override
	public void setLog(final LogService logger) {
		this.logger = logger;
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#start()
	 */
	@JmxOperation(description = "Start the runner that execute all test cases in the JUnit registry. " //
			+ "If a new bundle that contains test cases is installed in the system, tests are executed immedialty.", //
			operationAction = OperationAction.ACTION)
	@Override
	public void start() {
		start((String[]) null, null, null);
	}

	@JmxOperation(description = "Start all tests that matches the given patterns", //
	        operationAction = OperationAction.ACTION, //
	        parameterNames = { "includePatterns", "excludePatterns", "reportsPath" }, //
	        parameterDescriptions = { "includePatterns", "excludePatterns", "reportsPath" })
    public void start(String includePatterns, String excludePatterns, String reportsPath) {
        // collect all tests in the registry that matches patterns
        TestFilter filter = new TestFilter(includePatterns, excludePatterns);

        Set<String> filteredTests = new LinkedHashSet<String>();
        for (TestBean test : registry.getTests()) {
            if (filter.accept(test.getName())) {
                filteredTests.add(test.getId());
            }
        }
        start(filteredTests.toArray(new String[0]), reportsPath, null);
    }

	@JmxOperation(description = "Executes tests with the specified id collected by the JUnit registry", //
			operationAction = OperationAction.ACTION, //
	        parameterNames = { "testIds", "reportsPath" }, //
			parameterDescriptions = { //
					"an array of test ids to execute, a test id is composed by <bundle symbolic name>@<FQN of test class>", //
					"path on disk where save surefire reports" })
	public void start(String[] testIds, String reportsPath) {
	    start(testIds, reportsPath, null);
	}

	@Override
	public void start(String[] testIds, String reportsPath, TestRunnerNotifier notifier) {
		if (logger == null || registry == null) {
			return;
		}

		final File reportsDirectory;
		if (reportsPath != null) {
			reportsDirectory = new File(reportsPath);
		} else {
			reportsDirectory = defaultReportsDirectory;
		}

		if (!isRunning()) {
			final Queue<TestBean> tests;
			if (testIds == null) {
				// create a queue collecting all registry tests
				tests = new FilteredTestQueue(testFilter);
				testListener = new QueeueTestListener(tests);
				registry.addTestRegistryListener(testListener);

				tests.addAll(registry.getTests());
			} else {
				// create a queue with only the specified tests
				tests = new ArrayDeque<TestBean>(registry.getTests(testIds));
			}

			stop = false;
			running = true;
			executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
				@Override
				public Thread newThread(Runnable runnable) {
					return new Thread(runnable, "JUnitRunner-executor");
				}
			});

			if (testIds != null) {
				Runnable testRunnable = getSingleRunnable(reportsDirectory, tests, notifier);
				executor.schedule(testRunnable, 0l, TimeUnit.MILLISECONDS);
			} else {
			    Runnable testRunnable = getInfiniteRunnable(reportsDirectory, tests);
				executor.scheduleAtFixedRate(testRunnable, 0l, getRepeatTime(), TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * For test purpose only
	 *
	 * @return the delay time before reschedule the job runner
	 */
	protected long getRepeatTime() {
		return 5000l;
	}

	protected Runnable getSingleRunnable(final File reportsDirectory, final Queue<TestBean> tests, final TestRunnerNotifier notifier) {
		return getTestRunnable(reportsDirectory, tests, notifier, true);
	}

	protected Runnable getInfiniteRunnable(final File reportsDirectory, final Queue<TestBean> tests) {
        return getTestRunnable(reportsDirectory, tests, null, false);
	}

	private Runnable getTestRunnable(final File reportsDirectory, final Queue<TestBean> tests, final TestRunnerNotifier notifier, final boolean singleRun) {
		final TestRunnerNotifier safeNotifier = new SafeTestRunnerNotifier(notifier, logger);

		return new Runnable() {
			@Override
			public void run() {
				if (tests.isEmpty()) {
				    testCount.set(0);
					return;
				}

				try {
					safeNotifier.start();
					runTests(tests, reportsDirectory, safeNotifier);
				} finally {
					if (singleRun) {
						running = false;
						stop = true;
					}
					safeNotifier.stop();
				}
			}
		};
	}

	private void runTests(final Queue<TestBean> tests, final File reportsDirectory, TestRunnerNotifier notifier) {
		TestBean testBean;
		try {
			RunListener customListener = null;
			ReportListener reportListener = null;
			JUnitCore core = new JUnitCore();

			while (!isStopped() && (testBean = tests.poll()) != null) {
                testCount.set(tests.size());
				try {
					Class<?> testClass = testBean.getTestClass();
					if (!TestRegistryUtils.isValidTestClass(testClass)) {
					    logger.log(LogService.LOG_DEBUG, "Skip class " + testBean.getName());
						continue;
					}

					// initialise the report listener
					reportListener = new ReportListener();
					core.addListener(reportListener);

					customListener = notifier.getRunListener();
					if (customListener != null) {
						core.addListener(customListener);
					}

					logger.log(LogService.LOG_INFO, "Running test " + testBean.getId());
					Request request = Request.classes(testClass);
					Result result = core.run(request);

					if (isRerunFailingTests() && !result.wasSuccessful()) {
						rerunTests(core, reportListener);
					}

					// write test result
					final XMLReport xmlReport = new XMLReport(reportsDirectory);
					xmlReport.generateReport(reportListener.getReport());
				} catch (ClassNotFoundException e) {
					logger.log(LogService.LOG_ERROR, "Cannot load class " + testBean.getId(), e);
				} catch (NoClassDefFoundError e) {
					logger.log(LogService.LOG_ERROR, "Cannot load class " + testBean.getId(), e);
				} finally {
					if (customListener != null) {
						core.removeListener(customListener);
					}
					if (reportListener != null) {
						core.removeListener(reportListener);
					}
				}
			}

			logger.log(LogService.LOG_INFO, "All tests in the queue has been processed");
		} catch (Exception e) {
			logger.log(LogService.LOG_ERROR, null, e);
		}
	}

	protected void rerunTests(final JUnitCore core, final ReportListener listener) {
		// remove the report listener in case of rerun, will be
		// used a custom listener to avoid reset statistics
		core.removeListener(listener);

		RerunListenerWrapper reportListener = new RerunListenerWrapper(listener);
		try {
			core.addListener(reportListener);

			Collection<Description> failedTests = listener.getFailures();
			for (Description test : failedTests) {
				/*
				 * a suite descriptor does not have a class valued, the
				 * method getTestClass does not work in OSGi and JUnit does
				 * not provide a way to specify the classloader to use so
				 * the lookup for a valid test class in child descriptors
				 */
				if (!test.isTest()) {
					continue;
				}
				Class<?> testClass = test.getTestClass();
				if (testClass == null) {
					logger.log(LogService.LOG_INFO, "Skip rerun of test : " + test.getClassName() + "." + test.getMethodName());
					continue;
				}

				int runCount = reRunCount;
				Result rerunResult = null;
				while (runCount > 0 && (rerunResult == null || !rerunResult.wasSuccessful())) {
					Request request = Request.classes(testClass).filterWith(test);
					runCount--;
					rerunResult = core.run(request);
				}
			}
		} finally {
			core.removeListener(reportListener);
			core.addListener(listener);
		}
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#stop()
	 */
	@Override
	@JmxOperation(description = "Stop any active runner", operationAction = OperationAction.ACTION)
	public void stop() {
		stop = true;
		if (registry != null && testListener != null) {
			registry.removeTestRegistryListener(testListener);
		}
		if (executor != null) {
		    running = false;
			executor.shutdownNow();
		}
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#isStopped()
	 */
	@Override
	@JmxAttributeMethod(description = "Returns if the runner is stopped or is plan to be stopped")
	public boolean isStopped() {
		return stop;
	}

	/* (non-Javadoc)
	 * @see com.github.nfalco79.junit4osgi.runner.internal.TestRunner#isRunning()
	 */
	@Override
	@JmxAttributeMethod(description = "Returns the actual state of JUnit runner")
	public boolean isRunning() {
		return running;
	}

	@JmxAttributeMethod(description = "Returns the not filtered count of tests case ready to be executed")
	public int getTestCount() {
	    return testCount.get();
	}

	public boolean accept(Class<?> testClass) {
	    return testFilter.accept(testClass.getName());
	}

	public void setRerunFailingTests(Integer count) {
		this.reRunCount = count;
	}

	public boolean isRerunFailingTests() {
		return reRunCount > 0;
	}

	private JMXServer jmxServer = newJMXServer();

	protected JMXServer newJMXServer() {
		return new JMXServer();
	}

	public void activate() {
		jmxServer.start();
		jmxServer.register(this);

		if (Boolean.getBoolean(RUNNER_AUTOSTART)) {
			start();
		}
	}

	protected JMXServer getJMXServer() {
		return jmxServer;
	}

	public void deactivate() {
		stop();

		jmxServer.unregister(this);
		jmxServer.unregister(registry);
		jmxServer.stop();
	}
}