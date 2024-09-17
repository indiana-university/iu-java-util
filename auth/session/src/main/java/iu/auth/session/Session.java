package iu.auth.session;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.security.AlgorithmConstraints;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.session.IuSession;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * {@link IuSession} implementation
 */
public class Session implements IuSession {
	
	/** root protected resource URI */
	URI resourceUri;
	
	/** Session expiration time */
	Instant expires;
	
	/** Session creation time */
	Instant issueAt;
	
	/** change flag to determine when session attributes change */
	boolean isChange;

	
	/** Session details  */
	Map<String, Map<String, Object>> details;

	/**
	 * JSON type adapter for timestamp values.
	 */
	static IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter
			.from(v -> Instant.ofEpochSecond(((JsonNumber) v).longValue()), v -> IuJson.number(v.getEpochSecond()));

	/**
	 * JSON type adapter.
	 */
	static IuJsonAdapter<Session> JSON = IuJsonAdapter.from(Session::new, Session::toJson);

	/** 
	 * Constructor
	 * @param resourceUri root protected resource URI
	 * @param expires expiration time
	 */
	public Session(URI resourceUri, Duration expires) {
		this.resourceUri = resourceUri;
		this.issueAt = Instant.now();
		this.expires = issueAt.plus(expires.toMillis(), ChronoUnit.HOURS);
		details = new HashMap<String, Map<String,Object>>();
	}

	Session(JsonValue value) {
		final var claims = value.asJsonObject();
		issueAt = IuJson.get(claims, "iat", NUMERIC_DATE);
		expires = IuJson.get(claims, "exp", NUMERIC_DATE);
		details = IuJson.get(claims, "attributes");
	}

	/**
	 * Token constructor
	 * @param token tokenized session
	 * @param issuerKey issuer key
	 * @param secretKey Secret key to use for detokenizing the session.
	 */
	Session(String token, byte[] secretKey, WebKey issuerKey ) {
		final var key = WebKey.builder(Type.RAW).key(secretKey).build();
		final var decryptedToken = WebSignedPayload.parse(WebEncryption.parse(token).decryptText(key));
		final var tokenPayload = decryptedToken.getPayload();
		decryptedToken.verify(issuerKey);
		final var data = IuJson.parse(IuText.utf8(tokenPayload)).asJsonObject();
		String aud = Objects.requireNonNull(IuJson.get(data, "aud")); 
		IuObject.require(aud, a -> aud.equals(resourceUri.toString()), "Invalid audience");
		details = IuJson.get(data, "attributes");
	}

	/**
	 * Token constructor
	 * @param secretKey secret key
	 * @param issuerKey issuer key
	 * @param algorithm algorithm 
	 * @return tokenized session
	 */
	String tokenize(byte[] secretKey, WebKey issuerKey, Algorithm algorithm ) {
		return WebEncryption.builder(Encryption.A256GCM).compact() //
				.addRecipient(Algorithm.DIRECT) //
				.key(WebKey.builder(Type.RAW).key(secretKey).build())
				.encrypt(WebSignature.builder(algorithm).compact()
						.key(issuerKey) //
						.sign(toString()).compact()) //
				.compact();

	}


	@Override
	public <T> T getDetail(Class<T> type) {
		Map<String, Object> attributes = null;
		if (details.containsKey(type.getName())) {
			attributes = details.get(type.getName());
		}
		else {
			attributes = new HashMap<String, Object>();
			details.put(type.getName(), attributes);
		}
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
				new Class<?>[] { type }, new SessionDetail(attributes, this) ));


	}

	@Override
	public boolean isChange() {
		return isChange;
	}


	/**
	 * Sets the change flag
	 * @param isChange set to true when session attributes change, otherwise false 
	 */
	void setChange(boolean isChange) {
		this.isChange = isChange;
	}

	/**
	 * Gets session expire time
	 * @return {@link Instant} session expire time
	 */
	Instant getExpires() {
		return expires;
	}

	/**
	 * Gets session creation time
	 * @return {@link Instant} session creation time
	 */
	Instant getIssueAt() {
		return issueAt;
	}

	@Override
	public String toString() {
		return JSON.toJson(this).toString();
	}

	private JsonObject toJson() {
		final var builder = IuJson.object()//
				.add("iss", resourceUri.toString()) //
				.add("aud", resourceUri.toString()); //
		IuJson.add(builder, "iat",this::getIssueAt , NUMERIC_DATE);
		IuJson.add(builder, "exp", this::getExpires, NUMERIC_DATE);
		IuJson.add(builder, "details", this.details);
		return builder.build();
	}



}
