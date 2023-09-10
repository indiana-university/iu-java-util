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

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.iu.type.DefaultInterceptor;
import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuExecutable;
import edu.iu.type.IuType;
import jakarta.annotation.Priority;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.ExcludeDefaultInterceptors;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.Interceptors;

/**
 * Encapsulates interceptor support to simplify behavior where the interceptor
 * API is not present in the classpath.
 */
public class InterceptorHelper {

	/**
	 * Locates interceptor types defined for a resolved executable.
	 * 
	 * @param executable resolved constructor or method
	 * @return interceptors defined on the classpath as affecting the executable
	 *         context
	 */
	public static Collection<IuType<?>> findInterceptors(IuExecutable<?, ?> executable) {
		Queue<IuType<?>> interceptorTypes = new ArrayDeque<>();

		if (!executable.hasAnnotation(ExcludeDefaultInterceptors.class)
				&& !executable.declaringType().hasAnnotation(ExcludeDefaultInterceptors.class))
			for (IuType<?> defaultInterceptorType : IuType.resolveAnnotatedTypes(
					executable.declaringType().baseClass().getClassLoader(), DefaultInterceptor.class))
				interceptorTypes.add(defaultInterceptorType);

		if (!executable.hasAnnotation(ExcludeClassInterceptors.class)
				&& executable.declaringType().hasAnnotation(Interceptors.class))
			for (Class<?> interceptorType : executable.declaringType().annotation(Interceptors.class).value())
				interceptorTypes.add(IuType.resolve(interceptorType));

		if (executable.hasAnnotation(Interceptors.class))
			for (Class<?> interceptorType : executable.annotation(Interceptors.class).value())
				interceptorTypes.add(IuType.resolve(interceptorType));

		Queue<IuAnnotatedElement> elementsToCheckForInterceptorBinding = new ArrayDeque<>();
		elementsToCheckForInterceptorBinding.offer(executable.declaringType());
		elementsToCheckForInterceptorBinding.offer(executable);
		List<IuType<?>> boundInterceptors = new ArrayList<>();
		while (!elementsToCheckForInterceptorBinding.isEmpty()) {
			var currentElement = elementsToCheckForInterceptorBinding.poll();
			Set<Class<? extends Annotation>> bindingAnnotations = new LinkedHashSet<>();
			for (var annotation : currentElement.annotations().keySet()) {
				var annotationType = IuType.resolve(annotation);
				if (annotationType.hasAnnotation(InterceptorBinding.class)
						&& bindingAnnotations.add(annotationType.baseClass()))
					elementsToCheckForInterceptorBinding.offer(annotationType);
			}

			if (!bindingAnnotations.isEmpty())
				binding: for (IuType<?> interceptor : IuType.resolveAnnotatedTypes(
						executable.declaringType().baseClass().getClassLoader(), Interceptor.class)) {
					for (Class<? extends Annotation> binding : bindingAnnotations)
						if (!interceptor.hasAnnotation(binding))
							continue binding;
					boundInterceptors.add(interceptor);
				}
		}

		boundInterceptors.sort((a, b) -> Integer.compare(
				a.hasAnnotation(Priority.class) ? a.annotation(Priority.class).value()
						: Interceptor.Priority.APPLICATION,
				b.hasAnnotation(Priority.class) ? b.annotation(Priority.class).value()
						: Interceptor.Priority.APPLICATION));
		interceptorTypes.addAll(boundInterceptors);
		return Collections.unmodifiableCollection(interceptorTypes);
	}
}
