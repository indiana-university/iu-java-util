package iu.auth.session;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.auth.config.IuSessionHandler;
import edu.iu.auth.session.IuSession;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * {@link IuSessionHandler} implementation
 */
public class SessionHandler implements IuSessionHandler {

	private static final Map<String, SessionToken> SESSION_TOKENS = new ConcurrentHashMap<>();

	private final URI resourceUri;
	private byte[] secretKey;
	private final IuSessionConfiguration configuration;
	private final Supplier<WebKey> issuerKey;
	private final Algorithm algorithm;

	/**
	 * Constructor.
	 * @param resourceUri root protected resource URI
	 * @param configuration {#link {@link IuSessionConfiguration}
	 * @param issuerKey issuer key supplier
	 * @param algorithm  algorithm
	 */
	public SessionHandler(URI resourceUri, IuSessionConfiguration configuration, Supplier<WebKey> issuerKey, Algorithm algorithm) {
		if(!resourceUri.getPath().endsWith("/"))
		  throw new IllegalArgumentException("Invalid resource Uri");
		this.resourceUri = resourceUri;
		this.configuration = configuration;
		this.issuerKey = issuerKey;
		this.algorithm = algorithm;
	}

	@Override
	public IuSession create() {
		return new Session(resourceUri, configuration.getMaxSessionTtl());
	}

	@Override
	public IuSession activate(Iterable<HttpCookie> cookies) {
		if (secretKey == null)
			return null;

		final var hashKey = hashKey();
		final var storedSession = SESSION_TOKENS.get(hashKey);
		if (storedSession == null) {
			secretKey = null;
			return null;
		}

		if (storedSession.inactivePurgeTime().isBefore(Instant.now())) {
			SESSION_TOKENS.remove(hashKey);
			secretKey = null;
			return null;
		}

		return new Session(storedSession.token(), secretKey, issuerKey.get());
	}

	@Override
	public String store(IuSession session, boolean strict) {

		secretKey = EphemeralKeys.secret("AES", 256);
		Session s = (Session)session;

		SESSION_TOKENS.put(hashKey(), new SessionToken(s.tokenize(secretKey, issuerKey.get(), algorithm ), Instant.now().plus(configuration.getInActiveTtl())));

		final var cookieBuilder = new StringBuilder();
		cookieBuilder.append(getSessionCookieName(resourceUri));
		cookieBuilder.append('=');
		cookieBuilder.append(IuText.base64(secretKey));
		cookieBuilder.append("; Path=").append(resourceUri.getPath());
		cookieBuilder.append("; Secure");
		cookieBuilder.append("; HttpOnly");
		if (strict)
			cookieBuilder.append("; SameSite=Strict");
		return cookieBuilder.toString();
	}


	/**
	 * Gets the hash key to use for storing tokenized session data.
	 * 
	 * @return encoded digest of the session key
	 */
	String hashKey() {
		return IuText.base64(IuDigest.sha256(secretKey));
	}

	/**
	 * Gets the session cookie name for a protected resource URI
	 * 
	 * @param resourceUri resource {@link URI}
	 * @return session cookie name
	 */
	String getSessionCookieName(URI resourceUri) {
		return "iu-sk_" + IuText.base64(IuDigest.sha256(IuText.utf8(resourceUri.toString())));
	}
}
