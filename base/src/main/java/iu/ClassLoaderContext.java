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
package iu;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import edu.iu.IuClassLoaderContext;
import edu.iu.IuObject;

/**
 * Internal instance manager for {@link IuClassLoaderContext}
 */
public class ClassLoaderContext {

	private static final IuClassLoaderContext BOOT = () -> "boot";
	private static final IuClassLoaderContext PLATFORM = () -> "platform";
	private static final IuClassLoaderContext SYSTEM = () -> "system";
	private static final Map<ClassLoader, IuClassLoaderContext> CONTEXT;

	static {
		IuObject.assertNotOpen(ClassLoaderContext.class);
		final Map<ClassLoader, IuClassLoaderContext> context = new WeakHashMap<>();
		context.put(ClassLoader.getPlatformClassLoader(), PLATFORM);
		context.put(ClassLoader.getSystemClassLoader(), SYSTEM);
		CONTEXT = context;
	}

	/**
	 * Registers a context with a {@link ClassLoader}
	 * 
	 * @param context {@link IuClassLoaderContext}
	 * @param loader  {@link ClassLoader}
	 */
	public static void register(IuClassLoaderContext context, ClassLoader loader) {
		Objects.requireNonNull(loader, "loader");
		Objects.requireNonNull(context, "context");
		synchronized (CONTEXT) {
			if (CONTEXT.get(loader) != null)
				throw new IllegalStateException("already registered");
			else
				CONTEXT.put(loader, context);
		}
	}

	/**
	 * Gets the context registered for the current thread.
	 * 
	 * @return {@link IuClassLoaderContext}
	 */
	public static IuClassLoaderContext get() {
		var loader = Thread.currentThread().getContextClassLoader();

		while (loader != null) {
			final IuClassLoaderContext context;
			synchronized (CONTEXT) {
				context = CONTEXT.get(loader);
			}
			if (context != null)
				return context;
			loader = loader.getParent();
		}

		return BOOT;

	}

	private ClassLoaderContext() {
	}

}
