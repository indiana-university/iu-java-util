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
 * Provides delegated access to authentication and authorization utilities.
 * 
 * <img src="doc-files/iu.util.auth.bundle.svg" alt="UML Deployment Diagram">
 * 
 * @uses edu.iu.auth.spi.IuBasicAuthSpi implementation module
 * @uses edu.iu.auth.spi.IuOAuthSpi implementation module
 * @uses edu.iu.auth.spi.IuOpenIdConnectSpi implementation module
 * @provides edu.iu.auth.spi.IuBasicAuthSpi delegates to implementation module
 * @provides edu.iu.auth.spi.IuOAuthSpi delegates to implementation module
 * @provides edu.iu.auth.spi.IuOpenIdConnectSpi delegates to implementation
 *           module
 */
module iu.util.auth.bundle {
	requires transitive iu.util.auth;
	requires iu.util.type.base;

	uses edu.iu.auth.spi.IuBasicAuthSpi;
	uses edu.iu.auth.spi.IuOAuthSpi;
	uses edu.iu.auth.spi.IuOpenIdConnectSpi;

	provides edu.iu.auth.spi.IuBasicAuthSpi with iu.auth.bundle.BasicAuthSpiDelegate;
	provides edu.iu.auth.spi.IuOAuthSpi with iu.auth.bundle.OAuthSpiDelegate;
	provides edu.iu.auth.spi.IuOpenIdConnectSpi with iu.auth.bundle.OidcSpiDelegate;
}
