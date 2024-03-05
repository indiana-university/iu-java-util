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

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationScope;
import edu.iu.auth.oauth.IuAuthorizationSession;

/**
 * Service provider interface for OAuth.
 */
public interface IuOAuthSpi {

	/**
	 * Initializes client metadata.
	 * 
	 * @param client client metadata
	 * @return <em>optional</em> client credentials grant
	 * @see IuAuthorizationClient#initialize(IuAuthorizationClient)
	 */
	IuAuthorizationGrant initialize(IuAuthorizationClient client);

	/**
	 * Creates a new {@link IuAuthorizationSession} for managing OAuth authorization
	 * server interactions.
	 * 
	 * @param realm      authentication realm
	 * @param entryPoint entry point URI
	 * @return {@link IuAuthorizationSession}
	 */
	IuAuthorizationSession createAuthorizationSession(String realm, URI entryPoint);

	/**
	 * Creates an {@link IuAuthorizationScope}.
	 * 
	 * @param name  scope
	 * @param realm realm
	 * @return {@link IuAuthorizationScope}
	 */
	IuAuthorizationScope createAuthorizationScope(String name, String realm);

}