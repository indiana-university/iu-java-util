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
package iu.auth.principal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import edu.iu.IuException;
import edu.iu.UnsafeBiConsumer;
import edu.iu.UnsafeConsumer;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuPrincipalSpi;

/**
 * Provides identity verification support for internal implementation modules.
 */
public class PrincipalVerifierRegistry implements IuPrincipalSpi {

	private static final class Delegate<T extends IuPrincipalIdentity> {
		private final Class<T> type;
		private final Function<T, IuPrincipalIdentity> unwrap;

		private Delegate(Class<T> type, Function<T, IuPrincipalIdentity> unwrap) {
			this.type = type;
			this.unwrap = unwrap;
		}
	}

	private static Map<Class<?>, Delegate<?>> DELEGATES = new WeakHashMap<>();
	private static Map<String, Verifier> VERIFIERS = new HashMap<>();

	/**
	 * Determines if the indicated realm has been registered as authoritative for
	 * the local node.
	 * 
	 * @param realm authentication realm
	 * @return true of the local node registered as authoritative for the realm
	 */
	public static boolean isAuthoritative(String realm) {
		return VERIFIERS.containsKey(realm) && VERIFIERS.get(realm).authoritative;
	}

	/**
	 * Registers a principal identity verifier for an authentication realm.
	 * 
	 * @param realm         authentication realm
	 * @param idConsumer    {@link Consumer}, accepts {@link IuPrincipalIdentity}
	 *                      and throws a runtime exception (i.e.,
	 *                      {@link IllegalArgumentException}) if verification fails.
	 * @param authoritative true if the verifier provides authoritative confirmation
	 *                      of the principal identity; false if confirmation
	 *                      delegates trust to a remote provider
	 */
	public static synchronized void registerVerifier(String realm, UnsafeConsumer<IuPrincipalIdentity> idConsumer,
			boolean authoritative) {
		if (VERIFIERS.containsKey(realm))
			throw new IllegalStateException("verifier already registered for realm");
		VERIFIERS.put(realm, new Verifier(idConsumer, authoritative));
	}

	/**
	 * Registers a implementation class capable of unwrapping a delegated identity.
	 * 
	 * @param <T>      delegate type
	 * @param delegate delegate class
	 * @param unwrap   function for unwrapping the delegated identity
	 */
	public static synchronized <T extends IuPrincipalIdentity> void registerDelegate(Class<T> delegate,
			Function<T, IuPrincipalIdentity> unwrap) {
		if (DELEGATES.containsKey(delegate))
			throw new IllegalStateException("delegate already registered");
		DELEGATES.put(delegate, new Delegate<>(delegate, unwrap));
	}

	/**
	 * Default constructor.
	 */
	public PrincipalVerifierRegistry() {
	}

	@Override
	public <T extends IuPrincipalIdentity> void verify(T id, String realm) throws IuAuthenticationException {
		final var verifier = Objects.requireNonNull(VERIFIERS.get(realm), "missing verifier for realm");

		IuPrincipalIdentity resolvedId = id;
		Class<?> type = resolvedId.getClass();
		while (DELEGATES.containsKey(type)) {
			@SuppressWarnings("unchecked")
			final var delegate = (Delegate<T>) DELEGATES.get(type);
			resolvedId = delegate.unwrap.apply(delegate.type.cast(resolvedId));
			type = resolvedId.getClass();
		}

		final var principal = resolvedId;
		IuException.checked(IuAuthenticationException.class, () -> verifier.idConsumer.accept(principal));
	}

}
