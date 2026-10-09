package iu.oidc.client;

import java.util.logging.Level;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import edu.iu.IuRuntimeEnvironment;
import edu.iu.client.IuHttp;
import edu.iu.client.IuVault;
import edu.iu.config.IuConfig;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.oidc.client.config.IuOidcClient;
import iu.oidc.client.config.IuOidcClientReference;
import iu.oidc.client.config.IuOidcProvider;

class OidcClientITSupport implements BeforeAllCallback, BeforeEachCallback {

	static boolean isEnabled() {
		return IuVault.isConfigured() && IuRuntimeEnvironment.envOptional("it.oidc.provider") != null;
	}

	private static String providerName;
	private static String clientName;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
		
		providerName = IuRuntimeEnvironment.env("it.oidc.provider");
		clientName = IuRuntimeEnvironment.env("it.oidc.client");

		IuConfig.registerInterface("provider", IuOidcProvider.class, IuVault.RUNTIME);
		IuConfig.registerInterface("client", IuOidcClient.class, IuVault.RUNTIME);
		IuConfig.registerInterface("key", WebKey.class, IuVault.RUNTIME);
		IuConfig.seal();
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		IuTestLogger.allow(IuHttp.class.getName(), Level.INFO);
	}

	static IuOidcClientReference clientRef() {
		return new IuOidcClientReference() {
			@Override
			public IuOidcProvider getProvider() {
				return IuConfig.load(IuOidcProvider.class, providerName);
			}

			@Override
			public IuOidcClient getClient() {
				return IuConfig.load(IuOidcClient.class, clientName);
			}
		};
	}

}
