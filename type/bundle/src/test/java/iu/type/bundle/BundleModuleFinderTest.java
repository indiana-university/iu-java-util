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
package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuStream;

@SuppressWarnings("javadoc")
public class BundleModuleFinderTest {

	private String resourceName;
	private BundleModuleFinder finder;

	@BeforeEach
	public void setup() {
		resourceName = BundleModuleFinder.class.getName().replace('.', '/') + ".class";
		final var resourceUrl = BundleModuleFinder.class.getClassLoader().getResource(resourceName).toString();
		assertTrue(resourceUrl.endsWith(resourceName), resourceUrl + " " + resourceName);
		final var resourceRoot = Path
				.of(URI.create(resourceUrl.toString().substring(0, resourceUrl.length() - resourceName.length())));
		finder = new BundleModuleFinder(resourceRoot);
	}

	@AfterEach
	public void teardown() throws IOException {
		finder.close();
	}

	@Test
	public void testModuleNames() throws IOException {
		Set<String> names = new HashSet<>();
		finder.findAll().forEach(r -> names.add(r.descriptor().name()));
		assertTrue(names.remove("iu.util.type.bundle"), names.toString());
		assertTrue(names.isEmpty(), names.toString());
	}

	@Test
	public void testReadsAndCloses() throws IOException {
		final var ref = finder.find("iu.util.type.bundle").get();
		try (var reader = ref.open()) {
			assertSame(reader, ref.open());
			var url = reader.find(resourceName).get().toURL();
			var c = url.openConnection();
			c.setUseCaches(false);
			try (var in = c.getInputStream()) {
				IuStream.read(in);
			}
		}
		finder.close(); // extra call should not throw
		assertThrows(IllegalStateException.class, () -> ref.open());
		assertThrows(IllegalStateException.class, () -> finder.find(""));
		assertThrows(IllegalStateException.class, () -> finder.findAll());
	}

	@Test
	public void testCleanCloseWithoutRead() throws IOException {
		finder.find("iu.util.type.bundle").get();
	}

}
