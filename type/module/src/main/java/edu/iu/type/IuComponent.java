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
package edu.iu.type;

import java.lang.annotation.Annotation;
import java.nio.file.Path;

import iu.type.ComponentFactory;

/**
 * Defines an application component.
 */
public interface IuComponent {

	/**
	 * Creates a new component.
	 * 
	 * @param modulePath Paths to valid {@link Module}-defining jar files.
	 * @return component
	 */
	static IuComponent of(Path... modulePath) {
		return ComponentFactory.newComponent(modulePath);
	}

	/**
	 * Creates a component that delegates to this component.
	 * <p>
	 * A delegating component <em>must</em>:
	 * </p>
	 * <ul>
	 * <li>Have a {@link #classLoader() class loader} that delegates to this
	 * component's {@link #classLoader()}.</li>
	 * <li>Include of this component's {@link #interfaces() interfaces},
	 * {@link #annotatedTypes(Class) annotation types}, and
	 * {@link #resources()}.</li>
	 * </ul>
	 * 
	 * @param modulePath Paths to valid {@link Module}-defining jar files.
	 * @return delegating component
	 */
	IuComponent extend(Path... modulePath);

	/**
	 * Gets the {@link ClassLoader} for this component.
	 * 
	 * @return {@link ClassLoader}
	 */
	ClassLoader classLoader();

	/**
	 * Gets all of the component's public interfaces.
	 * 
	 * @return interface facades
	 */
	Iterable<IuType<?>> interfaces();

	/**
	 * Gets all types in the component annotated by a specific type.
	 * 
	 * @param annotationType annotation type
	 * @return annotated type facades
	 */
	Iterable<IuType<?>> annotatedTypes(Class<? extends Annotation> annotationType);

	/**
	 * Gets component's resources.
	 * 
	 * @return resources
	 */
	Iterable<IuResource<?>> resources();

}
