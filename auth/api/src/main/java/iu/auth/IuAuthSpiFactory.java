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
package iu.auth;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

/**
 * Maintains singleton instances of SPI interfaces related to API authorization
 * and authentication.
 * 
 * <p>
 * SPI providers <em>must</em> be visible to the same class loader that loaded
 * the service interface.
 * </p>
 */
public class IuAuthSpiFactory {

	private static class Provider<P> {
		P instance;
	}

	private static final Map<Class<?>, Provider<?>> PROVIDERS = new WeakHashMap<>();

	/**
	 * Gets a singleton instance of a service interface.
	 * 
	 * @param <P>              service type
	 * @param serviceInterface service interface
	 * @return singleton instance
	 */
	@SuppressWarnings("unchecked")
	public static <P> P get(Class<P> serviceInterface) {
		final Provider<P> provider;
		synchronized (PROVIDERS) {
			if (PROVIDERS.containsKey(serviceInterface))
				provider = (Provider<P>) PROVIDERS.get(serviceInterface);
			else
				PROVIDERS.put(serviceInterface, provider = new Provider<>());
		}

		synchronized (provider) {
			if (provider.instance == null)
				provider.instance = ServiceLoader.load(serviceInterface, serviceInterface.getClassLoader()).findFirst()
						.get();
		}

		return provider.instance;
	}

	private IuAuthSpiFactory() {
	}

}
