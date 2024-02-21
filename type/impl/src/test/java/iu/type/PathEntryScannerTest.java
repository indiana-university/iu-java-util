/*
 * Copyright © 2024 Indiana University
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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class PathEntryScannerTest {

	@Test
	public void testScanJar() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.archive"));
		assertEquals(Set.of("META-INF/lib/jakarta.ejb-api-4.0.0.jar", "META-INF/lib/jakarta.transaction-api-2.0.0.jar",
				"edu/iu/type/testruntime/TestRuntime.class", "module-info.class", "META-INF/lib/commons-lang-2.6.jar",
				"edu/iu/type/testruntime/package-info.class", "edu/iu/type/testruntime/UrlReader.class",
				"META-INF/maven/edu.iu.util/iu-java-type-testruntime/pom.xml",
				"META-INF/lib/jakarta.interceptor-api-2.1.0.jar", "META-INF/lib/jakarta.json-api-2.1.2.jar",
				"META-INF/maven/edu.iu.util/iu-java-type-testruntime/pom.properties",
				"META-INF/lib/jakarta.annotation-api-2.1.1.jar", "META-INF/runtime.properties"),
				PathEntryScanner.findResources(entry));
	}

	@Test
	public void testScanFolder() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.outputFolder"));
		final var scannedEntries = PathEntryScanner.findResources(entry);
		assertTrue(
				scannedEntries.containsAll(Set.of("META-INF/lib/jakarta.ejb-api-4.0.0.jar",
						"META-INF/lib/jakarta.transaction-api-2.0.0.jar", "edu/iu/type/testruntime/TestRuntime.class",
						"module-info.class", "META-INF/lib/commons-lang-2.6.jar",
						"edu/iu/type/testruntime/package-info.class", "edu/iu/type/testruntime/UrlReader.class",
						"META-INF/lib/jakarta.interceptor-api-2.1.0.jar", "META-INF/lib/jakarta.json-api-2.1.2.jar",
						"META-INF/lib/jakarta.annotation-api-2.1.1.jar", "META-INF/runtime.properties")),
				scannedEntries::toString);
	}

	@Test
	public void testReadRequiresAbsolute() throws IOException {
		final var path = mock(Path.class);
		for (final var invalidName : IuIterable.iter( //
				"/should/not/be/allowed", //
				"../should/not/be/allowed", //
				"should/../not/be/allowed", //
				"should/not/be/allowed/.."))
			assertSame("resourceName must be relative",
					assertThrows(IllegalArgumentException.class, () -> PathEntryScanner.read(path, invalidName))
							.getMessage(),
					() -> invalidName);
	}

	@Test
	public void testReadFromJar() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.archive"));
		final var properties = new Properties();
		properties.load(new ByteArrayInputStream(PathEntryScanner.read(entry, "META-INF/runtime.properties")));
		assertEquals("a property value", properties.getProperty("test.property"));
	}

	@Test
	public void testReadFromFile() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.outputFolder"));
		final var properties = new Properties();
		properties.load(new ByteArrayInputStream(PathEntryScanner.read(entry, "META-INF/runtime.properties")));
		assertEquals("a property value", properties.getProperty("test.property"));
	}

	@Test
	public void testMissingFromJar() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.archive"));
		assertThrows(NoSuchFileException.class, () -> PathEntryScanner.read(entry, "missing resource"));
	}

	@Test
	public void testMissingFromFile() throws IOException {
		final var entry = Path.of(IuTest.getProperty("testruntime.outputFolder"));
		assertThrows(NoSuchFileException.class, () -> PathEntryScanner.read(entry, "missing resource"));
	}

}
