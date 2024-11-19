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
	 * Get authentication realm
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Set authentication realm
	 * 
	 * @param realm authentication realm
	 */
	void setRealm(String realm);

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
	 * Get issue time
	 * 
	 * @return issue time
	 */
	Instant getIssueTime();

	/**
	 * Set issue time
	 * 
	 * @param issueTime issue time
	 */
	void setIssueTime(Instant issueTime);

	/**
	 * Get authentication time
	 * 
	 * @return authentication time
	 */
	Instant getAuthTime();

	/**
	 * Set authentication time
	 * 
	 * @param authTime authentication time
	 */
	void setAuthTime(Instant authTime);

	/**
	 * Get expires
	 * 
	 * @return expires
	 */
	Instant getExpires();

	/**
	 * Set expires
	 * 
	 * @param expires expires
	 */
	void setExpires(Instant expires);

	/**
	 * Get SAAML assertions
	 * 
	 * @return SAML assertions
	 */
	Iterable<StoredSamlAssertion> getAssertions();

	/**
	 * Set SAML assertions
	 * 
	 * @param assertions SAML assertions
	 */
	void setAssertions(Iterable<StoredSamlAssertion> assertions);

}
