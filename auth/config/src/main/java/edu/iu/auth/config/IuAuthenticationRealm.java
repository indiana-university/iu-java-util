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
package edu.iu.auth.config;

import java.security.cert.X509Certificate;
import java.util.Arrays;

import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.client.IuJsonAdapter;

/**
 * Provides realm configuration.
 */
public interface IuAuthenticationRealm {

	/**
	 * Enumerates authentication realm types.
	 */
	enum Type {

		/**
		 * OIDC client credentials.
		 */
		CREDENTIALS("credentials", Credentials.class),

		/**
		 * PKIX {@link X509Certificate} trusted End-Entity.
		 */
		PKI("pki", IuPrivateKeyPrincipal.class),

		/**
		 * PKIX {@link X509Certificate} trusted CA.
		 */
		CA("ca", IuCertificateAuthority.class),

		/**
		 * SAML Service Provider.
		 */
		SAML("saml_sp", IuSamlServiceProviderMetadata.class),
		
		
		/**
		 * Session attributes.
		 */
		SESSION("session", IuSessionConfiguration.class);

		/**
		 * Code used for validating config data.
		 */
		String code;

		private Class<? extends IuAuthenticationRealm> authInterface;

		private Type(String code, Class<? extends IuAuthenticationRealm> authenticationInterface) {
			this.code = code;
			this.authInterface = authenticationInterface;
		}

		/** JSON type adapter */
		public static IuJsonAdapter<Type> JSON = IuJsonAdapter.text(Type::from, a -> a.code);

		/**
		 * Gets an authentication realm type by configuration code
		 * 
		 * @param code configuration code
		 * @return authentication realm type
		 */
		public static Type from(String code) {
			return Arrays.stream(Type.class.getEnumConstants()).filter(a -> a.code.equals(code)).findFirst().get();
		}

		/**
		 * Verifies the type of realm matches this type.
		 * 
		 * @param realm {@link IuAuthenticationRealm}
		 */
		void check(IuAuthenticationRealm realm) {
			if (!authInterface.isInstance(realm))
				throw new IllegalStateException("Invalid realm type for " + code);
		}
	}

	/**
	 * Verifies that {@link #getType()} may be used to inform a decision to cast an
	 * instance to a specific subclass.
	 * 
	 * @param realm {@link IuAuthenticationRealm}
	 */
	static void verify(IuAuthenticationRealm realm) {
		realm.getType().check(realm);
	}

	/**
	 * Gets the authentication realm type.
	 * 
	 * @return {@link Type}
	 */
	Type getType();

}
