/*
 * Copyright © 2025 Indiana University
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

	/**
	 * Gets the {@code authnAuthority} IDP entity ID.
	 * 
	 * @return authenticating authority
	 */
	String getAuthnAuthority();

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
