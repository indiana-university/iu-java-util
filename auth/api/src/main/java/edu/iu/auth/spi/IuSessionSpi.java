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
package edu.iu.auth.spi;

import java.security.Principal;

import edu.iu.auth.session.IuSessionAttribute;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionToken;

/**
 * Session token service provider interface.
 */
public interface IuSessionSpi {

	/**
	 * Creates a session token.
	 * 
	 * @param header {@link IuSessionHeader}
	 * @return {@link IuSessionToken}
	 */
	IuSessionToken create(IuSessionHeader header);

	/**
	 * Refreshes a session token.
	 * 
	 * @param authorizedPrincipals authorized principals
	 * @param refreshToken         refresh token
	 * @return {@link IuSessionToken}
	 */
	IuSessionToken refresh(Iterable<Principal> authorizedPrincipals, String refreshToken);

	/**
	 * Authorizes a session token.
	 * 
	 * @param accessToken access token
	 * @return {@link IuSessionToken}
	 */
	IuSessionToken authorize(String accessToken);

	/**
	 * Creates a session attribute.
	 * 
	 * @param <T>            attribute type
	 * @param name           principal name
	 * @param attributeName  attribute name
	 * @param attributeValue attribute value
	 * @return {@link IuSessionAttribute}
	 */
	<T> IuSessionAttribute<T> createAttribute(String name, String attributeName, T attributeValue);

}
