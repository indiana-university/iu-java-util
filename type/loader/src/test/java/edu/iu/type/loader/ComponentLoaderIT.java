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
package edu.iu.type.loader;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ComponentLoaderIT {

	public static InputStream getComponentArchive(String componentName) throws IOException {
		return Files.newInputStream(Path.of(IuTest.getProperty(componentName + ".archive")));
	}

	public static InputStream[] getProvidedDependencyArchives(String componentName) throws IOException {
		Queue<InputStream> providedDependencyArchives = new ArrayDeque<>();
		var deps = IuTest.getProperty(componentName + ".deps");
		if (deps != null)
			for (var jar : Files.newDirectoryStream(Path.of(deps).toRealPath()))
				providedDependencyArchives.offer(Files.newInputStream(jar));
		return providedDependencyArchives.toArray(new InputStream[providedDependencyArchives.size()]);
	}

	@Test
	public void testLoadsRuntime() throws Throwable {
		final var out = new ByteArrayOutputStream();
		try (final var jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("foo"));
			jar.write("bar".getBytes());
			jar.closeEntry();
		}

		try (final var loader = new IuComponentLoader(IuIterable.iter(new ByteArrayInputStream(out.toByteArray())));
				final var comp = loader.load(a -> {

				}, getComponentArchive("testruntime"), getProvidedDependencyArchives("testruntime"))) {
			loader.getLoader();
			comp.getClassLoader();
			comp.getModuleLayer();
			comp.close();
			loader.close();
			assertThrows(IllegalStateException.class, loader::getLoader);
		}
	}

}