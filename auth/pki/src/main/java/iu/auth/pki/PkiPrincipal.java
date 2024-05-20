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

import java.io.IOException;
import java.util.Objects;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebKey;

/**
 * PKI principal identity implementation class.
 */
final class PkiPrincipal implements IuPrincipalIdentity {
	private static final long serialVersionUID = 1L;

	private final String serializedVerifyKey;
	private final String serializedEncryptKey;

	private transient WebKey verify;
	private transient WebKey encrypt;

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		verify = IuObject.convert(serializedVerifyKey, WebKey::parse);
		encrypt = IuObject.convert(serializedEncryptKey, WebKey::parse);
	}

	/**
	 * Constructor.
	 * 
	 * @param verify  fully populated JWK including private/secret key data
	 * @param encrypt fully populated JWK including private/secret key data
	 */
	PkiPrincipal(WebKey verify, WebKey encrypt) {
		this.verify = verify;
		this.encrypt = encrypt;
		getName(); // verify keys are well-formed

		this.serializedVerifyKey = IuObject.convert(verify, a -> a.wellKnown().toString());
		this.serializedEncryptKey = IuObject.convert(encrypt, a -> a.wellKnown().toString());
	}

	@Override
	public String getName() {
		return X500Utils.getCommonName(wellKnown().getCertificateChain()[0].getSubjectX500Principal());
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);

		final var priv = subject.getPrivateCredentials();
		final var pub = subject.getPublicCredentials();

		if (verify != null) {
			if (verify.getPrivateKey() != null)
				priv.add(verify);
			pub.add(verify.wellKnown());
		}

		if (encrypt != null) {
			if (encrypt.getPrivateKey() != null)
				priv.add(encrypt);
			pub.add(encrypt.wellKnown());
		}

		subject.setReadOnly();
		return subject;
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		final var key = key();
		if (key == null //
				|| key.getPrivateKey() == null)
			sb.append("Well-Known");
		else
			sb.append("Authoritative");
		sb.append(" PKI Principal ").append(getName());

		final var certChain = wellKnown().getCertificateChain();
		if (certChain.length == 1
				&& certChain[0].getSubjectX500Principal().equals(certChain[0].getIssuerX500Principal()))
			sb.append(", Self-Issued");
		else
			sb.append(", Issued by ")
					.append(X500Utils.getCommonName(certChain[certChain.length - 1].getIssuerX500Principal()));

		return sb.toString();
	}

	/**
	 * Gets the identifying key
	 * 
	 * @return identifying key; verify key if non-null, else encrypt key
	 */
	WebKey key() {
		return Objects.requireNonNullElse(verify, encrypt);
	}

	private WebKey wellKnown() {
		return key().wellKnown();
	}
	
}
