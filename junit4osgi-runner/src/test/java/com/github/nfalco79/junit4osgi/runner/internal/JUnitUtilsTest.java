package com.github.nfalco79.junit4osgi.runner.internal;

import static org.junit.Assert.*;

import org.example.AbstractTest;
import org.example.ITest;
import org.example.JUnit3Test;
import org.example.MainClassTest;
import org.example.PackageRetrieverUtils;
import org.example.SimpleTestCase;
import org.junit.Test;


public class JUnitUtilsTest {

	@Test
	public void main_class_does_not_contains_tests() throws Exception {
		assertFalse("this main does not contains tests", JUnitUtils.hasTests(MainClassTest.class));
	}

	@Test
	public void junit3_class_has_tests() throws Exception {
		assertTrue("this class extends TestCase so it contains tests for sure", JUnitUtils.hasTests(JUnit3Test.class));
	}

	@Test
	public void junit4_class_has_tests() throws Exception {
		assertTrue("this class has annotated method with @Test so has tests for sure", JUnitUtils.hasTests(SimpleTestCase.class));
	}

	@Test
	public void abstract_classes_are_skipped() throws Exception {
		assertFalse("this class is abstract and must be skipped", JUnitUtils.isValid(AbstractTest.class));
	}

	@Test
	public void interface_classes_are_skipped() throws Exception {
		assertFalse("this class is an interface and must be skipped", JUnitUtils.isValid(ITest.class));
	}

	@Test
	public void not_public_classes_are_skipped() throws Exception {
		assertFalse("this class has not public visibility and must be skipped", JUnitUtils.isValid(PackageRetrieverUtils.getPackageTestClass()));
	}

}