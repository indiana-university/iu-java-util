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
package edu.iu.auth.pki;

import java.security.cert.CertPath;
import java.security.cert.CertPathParameters;
import java.security.cert.X509Certificate;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuPkiSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Encapsulates a PKI principal.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5280">RFC-5280 X.509
 *      PKI</a>
 */
public interface IuPkiPrincipal extends IuPrincipalIdentity {

	/**
	 * Reads a PKI principal from serialized form.
	 * 
	 * <p>
	 * Serialized PKI certs <em>must</em> include:
	 * </p>
	 * <ul>
	 * <li>An {@link X509Certificate} with
	 * <ul>
	 * <li>{@link X509Certificate#getBasicConstraints() V3 basic constraints}</li>
	 * <li>{@link X509Certificate#getKeyUsage() Key usage} describing a single
	 * scenario</li>
	 * <li>X500 subject with an RDN containing one of
	 * <ul>
	 * <li>CN attribute containing a system principal URI with fragment
	 * naming/matching the JWK "kid" parameter</li>
	 * <li>CN attribute containing a system principal URI, with no fragment,
	 * matching the JWK "kid" parameter</li>
	 * <li>UID attribute with user principal name, <em>optionally</em> qualified
	 * with DC attribute values</li>
	 * </ul>
	 * </ul>
	 * <li>A private key matching the certificate's public key</li>
	 * <li>Additional certificates as needed to form a chain to a certificate issued
	 * by {@link #trust(String, CertPathParameters) trusted} signing
	 * certificate.</li>
	 * </ul>
	 * 
	 * <p>
	 * Self-signed end-entity will be trusted as authoritative for an authentication
	 * realm named by the subject CN attribute. Non end-entity certificates <em>must
	 * not</em> be self-signed.
	 * </p>
	 * 
	 * @param serialized PEM encoded
	 * @return {@link IuPkiPrincipal}
	 */
	static IuPkiPrincipal from(String serialized) {
		return IuAuthSpiFactory.get(IuPkiSpi.class).readPkiPrincipal(serialized);
	}

	/**
	 * Registers a set of trusted signing certificates for an authentication realm.
	 * 
	 * <p>
	 * <em>May</em> only be called once per authentication realm.
	 * </p>
	 * 
	 * @param realm authentication realm
	 * @param store trusted certificate store
	 */
	static void trust(String realm, CertPathParameters store) {
		IuAuthSpiFactory.get(IuPkiSpi.class).trust(realm, store);
	}

	/**
	 * Gets the certificate path.
	 * 
	 * @return {@link CertPath}
	 */
	CertPath getCertPath();

}
