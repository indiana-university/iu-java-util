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
package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.mockito.Mockito;

import edu.iu.IuException;

/**
 * Unit testing utilities.
 */
public final class IuTest {

	private static Properties properties;

	private static Enumeration<URL> getResources(String name) throws IOException {
		return ClassLoader.getSystemResources(name);
	}

	/**
	 * Decorates a {@link Mockito#mock(Class)} with a proxy capable of invoking the
	 * default methods on an interface.
	 * 
	 * @param <T>  interface type
	 * @param type interface class
	 * @return decorated mock instance
	 */
	public static <T> T mockWithDefaults(Class<T> type) {
		IuTest.class.getModule().addReads(type.getModule());

		var mock = Mockito.mock(type);
		assertTrue(type.isInterface());

		return Mockito.spy(type
				.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
					if (method.isDefault())
						try {
							return MethodHandles.privateLookupIn(type, MethodHandles.lookup())
									.unreflectSpecial(method, type).bindTo(proxy).invokeWithArguments(args);
						} catch (UnsupportedOperationException e) {
						}
					else
						method.setAccessible(true);

					return method.invoke(mock, args);
				})));
	}

	/**
	 * Loads {@link Properties} from the system classpath resource
	 * {@code META-INF/test.properties}.
	 * 
	 * <p>
	 * The method facilitates passing properties defined in {@code pom.xml} to a
	 * unit test.
	 * </p>
	 * 
	 * @return properties
	 */
	public static Properties properties() {
		if (properties == null)
			IuException.unchecked(() -> {
				var properties = new Properties();
				Enumeration<URL> sources = getResources("META-INF/iu-test.properties");
				for (var source : (Iterable<URL>) sources::asIterator)
					try (var in = source.openStream()) {
						properties.load(in);
					}
				IuTest.properties = properties;
			});
		return properties;
	}

	/**
	 * Gets a build-time property value.
	 * 
	 * @param key property name
	 * @return property value
	 */
	public static String getProperty(String key) {
		return properties().getProperty(key);

	}

	private IuTest() {
	}

}
