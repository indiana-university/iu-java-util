package iu.auth.saml;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuIterable;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * SAML {@link IuPrincipalIdentity} implementation.
 */
final class SamlPrincipal implements IuPrincipalIdentity {

	/**
	 * JSON type adapter.
	 */
	static final IuJsonAdapter<SamlPrincipal> JSON = IuJsonAdapter.from(SamlPrincipal::new, SamlPrincipal::toJson);

	/** authentication realm */
	private final String realm;

	/** identity provider id */
	private final String entityId;

	/** name */
	private final String name;

	/** time authentication statement was issued by the IDP */
	private final Instant issueTime;

	/** authentication time */
	private final Instant authTime;

	/** expires */
	private final Instant expires;

	/** attributes */
	private final SamlAssertion[] assertions;

	/**
	 * Constructor.
	 * 
	 * @param realm          authentication realm
	 * @param entityId       entity ID
	 * @param name           principal name
	 * @param issueTime      time authentication statement was issued by the IDP
	 * @param authTime       authentication time
	 * @param expires        expire
	 * @param samlAssertions verified SAML assertions
	 */
	public SamlPrincipal(String realm, String entityId, String name, Instant issueTime, Instant authTime,
			Instant expires, Iterable<SamlAssertion> samlAssertions) {
		this.realm = Objects.requireNonNull(realm);
		this.entityId = Objects.requireNonNull(entityId);
		this.name = Objects.requireNonNull(name);
		this.authTime = Objects.requireNonNull(authTime);
		this.issueTime = Objects.requireNonNull(issueTime);
		this.expires = Objects.requireNonNull(expires);
		this.assertions = IuIterable.stream(samlAssertions).toArray(SamlAssertion[]::new);
	}

	private SamlPrincipal(JsonValue value) {
		final var claims = value.asJsonObject();
		this.name = claims.getString("sub");
		this.entityId = claims.getString("iss");
		this.realm = claims.getString("aud");
		this.issueTime = Instant.ofEpochSecond(claims.getJsonNumber("iat").longValue());
		this.expires = Instant.ofEpochSecond(claims.getJsonNumber("exp").longValue());
		this.authTime = Instant.ofEpochSecond(claims.getJsonNumber("auth_time").longValue());
		this.assertions = IuJson.get(claims, "urn:oasis:names:tc:SAML:2.0:assertion",
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Instant getIssuedAt() {
		return issueTime;
	}

	@Override
	public Instant getAuthTime() {
		return authTime;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.getPublicCredentials().addAll(List.of(assertions));
		subject.setReadOnly();
		return subject;
	}

	@Override
	public String toString() {
		return toJson().toString();
	}

	/**
	 * Verifies that the principal was issued by the indicated realm and has not
	 * expired.
	 * 
	 * @param realm authentication realm
	 */
	void verify(String realm) {
		if (!this.realm.equals(realm))
			throw new IllegalArgumentException("invalid realm");
		if (Instant.now().isAfter(expires))
			throw new IllegalArgumentException("expired");
	}

	private JsonObject toJson() {
		final var builder = IuJson.object() //
				.add("iss", entityId) //
				.add("aud", realm) //
				.add("sub", name) //
				.add("iat", issueTime.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authTime.getEpochSecond()); //
		IuJson.add(builder, "urn:oasis:names:tc:SAML:2.0:assertion", () -> assertions,
				IuJsonAdapter.of(SamlAssertion[].class, SamlAssertion.JSON));
		return builder.build();
	}

}
