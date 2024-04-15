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
package iu.auth.pki;

import java.security.cert.CertPath;
import java.security.cert.X509Certificate;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.pki.IuPkiPrincipal;
import edu.iu.crypt.WebKey;

/**
 * {@link IuPkiPrincipal} implementation class.
 */
class PkiPrincipal implements IuPkiPrincipal {
	private static final long serialVersionUID = 1L;

	private transient WebKey key;
	private transient WebKey wellKnown;
	private final String serializedWellKnownKey;
	private final CertPath certPath;

	/**
	 * Constructor.
	 * 
	 * @param webKey    fully populated JWK including private/secret key data
	 * @param wellKnwon
	 * @param certPath
	 */
	PkiPrincipal(WebKey webKey, CertPath certPath) {
		this.wellKnown = webKey.wellKnown();
		this.key = wellKnown.equals(key) ? null : webKey;
		this.serializedWellKnownKey = wellKnown.toString();
		this.certPath = certPath;
	}

	@Override
	public String getName() {
		return X500Utils.getCommonName(((X509Certificate) certPath.getCertificates().get(0)).getSubjectX500Principal());
	}

	@Override
	public CertPath getCertPath() {
		return certPath;
	}

	@Override
	public Subject getSubject() {
		final var cert = ((X509Certificate) certPath.getCertificates().get(0));
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.getPrincipals().add(cert.getSubjectX500Principal());
		IuObject.convert(key, k -> {
			final var credentials = subject.getPrivateCredentials();
			credentials.add(k);
			credentials.add(k.getPrivateKey());
			return credentials;
		});
		subject.getPublicCredentials().add(wellKnown());
		subject.getPublicCredentials().add(certPath);
		subject.getPublicCredentials().add(cert);
		subject.getPublicCredentials().add(cert.getPublicKey());
		subject.setReadOnly();
		return subject;
	}

	private WebKey wellKnown() {
		if (wellKnown == null)
			wellKnown = WebKey.parse(serializedWellKnownKey);
		return wellKnown;
	}

}
