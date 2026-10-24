/*
 * Copyright © 2026 Indiana University
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
package iu.type;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import edu.iu.IuException;
import edu.iu.type.IuType;

/**
 * Tracks interceptor instances by target instance.
 *
 * <p>
 * Per Jakarta Interceptors, an interceptor instance has the same lifecycle as
 * the target instance it intercepts. Targets are held weakly, by identity, so
 * tracking never extends the life of a target; {@link #release(Object)} ends
 * the life of the interceptors explicitly when the target is destroyed.
 * </p>
 */
final class InterceptorInstances {

	private static final class Key extends WeakReference<Object> {
		private final int hash;

		private Key(Object target, ReferenceQueue<Object> queue) {
			super(target, queue);
			this.hash = System.identityHashCode(target);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			// only Keys are stored; a collected target equals only another collected one,
			// which expunge() removes either way
			return Objects.equals(get(), ((Key) obj).get());
		}
	}

	private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();
	private static final Map<Key, Map<Class<?>, Object>> INSTANCES = new ConcurrentHashMap<>();

	private InterceptorInstances() {
	}

	/**
	 * Removes entries for targets that have been garbage collected.
	 */
	private static void expunge() {
		Reference<?> expired;
		while ((expired = QUEUE.poll()) != null)
			INSTANCES.remove(expired);
	}

	/**
	 * Gets the interceptor instances for a target.
	 *
	 * @param target target instance
	 * @return interceptor instances by interceptor class, created as needed by the
	 *         caller
	 */
	static Map<Class<?>, Object> of(Object target) {
		expunge();
		return INSTANCES.computeIfAbsent(new Key(target, QUEUE), k -> new ConcurrentHashMap<>());
	}

	/**
	 * Gets the number of targets with tracked interceptor instances.
	 *
	 * @return count, including targets not yet expunged
	 */
	static int size() {
		return INSTANCES.size();
	}

	/**
	 * Hands interceptor instances used to construct a target to the target.
	 *
	 * @param target    target instance
	 * @param instances interceptor instances
	 */
	static void adopt(Object target, Map<Class<?>, Object> instances) {
		of(target).putAll(instances);
	}

	/**
	 * Creates an interceptor instance.
	 *
	 * @param interceptorClass interceptor class
	 * @return observed instance, with resources injected
	 */
	static Object create(Class<?> interceptorClass) {
		return IuException.unchecked(() -> IuType.of(interceptorClass).constructor().exec());
	}

	/**
	 * Destroys the interceptor instances of a target.
	 *
	 * @param target target instance
	 * @return failure from {@code @PreDestroy} of an interceptor, or null
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static Throwable release(Object target) {
		expunge();
		final var instances = INSTANCES.remove(new Key(target, QUEUE));
		if (instances == null)
			return null;

		Throwable e = null;
		for (final var instance : instances.values()) {
			final IuType type = IuType.of(instance.getClass());
			e = IuException.suppress(e, () -> type.destroy(instance));
		}
		return e;
	}

}
