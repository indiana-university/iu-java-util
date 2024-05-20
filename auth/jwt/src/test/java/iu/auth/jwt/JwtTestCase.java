package iu.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuAuthConfig;

@SuppressWarnings("javadoc")
public class JwtTestCase {

	private MockedStatic<AuthConfig> mockAuthConfig;
	private Map<String, IuAuthConfig> configs;
	private boolean sealed;

	@BeforeEach
	public void setup() {
		mockAuthConfig = mockStatic(AuthConfig.class);
		configs = new HashMap<>();
		mockAuthConfig.when(() -> AuthConfig.register(any())).then(a -> {
			assertFalse(sealed);

			final IuAuthConfig c = a.getArgument(0);
			configs.put(c.getRealm(), c);
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.seal()).then(a -> {
			sealed = true;
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.get(any(String.class))).thenAnswer(a -> {
			assertTrue(sealed);
			return Objects.requireNonNull(configs.get(a.getArguments()[0]));
		});
	}

	@AfterEach
	public void teardown() {
		mockAuthConfig.close();
		sealed = false;
		mockAuthConfig = null;
		configs = null;
	}

}
