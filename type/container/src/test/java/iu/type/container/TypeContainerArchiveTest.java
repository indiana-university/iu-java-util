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
package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.type.base.TemporaryFile;

@SuppressWarnings("javadoc")
public class TypeContainerArchiveTest {

	@Test
	public void testEmpty() throws IOException {
		final var path = TemporaryFile.of(new ByteArrayInputStream(new byte[0]));
		try {
			assertThrows(NullPointerException.class, () -> new TypeContainerArchive(path).close());
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testCorrupt() throws IOException {
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("corrupt"));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try {
			assertThrows(IllegalArgumentException.class, () -> new TypeContainerArchive(path).close());
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testPrimary() throws Exception {
		final var primary = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var in = Files.newInputStream(tca.primary())) {
			assertEquals(primary, IuText.utf8(IuStream.read(in)));
			assertEquals(0, tca.lib().length);
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testApi() throws Exception {
		final var primary = IdGenerator.generateId();
		final var api = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
			j.putNextEntry(new JarEntry("api/"));
			j.closeEntry();
			j.putNextEntry(new JarEntry("api/.jar"));
			j.write(IuText.utf8(api));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var primaryIn = Files.newInputStream(tca.primary()); //
				final var apiIn = Files.newInputStream(tca.api()[0])) {
			assertEquals(primary, IuText.utf8(IuStream.read(primaryIn)));
			assertEquals(api, IuText.utf8(IuStream.read(apiIn)));
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testLib() throws Exception {
		final var primary = IdGenerator.generateId();
		final var lib = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
			j.putNextEntry(new JarEntry("lib/"));
			j.closeEntry();
			j.putNextEntry(new JarEntry("lib/.jar"));
			j.write(IuText.utf8(lib));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var primaryIn = Files.newInputStream(tca.primary()); //
				final var libIn = Files.newInputStream(tca.lib()[0])) {
			assertEquals(primary, IuText.utf8(IuStream.read(primaryIn)));
			assertEquals(lib, IuText.utf8(IuStream.read(libIn)));
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testSupport() throws Exception {
		final var primary = IdGenerator.generateId();
		final var support = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
			j.putNextEntry(new JarEntry("support/"));
			j.closeEntry();
			j.putNextEntry(new JarEntry("support/.jar"));
			j.write(IuText.utf8(support));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var primaryIn = Files.newInputStream(tca.primary()); //
				final var supportIn = Files.newInputStream(tca.support()[0])) {
			assertEquals(primary, IuText.utf8(IuStream.read(primaryIn)));
			assertEquals(support, IuText.utf8(IuStream.read(supportIn)));
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testEjb() throws Exception {
		final var primary = IdGenerator.generateId();
		final var ejb = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
			j.putNextEntry(new JarEntry("ejb/"));
			j.closeEntry();
			j.putNextEntry(new JarEntry("ejb/.jar"));
			j.write(IuText.utf8(ejb));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var primaryIn = Files.newInputStream(tca.primary()); //
				final var ejbIn = Files.newInputStream(tca.embedded()[0])) {
			assertEquals(primary, IuText.utf8(IuStream.read(primaryIn)));
			assertEquals(ejb, IuText.utf8(IuStream.read(ejbIn)));
		} finally {
			Files.delete(path);
		}
	}

	@Test
	public void testWar() throws Exception {
		final var primary = IdGenerator.generateId();
		final var war = IdGenerator.generateId();
		final var jar = new ByteArrayOutputStream();
		try (final var j = new JarOutputStream(jar)) {
			j.putNextEntry(new JarEntry("primary.jar"));
			j.write(IuText.utf8(primary));
			j.closeEntry();
			j.putNextEntry(new JarEntry("ui/"));
			j.closeEntry();
			j.putNextEntry(new JarEntry("ui/.war"));
			j.write(IuText.utf8(war));
			j.closeEntry();
		}

		final var path = TemporaryFile.of(new ByteArrayInputStream(jar.toByteArray()));
		try (final var tca = new TypeContainerArchive(path); //
				final var primaryIn = Files.newInputStream(tca.primary()); //
				final var warIn = Files.newInputStream(tca.embedded()[0])) {
			assertEquals(primary, IuText.utf8(IuStream.read(primaryIn)));
			assertEquals(war, IuText.utf8(IuStream.read(warIn)));
		} finally {
			Files.delete(path);
		}
	}
}
