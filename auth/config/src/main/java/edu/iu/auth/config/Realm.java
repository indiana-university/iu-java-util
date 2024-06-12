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

import java.util.Arrays;

/**
 * Provides realm configuration.
 */
public interface Realm {

	/**
	 * Enumerates authentication realm types.
	 */
	enum Type {

		/**
		 * Token issuer.
		 */
		TOKEN("token_endpoint", TokenEndpoint.class);

		private String code;
		private Class<? extends Realm> authenticationInterface;

		private Type(String code, Class<? extends Realm> authenticationInterface) {
			this.code = code;
			this.authenticationInterface = authenticationInterface;
		}

		/**
		 * Gets an authentication realm type by configuration code
		 * 
		 * @param code configuration code
		 * @return authentication realm type
		 */
		static Type from(String code) {
			return Arrays.stream(Type.class.getEnumConstants()).filter(a -> a.code.equals(code)).findFirst().get();
		}
	}

	/**
	 * Gets the configuration for a realm.
	 * 
	 * @param <R>  authentication realm type
	 * @param name realm name
	 * @return realm configuration
	 */
	@SuppressWarnings("unchecked")
	static <R extends Realm> R of(String name) {
		return (R) AuthConfig.load(Realm.class, "realm/" + name,
				config -> Type.from(config.getString("type")).authenticationInterface);
	}

}
