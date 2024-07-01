package iu.auth.saml;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.config.AuthConfig;

/**
 * SAML session implementation to support session management
 */
final class SamlSession implements IuSamlSession {

	private final Logger LOG = Logger.getLogger(SamlSession.class.getName());

	private final SamlServiceProvider serviceProvider;
	private final URI postUri;
	private final URI entryPointUri;
	private final Supplier<byte[]> secretKey;

	private String relayState;
	private String sessionId;
	private SamlPrincipal id;

	/**
	 * Constructor.
	 * 
	 * @param entryPointUri   Application entry point URI
	 * @param postUri         HTTP POST Binding URI
	 * @param serviceProvider {@link SamlServiceProvider}
	 * @param secretKey       Secret key to use for tokenizing the session.
	 */
	SamlSession(URI entryPointUri, URI postUri, Supplier<byte[]> secretKey) {
		this.entryPointUri = entryPointUri;
		this.postUri = postUri;
		this.serviceProvider = SamlServiceProvider.withBinding(postUri);
		if (!serviceProvider.isValidEntryPoint(entryPointUri))
			throw new IllegalArgumentException("Invalid entry point URI");
		this.secretKey = secretKey;
	}

	/**
	 * Token constructor.
	 * 
	 * @param serviceProvider {@link SamlServiceProvider}
	 * @param token           tokenized session
	 * @param secretKey       Secret key to use for detokenizing the session.
	 */
	SamlSession(String token, Supplier<byte[]> secretKey) {
		final var key = WebKey.builder(Type.RAW).key(secretKey.get()).build();
		final var decryptedToken = WebSignedPayload.parse(WebEncryption.parse(token).decryptText(key));
		final var tokenPayload = decryptedToken.getPayload();
		final var data = IuJson.parse(IuText.utf8(tokenPayload)).asJsonObject();

		entryPointUri = Objects.requireNonNull(IuJson.get(data, "entry_point_uri", AuthConfig.adaptJson(URI.class)));
		postUri = Objects.requireNonNull(IuJson.get(data, "post_uri", AuthConfig.adaptJson(URI.class)));
		serviceProvider = SamlServiceProvider.withBinding(postUri);
		decryptedToken.getSignatures().iterator().next().verify(tokenPayload, serviceProvider.getVerifyKey());

		this.secretKey = secretKey;
		relayState = IuJson.get(data, "relay_state");
		sessionId = IuJson.get(data, "session_id");
		id = IuJson.get(data, "id", SamlPrincipal.JSON);
	}

	@Override
	public URI getRequestUri() {
		IuObject.require(id, Objects::isNull);
		IuObject.require(relayState, Objects::isNull);
		IuObject.require(sessionId, Objects::isNull);

		relayState = IdGenerator.generateId();
		sessionId = IdGenerator.generateId();
		return serviceProvider.getAuthnRequest(relayState, sessionId);
	}

	@Override
	public void verifyResponse(String remoteAddr, String samlResponse, String relayState)
			throws IuAuthenticationException {
		try {
			IuObject.require(id, Objects::isNull);
			IuObject.once(Objects.requireNonNull(relayState), Objects.requireNonNull(this.relayState));

			id = serviceProvider.verifyResponse( //
					IuWebUtils.getInetAddress(remoteAddr), //
					Objects.requireNonNull(samlResponse), //
					Objects.requireNonNull(sessionId));

		} catch (Throwable e) {
			LOG.log(Level.INFO, "Invalid SAML Response", e);

			final var challenge = new IuAuthenticationException(null, e);
			challenge.setLocation(URI.create(entryPointUri + "?RelayState="
					+ URLEncoder.encode(this.relayState, StandardCharsets.UTF_8)));
			throw challenge;
		}
	}

	@Override
	public SamlPrincipal getPrincipalIdentity() throws IuAuthenticationException {
		try {
			IuPrincipalIdentity.verify(id, serviceProvider.getRealm());
		} catch (IuAuthenticationException e) {
			e.setLocation(entryPointUri);
			throw e;
		}
		return id;
	}

	@Override
	public String toString() {
		final var builder = IuJson.object();
		IuJson.add(builder, "entry_point_uri", () -> entryPointUri, AuthConfig.adaptJson(URI.class));
		IuJson.add(builder, "post_uri", () -> postUri, AuthConfig.adaptJson(URI.class));
		builder.add("relay_state", relayState) //
				.add("session_id", sessionId);
		IuJson.add(builder, "id", () -> id, IuJsonAdapter.to(a -> IuJson.parse(a.toString())));

		final var secretKey = this.secretKey.get();
		final Encryption enc;
		switch (secretKey.length) {
		case 16:
			enc = Encryption.A128GCM;
			break;
		case 24:
			enc = Encryption.A192GCM;
			break;
		case 32:
			enc = Encryption.A256GCM;
			break;
		default:
			throw new IllegalStateException("secret key size");
		}

		return WebEncryption.builder(enc).compact() //
				.addRecipient(Algorithm.DIRECT) //
				.key(WebKey.builder(Type.RAW).key(secretKey).build()) //
				.encrypt(WebSignature.builder(serviceProvider.getVerifyAlg()).compact()
						.key(serviceProvider.getVerifyKey()) //
						.sign(builder.build().toString()).compact()) //
				.compact();
	}

}
