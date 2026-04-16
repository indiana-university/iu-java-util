/*
 * Copyright © 2026 Indiana University
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
package iu.saml;

import java.time.Instant;

import edu.iu.saml.IuSamlAssertion;

/**
 * SAML session details interface
 */
public interface SamlPostAuthentication {

	/**
	 * Get invalid status
	 * 
	 * @return invalid status
	 */
	boolean isInvalid();

	/**
	 * Set invalid status
	 * 
	 * @param invalid invalid status
	 */
	void setInvalid(boolean invalid);

	/**
	 * Get name
	 * 
	 * @return name
	 */
	String getName();

	/**
	 * Set name
	 * 
	 * @param name name
	 */
	void setName(String name);

	/**
	 * Get authenticating authority
	 * 
	 * @return authenticating authority
	 */
	String getAuthnAuthority();

	/**
	 * Set authenticating authority
	 * 
	 * @param authnAuthority authenticating authority
	 */
	void setAuthnAuthority(String authnAuthority);

	/**
	 * Gets authentication time.
	 * 
	 * @return authentication time
	 */
	Instant getAuthnInstant();

	/**
	 * Sets authentication time.
	 * 
	 * @param authnInstant authentication time
	 */
	void setAuthnInstant(Instant authnInstant);

	/**
	 * Gets expiration time.
	 * 
	 * @return expiration time
	 */
	Instant getExpires();

	/**
	 * Sets expiration time.
	 * 
	 * @param expires expiration time
	 */
	void setExpires(Instant expires);

	/**
	 * Get SAML assertions
	 * 
	 * @return SAML assertions
	 */
	Iterable<IuSamlAssertion> getAssertions();

	/**
	 * Set SAML assertions
	 * 
	 * @param assertions SAML assertions
	 */
	void setAssertions(Iterable<IuSamlAssertion> assertions);

}
