/*
 * Copyright © 2025 Indiana University
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
package iu.auth.nonce;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.nonce.IuAuthorizationChallenge;

/**
 * Broadcasts a one-time number that has been issued to a client.
 * 
 * <p>
 * When received: the nonce value is validated and queued for later
 * verification.
 * </p>
 */
final class IssuedChallenge implements IuAuthorizationChallenge {

	/**
	 * Validates remote address and user agent, and creates a client footprint.
	 * 
	 * @param remoteAddress remote address
	 * @param userAgent     user agent
	 * @return client thumbprint
	 */
	static byte[] thumbprint(String remoteAddress, String userAgent) {
		IuWebUtils.validateUserAgent(userAgent);
		return IuDigest.sha256(IuText.utf8(IuWebUtils.getInetAddress(remoteAddress).getHostAddress() + userAgent));
	}

	private final String nonce = IdGenerator.generateId();
	private final byte[] clientThumbprint;

	/**
	 * Creates a message that indicates the one-time number has been created.
	 * 
	 * @param remoteAddress remote address
	 * @param userAgent     user agent
	 */
	IssuedChallenge(String remoteAddress, String userAgent) {
		IuWebUtils.validateUserAgent(userAgent);
		this.clientThumbprint = thumbprint(remoteAddress, userAgent);
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	@Override
	public byte[] getClientThumbprint() {
		return clientThumbprint;
	}

}
