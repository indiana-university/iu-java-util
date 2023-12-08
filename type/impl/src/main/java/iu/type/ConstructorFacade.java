/*
 * Copyright © 2023 Indiana University
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

import java.lang.reflect.Constructor;

import edu.iu.IuException;
import edu.iu.type.IuConstructor;

/**
 * Facade implementation for {@link IuConstructor}.
 * 
 * @param <C> constructor declaring type
 */
final class ConstructorFacade<C> extends ExecutableBase<C, C, Constructor<C>> implements IuConstructor<C> {

	/**
	 * Facade constructor.
	 * 
	 * @param constructor   {@link Constructor}
	 * @param declaringType {@link TypeTemplate}
	 */
	ConstructorFacade(Constructor<C> constructor, TypeTemplate<?, C> declaringType) {
		super(constructor, declaringType.type, declaringType);
		annotatedElement.setAccessible(true);
		declaringType.postInit(this::seal);
	}

	private boolean hasAroundConstruct() {
		return hasAnnotation(jakarta.interceptor.Interceptors.class) //
				|| declaringType().hasAnnotation(jakarta.interceptor.Interceptors.class) //
				|| declaringType().annotatedMethods(jakarta.interceptor.AroundConstruct.class).iterator().hasNext();
	}

	@Override
	public C exec(Object... arguments) throws Exception {
		if (hasAroundConstruct())
			throw new UnsupportedOperationException("@AroundConstruct not supported in this version");

		// @AroundConstruct

		final var instance = annotatedElement.getDeclaringClass()
				.cast(IuException.checkedInvocation(() -> annotatedElement.newInstance(arguments)));

		declaringType.template.observeNewInstance(instance);
		
		// TODO: *after* observing new instances 
		// @PostConstruct
//			declaringType.annotatedMethods(PostConstruct.class).forEach(m -> IuException.unchecked(() -> m.exec()));

		return instance;
	}

}
