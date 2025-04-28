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
