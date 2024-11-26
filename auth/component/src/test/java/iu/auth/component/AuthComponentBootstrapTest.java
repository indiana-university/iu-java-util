package iu.auth.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class AuthComponentBootstrapTest {

	@Test
	public void testConstructor() {
		IuTestLogger.expect(AuthComponentBootstrap.class.getName(), Level.CONFIG, "TODO: initialize authentication");
		assertDoesNotThrow(AuthComponentBootstrap::new);
	}

}
