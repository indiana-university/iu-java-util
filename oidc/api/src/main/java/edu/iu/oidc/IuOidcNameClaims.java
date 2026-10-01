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
package edu.iu.oidc;

/**
 * The claims an OpenID Provider may assert about an end user's name.
 *
 * @see IuOidcClaims
 */
public interface IuOidcNameClaims {

	/**
	 * Gets the end user's full name, in displayable form, including every part and
	 * any suffix or title.
	 *
	 * @return {@code name} claim; null if not known
	 */
	default String getName() {
		return null;
	}

	/**
	 * Gets the given name or first name, which may carry more than one name.
	 *
	 * @return {@code given_name} claim; null if not known
	 */
	default String getGivenName() {
		return null;
	}

	/**
	 * Gets the surname or last name, which may carry more than one name.
	 *
	 * @return {@code family_name} claim; null if not known
	 */
	default String getFamilyName() {
		return null;
	}

	/**
	 * Gets the middle name, which may carry more than one name.
	 *
	 * @return {@code middle_name} claim; null if not known
	 */
	default String getMiddleName() {
		return null;
	}

	/**
	 * Gets the casual name the end user is referred to by, which may or may not be
	 * their {@link #getGivenName() given name}.
	 *
	 * @return {@code nickname} claim; null if not known
	 */
	default String getNickname() {
		return null;
	}

}
