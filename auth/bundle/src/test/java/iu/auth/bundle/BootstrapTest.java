package iu.auth.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.iu.auth.IuApiCredentials;

@SuppressWarnings("javadoc")
public class BootstrapTest {

	@Test
	public void testShutdown() throws Exception {
		IuApiCredentials.basic("foo", "bar");
		Bootstrap.shutdown();
		Bootstrap.shutdown();
		assertThrows(IllegalStateException.class, () -> IuApiCredentials.basic("foo", "bar"));

		final var impl = Bootstrap.class.getDeclaredField("impl");
		impl.setAccessible(true);
		final var loadImpl = Bootstrap.class.getDeclaredMethod("loadImpl");
		loadImpl.setAccessible(true);
		impl.set(null, loadImpl.invoke(null));
		assertDoesNotThrow(() -> IuApiCredentials.basic("foo", "bar"));
	}

}
