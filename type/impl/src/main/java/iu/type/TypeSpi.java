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
package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Service provider implementation.
 * 
 * @see IuTypeSpi
 */
public class TypeSpi implements IuTypeSpi {

	/**
	 * Default constructor.
	 */
	public TypeSpi() {
	}

	@Override
	public IuType<?, ?> resolveType(Type type) {
		return TypeFactory.resolveType(type);
	}

	@Override
	public IuComponent createComponent(ModuleLayer parentLayer, ClassLoader parent, BiConsumer<Module, Controller> controllerCallback,
			InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources) throws IOException {
		return ComponentFactory.createComponent(null, parentLayer, parent, controllerCallback, componentArchiveSource,
				providedDependencyArchiveSources);
	}

	@Override
	public IuComponent scanComponentEntry(ClassLoader classLoader, Path pathEntry)
			throws IOException, ClassNotFoundException {
		return new Component(classLoader, pathEntry);
	}

}
