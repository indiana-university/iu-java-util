/*
 * Copyright © 2026 Indiana University
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
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

import org.mockito.Mockito;

import edu.iu.IuException;
import edu.iu.IuObject;

/**
 * Unit testing utilities.
 */
public final class IuTest {

	private static Properties properties;

	private static Enumeration<URL> getResources(String name) throws IOException {
		return ClassLoader.getSystemResources(name);
	}

	/**
	 * Returns a random enum value.
	 * 
	 * @param <E>       enum type
	 * @param enumClass enum class
	 * @return random value
	 */
	public static <E extends Enum<E>> E rand(Class<E> enumClass) {
		final var values = enumClass.getEnumConstants();
		final var i = ThreadLocalRandom.current().nextInt(values.length);
		return values[i];
	}

	/**
	 * Decorates a {@link Mockito#mock(Class)} with a proxy capable of invoking the
	 * default methods on an interface.
	 * 
	 * @param <T>  interface type
	 * @param type interface class
	 * @return decorated mock instance
	 * @deprecated Use {@link Mockito#mock(Class, org.mockito.MockSettings)
	 *             mock(type, CALLS_REAL_METHODS)} instead
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

	/**
	 * Binds annotated fields.
	 * 
	 * <p>
	 * This method provides container-like behavior that enhances the test suite for
	 * a module that opens the package containing the test class, which binds fields
	 * at runtime via a dependency injection framework.
	 * </p>
	 *
	 * <p>
	 * Fields declared by the test instance class or one of it non-platform
	 * superclasses, are bound as:
	 * </p>
	 * 
	 * <ol>
	 * <li>If the extension declares a field of the same name, the extension's field
	 * value is copied.</li>
	 * <li>If the field's type is in the same package as the test instance, a
	 * singleton instance is created via accessible constructor, and its declared
	 * fields are enqueued.</li>
	 * <li>If the field's type is a non-platform type not in the same package as the
	 * test instance, a singleton mock is created and shared by all references.</li>
	 * <li>If the field's type is a platform type, either a matching field exists on
	 * the extension or {@link IllegalStateException} is thrown.</li>
	 * </ol>
	 * 
	 * @param testInstance   test instance
	 * @param extension      extension instance
	 * @param annotationType annotation type
	 * @throws IllegalStateException if a platform field type is discovered that
	 *                               carries the annotation and doesn't have a field
	 *                               defined on the extension class to provide its
	 *                               value.
	 */
	public static void bindResources(Object testInstance, Object extension,
			Class<? extends Annotation> annotationType) {
		final Map<AnnotatedField, Object> resources = new HashMap<>();

		final Queue<Object> toBind = new ArrayDeque<>();
		toBind.offer(testInstance);

		while (!toBind.isEmpty()) {
			final var r = toBind.poll();
			var c = r.getClass();
			while (!IuObject.isPlatformName(c.getName())) {
				for (final var f : c.getDeclaredFields()) {
					final var name = f.getName();
					if (f.isAnnotationPresent(annotationType)) {
						f.setAccessible(true);
						try {
							final var sourceField = extension.getClass().getDeclaredField(name);
							if (!f.getType().isAssignableFrom(sourceField.getType()))
								throw new NoSuchFieldException();

							sourceField.setAccessible(true);
							IuException.unchecked(() -> f.set(r, sourceField.get(extension)));
						} catch (NoSuchFieldException e) {
							IuException.unchecked(() -> {
								final var field = new AnnotatedField(name, f.getAnnotation(annotationType));
								var resource = resources.get(field);
								if (resource == null) {
									final var type = f.getType();
									if (type.getPackage() == testInstance.getClass().getPackage()) {
										final var con = type.getDeclaredConstructor();
										con.setAccessible(true);
										resource = con.newInstance();
										resources.put(field, resource);
										toBind.offer(resource);
									} else if (!IuObject.isPlatformName(type.getName()))
										resources.put(field, resource = mock(type));
									else
										throw new IllegalStateException("missing " + annotationType + " " + f, e);
								}
								try {
									f.set(r, resource); // throws IAE if resource doesn't match type
								} catch (Throwable e2) {
									e2.addSuppressed(e);
									throw e2;
								}
							});
						}
					}
				}
				c = c.getSuperclass();
			}
		}
	}

	private record AnnotatedField(String name, Annotation annotation) {
	}

	private IuTest() {
	}

}
