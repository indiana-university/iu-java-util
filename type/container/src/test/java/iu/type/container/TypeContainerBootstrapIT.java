package iu.type.container;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class TypeContainerBootstrapIT {

	@Test
	public void testInitCrypt() throws Exception {
		System.setProperty("iu.boot.components", "target/dependency/iu-java-crypt-impl-bundle.jar");
		try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE,
					"init resource ComponentResource .*");
			IuTestLogger.expect("edu.iu.crypt.Init", Level.CONFIG, "init iu-java-crypt SPI .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"before destroy base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertDoesNotThrow(typeContainerBootstrap::run);
			assertDoesNotThrow(typeContainerBootstrap::close);
		}
	}

	@Test
	public void testInitResourceError() throws Exception {
		System.setProperty("iu.boot.components", "target/dependency/iu-java-crypt-impl-bundle.jar");
		try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
					"before destroy base iu.util.crypt");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");

			final var error = new IllegalStateException();
			try (final var mockTypeContainerResource = mockConstruction(TypeContainerResource.class, (a, ctx) -> {
				doThrow(error).when(a).join();
			})) {
				assertSame(error, assertThrows(IllegalStateException.class, typeContainerBootstrap::run));
			}
		}
	}

}
