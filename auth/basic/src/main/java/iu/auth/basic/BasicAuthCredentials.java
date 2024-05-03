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
package iu.auth.basic;

import java.net.http.HttpRequest.Builder;
import java.util.Base64;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuException;
import edu.iu.auth.basic.IuBasicAuthCredentials;

/**
 * Implementation of {@link IuBasicAuthCredentials}.
 */
final class BasicAuthCredentials implements IuBasicAuthCredentials {
	private static final long serialVersionUID = 1L;

	/**
	 * Name.
	 */
	private final String name;

	/**
	 * Password.
	 */
	private final String password;

	/**
	 * Character set.
	 */
	private final String charset;

	/**
	 * Constructor.
	 * 
	 * @param name     username
	 * @param password password
	 * @param charset  character set
	 */
	BasicAuthCredentials(String name, String password, String charset) {
		this.name = name;
		this.password = password;
		this.charset = charset;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getCharset() {
		return charset;
	}

	@Override
	public Subject getSubject() {
		return new Subject(true, Set.of(this), Set.of(), Set.of());
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) {
		httpRequestBuilder.header("Authorization", "Basic " + Base64.getUrlEncoder()
				.encodeToString(IuException.unchecked(() -> (getName() + ':' + password).getBytes(charset))));
	}

	@Override
	public String toString() {
		return "BasicAuthCredentials [" + name + "]";
	}

}
