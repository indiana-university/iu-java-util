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
package iu.auth.pki;

import static org.mockito.Mockito.mock;

import edu.iu.auth.config.IuCertificateAuthority;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.client.IuJson;
import edu.iu.client.IuVault;
import iu.auth.config.AuthConfig;

@SuppressWarnings("javadoc")
public class PkiTestCase {

	static {
		final var vault = mock(IuVault.class);
		AuthConfig.registerInterface("realm", IuPrivateKeyPrincipal.class, vault);
		AuthConfig.registerInterface("realm", IuCertificateAuthority.class, vault);
	}

	static IuPrivateKeyPrincipal pkp(String pkp) {
		return AuthConfig.adaptJson(IuPrivateKeyPrincipal.class).fromJson(IuJson.parse(pkp));
	}

	static IuCertificateAuthority ca(String ca) {
		return AuthConfig.adaptJson(IuCertificateAuthority.class).fromJson(IuJson.parse(ca));
	}

}
