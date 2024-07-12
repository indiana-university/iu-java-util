/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.auth.saml;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * SAML {@link IuPrincipalIdentity} implementation.
 */
public final class SamlPrincipal implements IuPrincipalIdentity {
	static {
		IuObject.assertNotOpen(SamlPrincipal.class);
	}

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
	SamlPrincipal(String realm, String entityId, String name, Instant issueTime, Instant authTime, Instant expires,
			Iterable<SamlAssertion> samlAssertions) {
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
	 * @throws IuAuthenticationException if the session is expired
	 */
	void verify(String realm) throws IuAuthenticationException {
		if (!this.realm.equals(realm))
			throw new IllegalArgumentException("invalid realm");

		if (Instant.now().isAfter(expires))
			throw new IuAuthenticationException(null, new IllegalStateException("expired"));
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
