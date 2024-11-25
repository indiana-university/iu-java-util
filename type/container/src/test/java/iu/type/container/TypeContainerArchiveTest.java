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
