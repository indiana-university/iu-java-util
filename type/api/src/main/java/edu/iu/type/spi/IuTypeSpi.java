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
package edu.iu.type.spi;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;

import edu.iu.type.IuComponent;
import edu.iu.type.IuType;

/**
 * Implementation service provider interface.
 */
public interface IuTypeSpi {

	/**
	 * Gets the {@link #getImplementationModule() implementation module} for the
	 * provided implementation.
	 * 
	 * @return {@link Module}
	 */
	static Module getModule() {
		return TypeImplementation.PROVIDER.getImplementationModule();
	}

	/**
	 * Gets the implementation module.
	 * 
	 * @return {@link Module}
	 */
	Module getImplementationModule();

	/**
	 * Resolves an {@link IuType} instance for a generic type.
	 * 
	 * @param type Type
	 * @return Type introspection facade
	 * @see IuType#of(Type)
	 * @see IuType#of(Class)
	 */
	IuType<?, ?> resolveType(Type type);

	/**
	 * Implements {@link IuComponent#of(InputStream, InputStream...)}.
	 * 
	 * @param controllerCallback               receives a reference to
	 *                                         {@link Module} defined by the
	 *                                         <strong>component archive</strong>
	 *                                         and the {@link Controller} for the
	 *                                         module layer created in conjunction
	 *                                         with this loader. API Note from
	 *                                         {@link Controller}: <em>Care should
	 *                                         be taken with Controller objects,
	 *                                         they should never be shared with
	 *                                         untrusted code.</em>
	 * @param componentArchiveSource           component archive
	 * @param providedDependencyArchiveSources provided dependency archives
	 * @return {@link IuComponent} instance
	 * @throws IOException If the <strong>component archive</strong> or any
	 *                     <strong>dependency archives</strong> are unreadable.
	 * 
	 * @see IuComponent
	 */
	IuComponent createComponent(BiConsumer<Module, Controller> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException;

	/**
	 * Decorates a path entry in a loaded class environment as a {@link IuComponent
	 * component}.
	 * 
	 * @param classLoader {@link ClassLoader}; <em>must</em> include
	 *                    {@code pathEntry} on its class or module path.
	 * @param pathEntry   Single {@link Path path entry} representing a
	 *                    {@link JarFile jar file} or folder containing resources
	 *                    loaded by {@code classLoader}
	 * @return {@link IuComponent} decorated view of the path entry relative to the
	 *         class loader.
	 * @throws IOException            if an I/O error occurs while scanning the path
	 *                                for resources.
	 * @throws ClassNotFoundException if any class discovered on the path could not
	 *                                be loaded using {@code classLoader}
	 */
	IuComponent scanComponentEntry(ClassLoader classLoader, Path pathEntry) throws IOException, ClassNotFoundException;

}
