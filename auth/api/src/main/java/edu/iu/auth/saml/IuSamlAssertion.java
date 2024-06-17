package edu.iu.auth.saml;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an assertion issued by a SAML Identity Provider.
 * 
 * @see <a href=
 *      "https://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html">SAML
 *      2.0</a>
 */
public interface IuSamlAssertion {

	/**
	 * OID for the common {@code eduPersonPrincipalName} attribute.
	 */
	static final String EDU_PERSON_PRINCIPAL_NAME_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";

	/**
	 * OID for the common {@code displayName} attribute.
	 */
	static final String DISPLAY_NAME_OID = "urn:oid:2.16.840.1.113730.3.1.241";

	/**
	 * OID for the common {@code mail} (email address) attribute.
	 */
	static final String MAIL_OID = "urn:oid:0.9.2342.19200300.100.1.3";

	/**
	 * Gets the {@code notBefore} condition value.
	 * 
	 * @return {@link Instant}
	 */
	Instant getNotBefore();

	/**
	 * Gets the {@code notOnOrAfter} condition value.
	 * 
	 * @return {@link Instant}
	 */
	Instant getNotOnOrAfter();

	/** authnInstant */
	/**
	 * Gets the {@code authnInstant} authentication statement value.
	 * 
	 * @return {@link Instant}
	 */
	Instant getAuthnInstant();

	/**
	 * Gets attribute values.
	 * 
	 * @return {@link Map} of attribute values.
	 */
	Map<String, String> getAttributes();

}
