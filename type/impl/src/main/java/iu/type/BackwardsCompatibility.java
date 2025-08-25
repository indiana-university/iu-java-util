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
package iu.type;

import java.lang.reflect.Modifier;

import edu.iu.type.IuComponent;
import jakarta.annotation.Resource;

/**
 * Provides access to <strong>context-compatible</strong> versions of
 * <strong>context-sensitive</strong> classes.
 * 
 * <p>
 * A <strong>context-sensitive</strong> class is {@link Modifier#PUBLIC public}
 * and intended to be loaded by direct reference from its {@link IuComponent
 * component} rather than shared by parent or module delegation. For example,
 * {@link Resource}.
 * </p>
 * 
 * <p>
 * <strong>Context-compatible</strong> refers to a class that has the same name,
 * or a compatible name (i.e., javax.* vs. jakarta.*), as a
 * <strong>context-sensitive</strong> class, and is guaranteed to be visible to
 * the current thread's {@link Thread#getContextClassLoader()}.
 * </p>
 */
final class BackwardsCompatibility {

	/**
	 * Gets a <strong>context-compatible</strong> equivalent of a
	 * <strong>context-sensitive</strong> class.
	 * 
	 * @param contextSensitive <strong>context-sensitive</strong> class
	 * @return context-compatible equivalent
	 */
	static Class<?> getCompatibleClass(Class<?> contextSensitive) {
		return getCompatibleClass(contextSensitive, Thread.currentThread().getContextClassLoader());
	}

	/**
	 * Gets a <strong>context-compatible</strong> equivalent of a
	 * <strong>context-sensitive</strong> class.
	 * 
	 * @param contextSensitive <strong>context-sensitive</strong> class
	 * @param contextLoader    {@link ClassLoader} that defines the context
	 * @return context-compatible equivalent
	 */
	static Class<?> getCompatibleClass(Class<?> contextSensitive, ClassLoader contextLoader) {
		var className = contextSensitive.getName();

		ClassNotFoundException contextClassNameNotFound;
		try {
			return contextLoader.loadClass(className);
		} catch (ClassNotFoundException e) {
			contextClassNameNotFound = e;
		}

		String compatibleClassName;
		if (className.startsWith("jakarta."))
			compatibleClassName = "javax" + className.substring(7);
		else if (className.startsWith("javax."))
			compatibleClassName = "jakarta" + className.substring(5);
		else {
			final var err = new NoClassDefFoundError();
			err.initCause(contextClassNameNotFound);
			throw err;
		}

		try {
			return Thread.currentThread().getContextClassLoader().loadClass(compatibleClassName);
		} catch (ClassNotFoundException e) {
			final var err = new NoClassDefFoundError();
			err.initCause(e);
			err.addSuppressed(contextClassNameNotFound);
			throw err;
		}
	}

	private BackwardsCompatibility() {
	}

}
