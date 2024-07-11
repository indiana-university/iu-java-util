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
/**
 * API Authentication and Authorization interfaces.
 * 
 * <img src="doc-files/iu.util.auth.svg" alt="UML Class Diagram">
 * 
 * @uses edu.iu.auth.spi.IuAuthConfigSpi Configuration bootstrap
 * @uses edu.iu.auth.spi.IuPrincipalSpi For access to identity provider
 *       verification resources
 * @uses edu.iu.auth.spi.IuNonceSpi One-time number generator
 * @uses edu.iu.auth.spi.IuBasicAuthSpi For access to HTTP basic auth resources
 * @uses edu.iu.auth.spi.IuJwtSpi for access to JWT implementation resources
 * @uses edu.iu.auth.spi.IuOAuthSpi For access to OAuth 2.0 implementation
 *       resources
 * @uses edu.iu.auth.spi.IuSamlSpi For access to SAML provider resources
 */
module iu.util.auth {
	exports edu.iu.auth;
	exports edu.iu.auth.basic;
	exports edu.iu.auth.jwt;
	exports edu.iu.auth.oauth;
	exports edu.iu.auth.oidc;
	exports edu.iu.auth.saml;
	exports edu.iu.auth.spi;

	requires iu.util;
	requires transitive java.net.http;

	uses edu.iu.auth.spi.IuPrincipalSpi;
	uses edu.iu.auth.spi.IuAuthConfigSpi;
	uses edu.iu.auth.spi.IuNonceSpi;
	uses edu.iu.auth.spi.IuBasicAuthSpi;
	uses edu.iu.auth.spi.IuJwtSpi;
	uses edu.iu.auth.spi.IuOAuthSpi;
	uses edu.iu.auth.spi.IuSamlSpi;
}
