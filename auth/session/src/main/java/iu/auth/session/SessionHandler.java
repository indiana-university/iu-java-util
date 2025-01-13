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
package iu.auth.session;

import java.net.HttpCookie;
import java.net.URI;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuDataStore;
import edu.iu.IuDigest;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.auth.session.IuSession;
import edu.iu.auth.session.IuSessionHandler;
import edu.iu.crypt.EphemeralKeys;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * {@link IuSessionHandler} implementation
 */
public class SessionHandler implements IuSessionHandler {
	static {
		IuObject.assertNotOpen(SessionHandler.class);
	}

	private static final Logger LOG = Logger.getLogger(SessionHandler.class.getName());

	private final URI resourceUri;
	private final IuSessionConfiguration configuration;
	private final Supplier<WebKey> issuerKey;
	private final Algorithm algorithm;
	private final IuDataStore dataStore;

	/**
	 * Constructor.
	 * 
	 * @param resourceUri   root protected resource URI
	 * @param configuration {#link {@link IuSessionConfiguration}
	 * @param issuerKey     issuer key supplier
	 * @param algorithm     algorithm
	 * @param dataStore     data store
	 */
	public SessionHandler(URI resourceUri, IuSessionConfiguration configuration, Supplier<WebKey> issuerKey,
			Algorithm algorithm, IuDataStore dataStore) {
		this.resourceUri = resourceUri;
		this.configuration = configuration;
		this.issuerKey = issuerKey;
		this.algorithm = algorithm;
		this.dataStore = dataStore;
	}

	@Override
	public IuSession create() {
		return new Session(resourceUri, configuration.getMaxSessionTtl());
	}

	@Override
	public IuSession activate(Iterable<HttpCookie> cookies) {
		final var cookieName = getSessionCookieName();

		String activatedSession = null;
		byte[] secretKey = null;
		if (cookies != null)
			for (final var cookie : cookies)
				if (cookie.getName().equals(cookieName))
					try {
						final var value = IuText.base64Url(cookie.getValue());

						final var hashKey = hashKey(value);
						final var session = dataStore.get(hashKey);
						if (session == null)
							continue;

						secretKey = value;
						activatedSession = IuText.utf8(session);
						break;
					} catch (Throwable e) {
						LOG.log(Level.INFO, "Invalid session cookie value", e);
					}

		if (activatedSession == null)
			return null;

		return new Session(activatedSession, secretKey, issuerKey.get(), configuration.getMaxSessionTtl());
	}

	@Override
	public String store(IuSession session) {
		final var secretKey = EphemeralKeys.secret("AES", 256);
		final var s = (Session) session;

		dataStore.put(hashKey(secretKey), IuText.utf8(s.tokenize(secretKey, issuerKey.get(), algorithm)),
				configuration.getInactiveTtl());

		final var cookieBuilder = new StringBuilder();
		cookieBuilder.append(getSessionCookieName());
		cookieBuilder.append('=');
		cookieBuilder.append(IuText.base64Url(secretKey));
		cookieBuilder.append("; Path=");

		final var path = resourceUri.getPath();
		if (path.isEmpty())
			cookieBuilder.append("/");
		else
			cookieBuilder.append(path);

		if (IuObject.equals(resourceUri.getScheme(), "https"))
			cookieBuilder.append("; Secure");

		cookieBuilder.append("; HttpOnly");

		if (s.isStrict())
			cookieBuilder.append("; SameSite=Strict");
		return cookieBuilder.toString();
	}

	@Override
	public void remove(Iterable<HttpCookie> cookies) {
		if (cookies != null) {
			final var cookieName = getSessionCookieName();
			for (final var cookie : cookies)
				if (cookie.getName().equals(cookieName))
					try {
						dataStore.put(hashKey(IuText.base64Url(cookie.getValue())), null);
					} catch (Throwable e) {
						LOG.log(Level.INFO, "Invalid session cookie value", e);
					}
		}
	}

	/**
	 * Gets the hash key to use for storing tokenized session data.
	 * 
	 * @param secretKey secret key data
	 * @return encoded digest of the session key
	 */
	static byte[] hashKey(byte[] secretKey) {
		return IuDigest.sha256(secretKey);
	}

	/**
	 * Gets the session cookie name for a protected resource URI
	 * 
	 * @return session cookie name
	 */
	String getSessionCookieName() {
		return "iu-sk_" + IuText.base64Url(IuDigest.sha256(IuText.utf8(resourceUri.toString())));
	}
}
