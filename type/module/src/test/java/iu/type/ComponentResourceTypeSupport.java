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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.mockito.stubbing.Answer;

import edu.iu.type.IuConstructor;
import edu.iu.type.IuType;

/**
 * Mimics type introspection behavior required by ComponentResource.
 */
public class ComponentResourceTypeSupport implements InvocationInterceptor {

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		try (var mockType = mockStatic(IuType.class)) {
			var mockTypes = new HashMap<Class<?>, IuType<?>>();
			Answer ofAnswer = a -> {
				var t = a.getArgument(0);
				
				Class c;
				if (t instanceof ParameterizedType)
					c = (Class) ((ParameterizedType) t).getRawType();
				else
					c = (Class) t;
				
				var type = mockTypes.get(c);
				if (type == null) {
					type = mock(IuType.class);
					when(type.name()).thenReturn(c.getName());
					when(type.baseClass()).thenReturn(c);
					if (c == boolean.class)
						when(type.autoboxClass()).thenReturn((Class) Boolean.class);
					else
						when(type.autoboxClass()).thenReturn(c);
					var constructor = mock(IuConstructor.class);
					assertDoesNotThrow(() -> when(constructor.exec()).then(b -> {
						try {
							try {
								return c.getDeclaredConstructor().newInstance();
							} catch (NoSuchMethodException e) {
								return c.getDeclaredConstructor(invocationContext.getTargetClass())
										.newInstance(invocationContext.getTarget().get());
							}
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}

					}));
					when(type.constructor()).thenReturn(constructor);
					mockTypes.put(c, type);
				}
				return type;
			};
			mockType.when(() -> IuType.of(any(Class.class))).then(ofAnswer);
			mockType.when(() -> IuType.of(any(Type.class))).then(ofAnswer);
			invocation.proceed();
		}
}

}
