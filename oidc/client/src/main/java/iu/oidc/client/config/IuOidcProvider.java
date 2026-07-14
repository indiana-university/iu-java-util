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
package iu.oidc.client.config;

import java.net.URI;
import java.time.Duration;

import edu.iu.oidc.IuOidcProviderMetadata;

/**
 * Client view of an OIDC provider.
 */
public interface IuOidcProvider {

	/**
	 * Gets configured metadata.
	 * 
	 * @return {@link IuOidcProviderMetadata}; null to use other properties to pull
	 *         from a well-known self-published configuration source.
	 */
	IuOidcProviderMetadata getMetadata();

	/**
	 * Gets the issuer URI.
	 * 
	 * @return issuer URI; null when providing {@link #getMetadata() configured
	 *         metadata}
	 */
	URI getIssuer();

	/**
	 * Gets the metadata configuration URI.
	 * 
	 * @return metadata configuration URI; null when providing {@link #getMetadata()
	 *         configured metadata}
	 */
	default URI getMetadataUri() {
		final var issuer = getIssuer();
		if (issuer == null)
			return null;
		else
			return URI.create(issuer + "/.well-known/openid-configuration");
	}

	/**
	 * Gets the metadata refresh interval.
	 * 
	 * @return {@link Duration}; null when providing {@link #getMetadata()
	 *         configured metadata}
	 */
	default Duration getMetadataTtl() {
		if (getMetadata() != null)
			return null;
		else
			return Duration.ofMinutes(15L);
	}

}
