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
package iu.pki;

import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.X500Utils;
import edu.iu.pki.IuPkiPrincipal;

/**
 * PKI principal identity implementation class.
 */
public final class PkiPrincipal implements IuPkiPrincipal {
	static {
		IuObject.assertNotOpen(PkiPrincipal.class);
	}

	private final String name;
	private final WebKey jwk;

	/**
	 * Constructor.
	 * 
	 * @param jwk Identity {@link WebKey}
	 */
	public PkiPrincipal(WebKey jwk) {
		this.jwk = jwk;

		final var certificateChain = Objects.requireNonNull( //
				WebCertificateReference.verify(jwk), "missing certificate chain");

		final var cert = certificateChain[0];
		name = X500Utils.getCommonName(cert.getSubjectX500Principal());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public WebKey getJwk() {
		return jwk;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(jwk);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		PkiPrincipal other = (PkiPrincipal) obj;
		return IuObject.equals(jwk, other.jwk);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append("PKI Principal ").append(name);

		final var certChain = jwk.getCertificateChain();
		final var issuer = certChain[certChain.length - 1].getIssuerX500Principal();
		final var subject = certChain[0].getSubjectX500Principal();
		if (subject.equals(issuer))
			sb.append(", Self-Issued");
		else
			sb.append(", Issued by ").append(X500Utils.getCommonName(issuer));

		return sb.toString();
	}

}
