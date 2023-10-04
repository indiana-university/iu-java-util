/*
 * Copyright © 2023 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ComponentVersionTest {

	private IuComponentVersion createVersion(String name, String impl, int major, int minor) {
		return new IuComponentVersion() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public int major() {
				return major;
			}

			@Override
			public int minor() {
				return minor;
			}

			@Override
			public String implementationVersion() {
				return impl;
			}
		};
	}

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

	@Test
	public void testSpecVersion() {
		var version = createVersion("a", "1.2.3", 1, 2);
		var specVersion = version.specificationVersion();
		assertEquals("a", specVersion.name());
		assertNull(specVersion.implementationVersion());
		assertEquals(1, specVersion.major());
		assertEquals(2, specVersion.minor());
		assertSame(specVersion, specVersion.specificationVersion());
		assertEquals(specVersion, specVersion.specificationVersion());
		assertEquals(specVersion, version.specificationVersion());
		assertNotEquals(specVersion, null);
		assertNotEquals(specVersion, this);
		assertNotEquals(specVersion, version);
		assertNotEquals(specVersion, createVersion("b", null, 1, 2));
		assertNotEquals(specVersion, createVersion("a", null, 2, 2));
		assertNotEquals(specVersion, createVersion("a", null, 1, 0));
		assertEquals(specVersion.hashCode(), version.specificationVersion().hashCode());
		assertEquals("a-1.2+", version.specificationVersion().toString());
	}

}
