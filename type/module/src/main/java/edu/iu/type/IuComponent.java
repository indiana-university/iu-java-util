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

import java.nio.file.Path;

import iu.type.ComponentFactory;

/**
 * Defines an application component.
 */
public interface IuComponent {

	/**
	 * Creates a new component.
	 * 
	 * @param componentModuleJar   Path to a valid jar file that defines a
	 *                             {@link Module}.
	 * @param dependencyModuleJars Paths valid jar files that define a
	 *                             {@link Module} to include in the component's
	 *                             module path.
	 * @return component
	 */
	static IuComponent of(Path componentModuleJar, Path... dependencyModuleJars) {
		return ComponentFactory.newComponent(componentModuleJar, dependencyModuleJars);
	}

	/**
	 * Gets the {@link ClassLoader} for this component.
	 * 
	 * @return {@link ClassLoader}
	 */
	ClassLoader classLoader();

	/**
	 * Gets all public interfaces {@link Module#isOpen(String, Module) opened} by
	 * the component's {@link Module} that are {@link Module#canRead(Module)
	 * readable} by a target {@link Module}.
	 * 
	 * <p>
	 * The method <em>must</em> not return interfaces from dependency modules.
	 * </p>
	 * 
	 * @param module {@link Module} that intends to use the interfaces
	 * @return interface facades
	 */
	Iterable<IuType<?>> interfaces(Module module);

	/**
	 * Gets all types {@link Module#isOpen(String, Module) opened} by the
	 * component's {@link Module} that are {@link Module#canRead(Module) readable}
	 * by a target {@link Module}.
	 * 
	 * <p>
	 * The method <em>must</em> not include interfaces from dependent modules.
	 * </p>
	 * 
	 * @param module {@link Module} that intends to use the annotated types
	 * @return annotated type facades
	 */
	Iterable<IuType<?>> annotatedTypes(Module module);

	/**
	 * Gets resources defined by the component that may be
	 * {@link Module#canRead(Module) read} by a target {@link Module}.
	 * 
	 * @param module {@link Module} that intends to use the resources
	 * @return resources
	 */
	Iterable<IuResource<?>> resources(Module module);

}
