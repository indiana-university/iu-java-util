package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeContainerResourceTest {

	@Test
	public void testNonRunnable() {
		class A {
		}
		final var resource = mock(IuResource.class);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);
		final var containerResource = new TypeContainerResource(resource, component);
		assertDoesNotThrow(() -> containerResource.join());
	}

	@Test
	public void testRunnable() {
		class A implements Runnable {
			boolean run;

			@Override
			public void run() {
				this.run = true;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);

		final var containerResource = new TypeContainerResource(resource, component); // starts thread
		assertDoesNotThrow(() -> containerResource.join());
		assertTrue(a.run);
	}

	@Test
	public void testUnsafeRunnable() {
		class A implements UnsafeRunnable {
			boolean run;

			@Override
			public void run() {
				this.run = true;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);

		final var containerResource = new TypeContainerResource(resource, component); // starts thread
		assertDoesNotThrow(() -> containerResource.join());
		assertTrue(a.run);
	}

	@Test
	public void testError() {
		final var error = new IllegalStateException();
		class A implements Runnable {
			@Override
			public void run() {
				throw error;
			}
		}
		final var a = new A();
		final var resource = mock(IuResource.class);
		when(resource.get()).thenReturn(a);
		when(resource.type()).thenReturn(IuType.of(A.class));
		final var component = mock(IuComponent.class);

		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + resource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.SEVERE, "fail resource " + resource,
				IllegalStateException.class, e -> e == error);

		final var containerResource = new TypeContainerResource(resource, component); // starts thread
		assertSame(error, assertThrows(IllegalStateException.class, () -> containerResource.join()));
	}

}
