package edu.iu.type;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ComponentVersionTest {

	@Test
	public void testSpecMeetsSpec() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");

		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("");

		assertTrue(version.meets(requiredVersion));
	}

	@Test
	public void testImplementationMeetsImplementation() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("");
		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("");
		when(requiredVersion.implementationVersion()).thenReturn("");
		assertTrue(version.meets(requiredVersion));
	}

	@Test
	public void testNamesMustMatch() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("a");
		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("b");
		assertFalse(version.meets(requiredVersion));
	}

	@Test
	public void testMajorMustMatch() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.major()).thenReturn(1);
		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("");
		assertFalse(version.meets(requiredVersion));
	}

	@Test
	public void testMinorMayBeGreater() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.minor()).thenReturn(1);
		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("");
		assertTrue(version.meets(requiredVersion));
	}

	@Test
	public void testMinorMustNotBeLess() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		var requiredVersion = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(requiredVersion.name()).thenReturn("");
		when(requiredVersion.minor()).thenReturn(1);
		assertFalse(version.meets(requiredVersion));
	}

	@Test
	public void testNamesUseNaturalOrder() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("a");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("b");
		assertTrue(version.compareTo(version2) < 0);
	}

	@Test
	public void testImplementationVersionsMatch() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("");
		assertTrue(version.compareTo(version2) == 0);
	}

	@Test
	public void testMajorUsesNumericOrder() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.major()).thenReturn(12);
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.major()).thenReturn(2);
		assertTrue(version.compareTo(version2) > 0);
	}

	@Test
	public void testMinorUsesNumericOrder() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.minor()).thenReturn(23);
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.minor()).thenReturn(3);
		assertTrue(version.compareTo(version2) > 0);
	}

	@Test
	public void testSpecsMatch() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		assertTrue(version.compareTo(version2) == 0);
	}

	@Test
	public void testSpecIsLessThanImpl() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("");
		assertTrue(version.compareTo(version2) < 0);
	}

	@Test
	public void testImplIsLessThanSpec() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		assertTrue(version.compareTo(version2) > 0);
	}

	@Test
	public void testPatchUsesNumericOrder() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("1.2.34");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("1.2.4");
		assertTrue(version.compareTo(version2) > 0);
	}

	@Test
	public void testPatchNeedsValueVersionsLeft() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("1.2");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("1.2.4");
		assertThrows(IllegalStateException.class, () -> version.compareTo(version2));
	}

	@Test
	public void testPatchNeedsValueVersionsRight() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("1.2.34");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("1.2");
		assertThrows(IllegalStateException.class, () -> version.compareTo(version2));
	}

	@Test
	public void testNaturalOrder() {
		var version = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version.name()).thenReturn("");
		when(version.implementationVersion()).thenReturn("1.2.34-SNAPSHOT");
		var version2 = IuTest.mockWithDefaults(IuComponentVersion.class);
		when(version2.name()).thenReturn("");
		when(version2.implementationVersion()).thenReturn("1.2.34+build.5");
		assertTrue(version.compareTo(version2) > 0);
	}

}
