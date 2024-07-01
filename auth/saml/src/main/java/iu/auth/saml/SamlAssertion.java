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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.saml.IuSamlAssertion;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Validates, decodes, and holds attribute, condition, and authentication
 * statement values from a SAML Assertion.
 */
final class SamlAssertion implements IuSamlAssertion {

	private static final Logger LOG = Logger.getLogger(SamlServiceProvider.class.getName());

	/**
	 * JSON type adapter for timestamp values.
	 */
	static IuJsonAdapter<Instant> NUMERIC_DATE = IuJsonAdapter
			.from(v -> Instant.ofEpochSecond(((JsonNumber) v).longValue()), v -> IuJson.number(v.getEpochSecond()));

	/**
	 * JSON type adapter.
	 */
	static IuJsonAdapter<SamlAssertion> JSON = IuJsonAdapter.from(SamlAssertion::new, SamlAssertion::toJson);

	private static String readStringAttribute(Attribute attribute) {
		Object attrval = attribute.getAttributeValues().get(0);
		if (attrval instanceof XSString)
			return ((XSString) attrval).getValue();
		else
			return ((XSAny) attrval).getTextContent();
	}

	private final Instant notBefore;
	private final Instant notOnOrAfter;
	private final Instant authnInstant;
	private final Map<String, String> attributes;

	/**
	 * Constructor.
	 * 
	 * @param validator         OpenSAML Assertion Validator
	 * @param validationContext OpenSAML Validation Context
	 * @param assertion         Unmarshalled OpenSAML Assertion
	 */
	SamlAssertion(SAML20AssertionValidator validator, ValidationContext validationContext, Assertion assertion) {
		IuException.unchecked(() -> validator.validate(assertion, validationContext));

		final Map<String, String> attributes = new LinkedHashMap<>();

		LOG.fine(() -> "SAML2 assertion " + XmlDomUtil.getContent(assertion.getDOM()));

		for (AttributeStatement attributeStatement : assertion.getAttributeStatements())
			for (Attribute attribute : attributeStatement.getAttributes()) {
				final var value = readStringAttribute(attribute);

				for (final var name : IuIterable.filter(IuIterable.iter( //
						attribute.getName(), //
						attribute.getFriendlyName() //
				), Objects::nonNull))
					IuObject.once(value, attributes.put(name, value));
			}

		final Conditions conditions = assertion.getConditions();
		if (conditions != null) {
			notBefore = conditions.getNotBefore();
			notOnOrAfter = conditions.getNotOnOrAfter();
		} else {
			notBefore = null;
			notOnOrAfter = null;
		}

		Instant authnInstant = null;
		for (AuthnStatement statement : assertion.getAuthnStatements())
			authnInstant = IuObject.once(authnInstant, statement.getAuthnInstant());
		this.authnInstant = authnInstant;

		this.attributes = Collections.unmodifiableMap(attributes);
	}

	private SamlAssertion(JsonValue value) {
		final var claims = value.asJsonObject();
		notBefore = IuJson.get(claims, "nbf", NUMERIC_DATE);
		notOnOrAfter = IuJson.get(claims, "exp", NUMERIC_DATE);
		authnInstant = IuJson.get(claims, "authn_instant", NUMERIC_DATE);
		attributes = IuJson.get(claims, "attribute_statement");
	}

	@Override
	public Instant getNotBefore() {
		return notBefore;
	}

	@Override
	public Instant getNotOnOrAfter() {
		return notOnOrAfter;
	}

	@Override
	public Instant getAuthnInstant() {
		return authnInstant;
	}

	@Override
	public Map<String, String> getAttributes() {
		return attributes;
	}

	@Override
	public String toString() {
		return JSON.toJson(this).toString();
	}

	private JsonObject toJson() {
		final var builder = IuJson.object();
		IuJson.add(builder, "nbf", this::getNotBefore, NUMERIC_DATE);
		IuJson.add(builder, "exp", this::getNotOnOrAfter, NUMERIC_DATE);
		IuJson.add(builder, "authn_instant", this::getAuthnInstant, NUMERIC_DATE);
		IuJson.add(builder, "attribute_statement", this::getAttributes, IuJsonAdapter.basic());
		return builder.build();
	}
}
