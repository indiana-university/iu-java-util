/*
 * Copyright © 2025 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentModuleFinderTest extends IuTypeTestCase {

	@Test
	public void testRuntime() throws IOException {
		var path = TestArchives.getModulePath("testruntime");
		ModuleReference ref;
		try (var finder = new ComponentModuleFinder(path)) {
			Set<String> names = new HashSet<>();
			finder.findAll().forEach(r -> names.add(r.descriptor().name()));
			assertTrue(names.remove("iu.util.type.testruntime"), names.toString());
			assertTrue(names.remove("org.eclipse.parsson"), names.toString());
			assertTrue(names.isEmpty(), names.toString());

			ref = finder.find("iu.util.type.testruntime").get();
			try (var reader = ref.open()) {
				assertSame(reader, ref.open());
				var url = reader.find("META-INF/runtime.properties").get().toURL();
				var c = url.openConnection();
				c.setUseCaches(false);
				Properties props = new Properties();
				try (var in = c.getInputStream()) {
					props.load(in);
				}
				assertEquals("a property value", props.get("test.property"));
			}
			finder.close(); // extra call should not throw
			assertThrows(IllegalStateException.class, () -> finder.find(""));
			assertThrows(IllegalStateException.class, () -> finder.findAll());
		}
		assertThrows(IllegalStateException.class, () -> ref.open());
		for (var p : path)
			Files.delete(p);
	}

}
