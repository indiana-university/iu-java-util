package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuAnnotatedElement;
import iu.type.StaticDependencyHelper;
import jakarta.annotation.security.PermitAll;

@SuppressWarnings("javadoc")
public class StaticDependencyTest {

	@Test
	public void testAnnotationNotSupported() throws Exception {
		var resourceName = StaticDependencyHelper.class.getName().replace('.', '/') + ".class";
		var typeResource = StaticDependencyHelper.class.getClassLoader().getResource(resourceName).toExternalForm();
		var url = new URL(typeResource.substring(0, typeResource.length() - resourceName.length()));
		try (var loader = new URLClassLoader(new URL[] { url }) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					if (name.startsWith("iu.") || name.startsWith("jakarta.")) {
						Class<?> rv = this.findLoadedClass(name);
						if (rv != null)
							return rv;
						rv = findClass(name);
						if (resolve)
							resolveClass(rv);
						return rv;
					} else
						return super.loadClass(name, resolve);
				}
			}
		}) {
			var staticDependencyHelper = loader.loadClass(StaticDependencyHelper.class.getName());
			assertNotSame(staticDependencyHelper, StaticDependencyHelper.class);
			assertFalse((Boolean) staticDependencyHelper.getMethod("isAnnotationSupported").invoke(null));

			var annotatedElement = mock(IuAnnotatedElement.class);
			assertFalse((Boolean) staticDependencyHelper.getMethod("hasPermitAll", IuAnnotatedElement.class).invoke(null,
					annotatedElement));
		}
	}

	@Test
	public void testAnnotationSupported() {
		assertTrue(StaticDependencyHelper.isAnnotationSupported());

		var annotatedElement = mock(IuAnnotatedElement.class);
		assertFalse(StaticDependencyHelper.hasPermitAll(annotatedElement));

		annotatedElement = mock(IuAnnotatedElement.class);
		when(annotatedElement.hasAnnotation(PermitAll.class)).thenReturn(true);
		assertTrue(StaticDependencyHelper.hasPermitAll(annotatedElement));
	}

}
