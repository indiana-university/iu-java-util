package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import iu.type.container.spi.IuEnvironment;

@SuppressWarnings("javadoc")
public class DefaultEnvironmentTest extends TypeContainerTestCase {

	@Test
	public void testMissing() {
		final var name = IdGenerator.generateId();
		System.setProperty("iu.config", "");
		final var env = new DefaultEnvironment();
		assertEquals("DefaultEnvironment [config=null]", env.toString());
		assertNull(env.resolve(name, String.class));
	}

	@Test
	public void testEmpty() {
		final var name = IdGenerator.generateId();
		final var env = new DefaultEnvironment();
		assertEquals("DefaultEnvironment [config=" + config + ']', env.toString());

		IuTestLogger.expect(DefaultEnvironment.class.getName(), Level.CONFIG,
				"missing " + config.resolve("environment.properties"));
		assertNull(env.resolve(name, String.class));
	}

	@Test
	public void testConvertString() {
		final var value = IdGenerator.generateId();
		assertEquals(value, DefaultEnvironment.convert(String.class, value));
	}

	@Test
	public void testConvertBoolean() {
		assertTrue(DefaultEnvironment.convert(boolean.class, "true"));
		assertTrue(DefaultEnvironment.convert(Boolean.class, "true"));
		assertFalse(DefaultEnvironment.convert(boolean.class, "false"));
		assertFalse(DefaultEnvironment.convert(Boolean.class, "false"));
	}

	@Test
	public void testConvertInt() {
		final var i = ThreadLocalRandom.current().nextInt();
		assertEquals(i, DefaultEnvironment.convert(int.class, Integer.toString(i)));
		assertEquals(i, DefaultEnvironment.convert(Integer.class, Integer.toString(i)));
	}

	@Test
	public void testConvertLong() {
		final var l = ThreadLocalRandom.current().nextLong();
		assertEquals(l, DefaultEnvironment.convert(long.class, Long.toString(l)));
		assertEquals(l, DefaultEnvironment.convert(Long.class, Long.toString(l)));
	}

	@Test
	public void testConvertDecimal() {
		final var d = new BigDecimal(ThreadLocalRandom.current().nextDouble());
		assertEquals(d, DefaultEnvironment.convert(BigDecimal.class, d.toString()));
	}

	@Test
	public void testConvertURI() {
		final var value = URI.create(IdGenerator.generateId());
		assertEquals(value, DefaultEnvironment.convert(URI.class, value.toString()));
	}

	@Test
	public void testConvertDuration() {
		final var d = Duration.ofNanos(ThreadLocalRandom.current().nextLong());
		assertEquals(d, DefaultEnvironment.convert(Duration.class, d.toString()));
	}

	@Test
	public void testConvertUnsupported() {
		IuTestLogger.expect(DefaultEnvironment.class.getName(), Level.FINEST, "unsupported java.lang.Object");
		assertNull(DefaultEnvironment.convert(Object.class, IdGenerator.generateId()));
	}

	@Test
	public void testValues() throws IOException {
		Files.createDirectories(config);

		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();

		final var nameA = IdGenerator.generateId();
		final var valueA = IdGenerator.generateId();
		final var propsA = new Properties();
		propsA.setProperty(nameA, valueA);
		try (final var out = Files.newOutputStream(config.resolve("environment.properties"))) {
			propsA.store(out, null);
		}

		final var nameB = IdGenerator.generateId();
		final var valueB = IdGenerator.generateId();
		final var propsB = new Properties();
		propsB.setProperty(nameB, valueB);
		try (final var out = Files.newOutputStream(config.resolve(application + ".properties"))) {
			propsB.store(out, null);
		}

		final var nameC = IdGenerator.generateId();
		final var valueC = IdGenerator.generateId();
		final var propsC = new Properties();
		propsC.setProperty(nameC, valueC);
		try (final var out = Files.newOutputStream(config.resolve(application + '-' + environment + ".properties"))) {
			propsC.store(out, null);
		}

		final IuEnvironment env;
		System.setProperty("iu.application", application);
		System.setProperty("iu.environment", environment);
		try {
			env = new DefaultEnvironment();
		} finally {
			System.clearProperty("iu.application");
			System.clearProperty("iu.environment");
		}

		IuTestLogger.expect(DefaultEnvironment.class.getName(), Level.CONFIG,
				"loaded " + config.resolve(application + '-' + environment + ".properties"));
		IuTestLogger.expect(DefaultEnvironment.class.getName(), Level.CONFIG,
				"loaded " + config.resolve(application + ".properties"));
		IuTestLogger.expect(DefaultEnvironment.class.getName(), Level.CONFIG,
				"loaded " + config.resolve("environment.properties"));

		assertEquals(valueA, env.resolve(nameA, String.class));
		assertEquals(valueB, env.resolve(nameB, String.class));
		assertEquals(valueC, env.resolve(nameC, String.class));
	}

}
