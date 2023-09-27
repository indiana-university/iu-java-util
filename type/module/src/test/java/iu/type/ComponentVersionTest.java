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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ThreadLocalRandom;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentVersionTest {

	@Test
	public void testSpecificationVersion() {
		var version = new ComponentVersion("", 1, 0);
		assertEquals("", version.name());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testSpecMustUseNonNegativeMajor() {
		assertEquals("Component major version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("", -1, 0)).getMessage());
	}

	@Test
	public void testSpecMustUseNonNegativeMinor() {
		assertEquals("Component minor version number must be non-negative",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("", 0, -1)).getMessage());
	}

	@Test
	public void testSpecRequiesName() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion(null, 0, 0));
	}

	@Test
	public void testImplRequiesName() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion(null, (String) null));
	}

	@Test
	public void testImplRequiresVersion() {
		assertThrows(NullPointerException.class, () -> new ComponentVersion("", (String) null));
	}

	@Test
	public void testImplRequiresSemanticVersion() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", ""))
						.getMessage());
	}

	@Test
	public void testImplRequiresNonNegativeMajor() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", "-1.0.0"))
						.getMessage());
	}

	@Test
	public void testImplRequiresNonNegativeMinor() {
		assertEquals("Invalid version for my-component, must be a valid semantic version",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my-component", "0.-1.0"))
						.getMessage());
	}

	@Test
	public void testImplExtractsMajorAndMinor() {
		var version = new ComponentVersion("", "1.0.2");
		assertEquals("", version.name());
		assertEquals(1, version.major());
		assertEquals(0, version.minor());
	}

	@Test
	public void testDepRequiresName() {
		var attr = new Manifest().getMainAttributes();
		assertEquals("Missing my_dependency-api-Extension-Name in META-INF/MANIFEST.MF main attributes",
				assertThrows(IllegalArgumentException.class, () -> new ComponentVersion("my.dependency-api", attr))
						.getMessage());
	}

}
