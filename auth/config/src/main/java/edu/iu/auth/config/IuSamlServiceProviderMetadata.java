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
package edu.iu.auth.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.IuIterable;
import edu.iu.auth.saml.IuSamlAssertion;

/**
 * Provides client configuration metadata for interacting with an SAML
 * authorization server.
 * 
 * <p>
 * The interface <em>should</em> be implemented by the application client module
 * requiring authorization on behalf of an SAML identity provider.
 * </p>
 */
public interface IuSamlServiceProviderMetadata extends IuAuthenticationRealm {

	/**
	 * Gets whether to fail on address mismatch or not, true if required, false if
	 * not
	 * 
	 * @return failed on address mismatch
	 */
	default boolean isFailOnAddressMismatch() {
		return false;
	}

	/**
	 * Gets the maximum length of time to allow an authenticated session to be
	 * remain active before requesting the provide re-establish credentials for the
	 * principal.
	 * 
	 * @return {@link Duration}, will be truncated to second
	 */
	default Duration getAuthenticatedSessionTimeout() {
		return Duration.ofHours(12L);
	}

	/**
	 * Gets the maximum time interval to re-established metadata resolver typically
	 * measured in seconds. Once this interval is passed, metadata resolver will be
	 * re-established using metadata URIs.
	 * 
	 * @return metadaaTtl {@link Duration}
	 */
	default Duration getMetadataTtl() {
		return Duration.ofMinutes(5L);
	}

	/**
	 * Gets the application entry point URI, where users will be returned after
	 * successfully authenticating via the Service Provider.
	 * 
	 * @return {@link URI}
	 */
	URI getEntryPointUri();

	/**
	 * Gets allowed list of IP addresses to validate against SAML response
	 * 
	 * @return allowed ranged of IP addresses
	 */
	default Iterable<String> getAllowedRange() {
		return IuIterable.empty();
	}

	/**
	 * Gets the SAML metadata {@link URI} to retrieve configure metadata file that
	 * is configured directly into the SAML provider by administrator
	 * 
	 * @return metadata URL
	 */
	Iterable<URI> getMetadataUris();

	/**
	 * Gets the list of assertion Consumer {@link URI}
	 * 
	 * @return allowed list of assertion consumer {@link URI}
	 */
	Iterable<URI> getAcsUris();

	/**
	 * Gets the Service Provider registered Entity ID.
	 * 
	 * @return SP Entity ID
	 */
	String getServiceProviderEntityId();

	/**
	 * Gets the Identity Provider registered Entity ID.
	 * 
	 * @return IDP Entity ID
	 */
	String getIdentityProviderEntityId();

	/**
	 * Gets the SAML Service Provider identity keys.
	 * 
	 * @return SAML SP identity keys
	 */
	IuPrivateKeyPrincipal getIdentity();

	/**
	 * Gets the name of the SAML Assertion Attribute that contains the principal
	 * name.
	 * 
	 * <p>
	 * At least one assertion <em>must</em> include this attribute value.
	 * </p>
	 * 
	 * @return principal name attribute
	 */
	default String getPrincipalNameAttribute() {
		return IuSamlAssertion.EDU_PERSON_PRINCIPAL_NAME_OID;
	}

}
