package iu.esas.thirdparty.auth.oidc.client;

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import edu.iu.IuDataStore;
import edu.iu.IuObject;
import edu.iu.IuRequestAttributes;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.auth.oidc.IuOpenIdProviderMetadata;
import edu.iu.auth.saml.IuSamlServiceProviderMetadata;
import edu.iu.auth.saml.IuSamlSessionVerifier;
import edu.iu.auth.session.IuSessionConfiguration;
import edu.iu.crypt.Init;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.oidc.ThirdPartyClient;
import edu.iu.oidc.IuOidcClientEndpoint;
import edu.iu.redis.IuRedis;
import edu.iu.redis.IuRedisConfiguration;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAccess;
import iu.auth.config.AuthConfig;
import iu.auth.config.RequestCallerAttributes;
import iu.auth.pki.PkiVerifier;
import iu.auth.saml.SamlServiceProvider;
import iu.auth.session.SessionHandler;
import iu.esas.vault.IuVaultClient;
import iu.eshrs.auth.boot.IuAuthorizationClient.AuthMethod;
import iu.eshrs.auth.boot.IuAuthorizationClient.GrantType;
import iu.oidc.provider.config.IuClientResource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;

/**
 * Third party authorization bootstrap resource
 */
@Resource
@Priority(100)
public class ThirdPartyAuth {

	private static final Logger LOG = Logger.getLogger(ThirdPartyAuth.class.getName());

	private record Call(RequestCallerAttributes caller, IuOidcClientEndpoint clientEndpoint) {
	}

	private static final ThreadLocal<Call> CALLER = new ThreadLocal<>();

	@Resource
	private String application;
	@Resource
	private String environment;
	@Resource
	private boolean development;
	@Resource
	private boolean production;
	@Resource
	private URI resourceUri;
	@Resource
	private URI postUri;

	@Resource
	private ThirdPartyAccess thirdPartyAccess;

	private IuDataStore sessionStore;
	private SessionHandler sessionHandler;

	private SamlServiceProvider samlProvider;
	private IuSamlSessionVerifier samlSessionVerifier;

	/**
	 * Default constructor
	 */
	ThirdPartyAuth() {
	}

	/**
	 * Binds request attributes and principal identity to a potentially remote call.
	 * 
	 * @param <T>                   return type
	 * @param call                  potentially remote call
	 * @param attributes            request attributes
	 * @param clientEndpoint        client endpoint
	 * @param resourceCode          resource application code
	 * @param principal             principal identity
	 * @param impersonatedPrincipal impersonated principal name
	 * @return from {@link Callable#call()}
	 * @throws Exception from {@link Callable#call()}
	 */
	<T> T call(Callable<T> call, IuRequestAttributes attributes, IuOidcClientEndpoint clientEndpoint,
			String resourceCode, IuPrincipalIdentity principal, String impersonatedPrincipal) throws Exception {
		final String authnPrincipal;
		if (principal == null) {
			authnPrincipal = application;
			if (impersonatedPrincipal != null) {
				LOG.warning(
						"ignoring impersonated principal name for non-authenticated request; " + impersonatedPrincipal);
				impersonatedPrincipal = null;
			}
		} else {
			IuPrincipalIdentity.verify(principal, application);
			authnPrincipal = principal.getName();
		}

		if (impersonatedPrincipal != null)
			if (production) {
				LOG.warning("ignoring impersonated principal name in production; " + impersonatedPrincipal
						+ " requested by " + authnPrincipal);
				impersonatedPrincipal = null;
			} else {
				CALLER.set(new Call(new RequestCallerAttributes(attributes, authnPrincipal, null), null));
				try {
					if (!thirdPartyAccess.isImpersonationAllowed(resourceCode)) {
						LOG.warning("ignoring impersonated principal for unauthorized user; " + impersonatedPrincipal
								+ " requested by " + authnPrincipal);
						impersonatedPrincipal = null;
					} else
						LOG.info("using impersonated principal name " + impersonatedPrincipal
								+ " as subject, instead of authenticated " + authnPrincipal);
				} finally {
					CALLER.remove();
				}
			}

		IuObject.require(CALLER.get(), Objects::isNull);
		CALLER.set(new Call(new RequestCallerAttributes(attributes, authnPrincipal, impersonatedPrincipal),
				clientEndpoint));
		try {
			return call.call();
		} finally {
			CALLER.remove();
		}
	}

	/**
	 * Gets {@link IuCallerAttributes} bound by
	 * {@link #call(Callable, IuRequestAttributes, IuOidcClientEndpoint, String, IuPrincipalIdentity, String)}.
	 * 
	 * @return {@link IuCallerAttributes}
	 */
	static IuCallerAttributes getCaller() {
		return Objects.requireNonNull(CALLER.get().caller);
	}

	/**
	 * Gets the {@link IuOidcClientEndpoint} bound by
	 * {@link #call(Callable, IuRequestAttributes, IuOidcClientEndpoint, String, IuPrincipalIdentity, String)}
	 * 
	 * @return {@link URI}
	 */
	static IuOidcClientEndpoint getClientEndpoint() {
		return CALLER.get().clientEndpoint;
	}

	/**
	 * Initializes the third party authorization configuration and resources.
	 */
	@PostConstruct
	void init() {
		Init.init();
		final var vaults = IuVaultClient.init(development, application, environment);

		AuthConfig.registerInterface("session", IuSessionConfiguration.class, vaults);
		AuthConfig.registerInterface("redis", IuRedisConfiguration.class, vaults);
		AuthConfig.registerInterface("saml", IuSamlServiceProviderMetadata.class, vaults);
		AuthConfig.registerInterface("issuer", ThirdPartyIssuer.class, vaults);
		AuthConfig.registerInterface("client", ThirdPartyClient.class, vaults);
		AuthConfig.registerInterface(IuOidcClientEndpoint.class);
		AuthConfig.registerInterface(IuClientResource.class);
		AuthConfig.registerInterface(IuOpenIdProviderMetadata.class);

		final var samlConfiguration = AuthConfig.load(IuSamlServiceProviderMetadata.class, application);
		AuthConfig.register(new PkiVerifier(samlConfiguration.getIdentity()));

		samlProvider = new SamlServiceProvider(postUri, application, samlConfiguration);
		AuthConfig.register(samlProvider);

		AuthConfig.seal();

		sessionStore = IuRedis.createConnection(AuthConfig.load(IuRedisConfiguration.class, "session"));
		sessionHandler = new SessionHandler(resourceUri,
				() -> AuthConfig.load(IuSessionConfiguration.class, application), sessionStore);

		samlSessionVerifier = IuSamlSessionVerifier.create(postUri);
	}

	/**
	 * Returns the session store used for third party authorization sessions.
	 *
	 * @return the {@link IuDataStore} session store
	 */
	IuDataStore sessionStore() {
		return sessionStore;
	}

	/**
	 * Returns the session handler for tracking user interactions.
	 * 
	 * @return {@link #sessionHandler}
	 */
	SessionHandler sessionHandler() {
		return sessionHandler;
	}

	/**
	 * Returns the SAML service provider used for third party authorization.
	 *
	 * @return the {@link SamlServiceProvider} instance
	 */
	SamlServiceProvider samlProvider() {
		return samlProvider;
	}

	/**
	 * Returns the SAML session verifier used for third party authorization.
	 *
	 * @return the {@link IuSamlSessionVerifier} instance
	 */
	IuSamlSessionVerifier samlSessionVerifier() {
		return samlSessionVerifier;
	}

	/**
	 * Gets the OIDC metadata for third party.
	 * 
	 * @return OIDC metadata
	 */
	IuOpenIdProviderMetadata getOidcMetadata() {
		final var issuer = AuthConfig.load(ThirdPartyIssuer.class, application);
		final Set<Algorithm> signAlg = new LinkedHashSet<>();
		for (final var key : issuer.getKeys())
			if (Use.SIGN.equals(key.getUse()))
				for (final var alg : EnumSet.allOf(Algorithm.class))
					if (alg.use.equals(Use.SIGN))
						if (Arrays.asList(alg.type).contains(key.getType()))
							signAlg.add(alg);

		return new IuOpenIdProviderMetadata() {
			@Override
			public URI getIssuer() {
				return resourceUri;
			}

			@Override
			public URI getJwksUri() {
				return URI.create(resourceUri + "/oidc/jwks");
			}

			@Override
			public Set<String> getScopesSupported() {
				return Set.of("openid");
			}

			@Override
			public URI getUserinfoEndpoint() {
				return URI.create(resourceUri + "/oidc/userinfo");
			}

			@Override
			public Iterable<Algorithm> getUserinfoSigningAlgValuesSupported() {
				return signAlg;
			}

			@Override
			public Iterable<Encryption> getUserinfoEncryptionEncValuesSupported() {
				return EnumSet.allOf(Encryption.class);
			}

			@SuppressWarnings("deprecation")
			@Override
			public Iterable<Algorithm> getUserinfoEncryptionAlgValuesSupported() {
				return List.of(Algorithm.ECDH_ES, Algorithm.ECDH_ES_A128KW, Algorithm.ECDH_ES_A192KW,
						Algorithm.ECDH_ES_A256KW, Algorithm.RSA1_5, Algorithm.RSA_OAEP, Algorithm.RSA_OAEP_256);
			}

			@Override
			public URI getTokenEndpoint() {
				return URI.create(resourceUri + "/oidc/token");
			}

			@SuppressWarnings("deprecation")
			@Override
			public Iterable<Algorithm> getTokenEndpointSigningAlgValuesSupported() {
				return List.of(Algorithm.EDDSA, Algorithm.ES256, Algorithm.ES384, Algorithm.ES512, Algorithm.PS256,
						Algorithm.PS384, Algorithm.PS512, Algorithm.RS256, Algorithm.RS384, Algorithm.RS512);
			}

			@Override
			public Set<AuthMethod> getTokenEndpointAuthMethodsSupported() {
				return Set.of(AuthMethod.PRIVATE_KEY_JWT);
			}

			@Override
			public Iterable<Algorithm> getIdTokenSigningAlgValuesSupported() {
				return signAlg;
			}

			@Override
			public Iterable<Encryption> getIdTokenEncryptionEncValuesSupported() {
				return EnumSet.allOf(Encryption.class);
			}

			@SuppressWarnings("deprecation")
			@Override
			public Iterable<Algorithm> getIdTokenEncryptionAlgValuesSupported() {
				return List.of(Algorithm.ECDH_ES, Algorithm.ECDH_ES_A128KW, Algorithm.ECDH_ES_A192KW,
						Algorithm.ECDH_ES_A256KW, Algorithm.RSA1_5, Algorithm.RSA_OAEP, Algorithm.RSA_OAEP_256);
			}

			@Override
			public Set<String> getGrantTypesSupported() {
				return Set.of(GrantType.AUTHORIZATION_CODE.parameterValue, GrantType.REFRESH_TOKEN.parameterValue);
			}

			@Override
			public URI getAuthorizationEndpoint() {
				return URI.create(resourceUri + "/oidc/authorize");
			}

			@Override
			public Set<String> getAcrValuesSupported() {
				return null;
			}

			@Override
			public boolean isRequireRequestUriRegistration() {
				return false;
			}

			@Override
			public boolean isRequestUriParameterSupported() {
				return false;
			}

			@Override
			public boolean isRequestParameterSupported() {
				return false;
			}

			@Override
			public boolean isClaimsParameterSupported() {
				return false;
			}

			@Override
			public Set<String> getUiLocalesSupported() {
				return null;
			}

			@Override
			public Set<String> getSubjectTypesSupported() {
				return null;
			}

			@Override
			public URI getServiceDocumentation() {
				return null;
			}

			@Override
			public Set<String> getResponseTypesSupported() {
				return null;
			}

			@Override
			public Set<String> getResponseModesSupported() {
				return null;
			}

			@Override
			public Set<Algorithm> getRequestObjectSigningAlgValuesSupported() {
				return null;
			}

			@Override
			public Set<Encryption> getRequestObjectEncryptionEncValuesSupported() {
				return null;
			}

			@Override
			public Set<Algorithm> getRequestObjectEncryptionAlgValuesSupported() {
				return null;
			}

			@Override
			public URI getRegistrationEndpoint() {
				return null;
			}

			@Override
			public URI getOpTosUri() {
				return null;
			}

			@Override
			public URI getOpPolicyUri() {
				return null;
			}

			@Override
			public Set<String> getDisplayValuesSupported() {
				return null;
			}

			@Override
			public Set<String> getClaimsSupported() {
				return null;
			}

			@Override
			public Set<String> getClaimsLocalesSupported() {
				return null;
			}

			@Override
			public Set<String> getClaimTypesSupported() {
				return null;
			}
		};
	}

}