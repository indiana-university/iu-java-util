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
package iu.type.loader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.loader.IuLoadedComponent;

/**
 * Provides a {@link AutoCloseable} handle to a loaded component.
 */
public class LoadedComponent implements IuLoadedComponent {

	private volatile ClassLoader loader;
	private volatile ModuleLayer layer;
	private volatile AutoCloseable component;

	/**
	 * Constructor.
	 * 
	 * @param parent                           parent {@link ClassLoader}
	 * @param parentLayer                      parent {@link ModuleLayer}
	 * 
	 * @param controllerCallback               receives a reference to an
	 *                                         {@link IuComponentController} that
	 *                                         may be used to set up access rules
	 *                                         for the component. This reference
	 *                                         <em>should not</em> be passed beyond
	 *                                         the scope of the callback; see
	 *                                         {@link ModularClassLoader}
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public LoadedComponent(ClassLoader parent, ModuleLayer parentLayer, Consumer<Controller> controllerCallback,
			InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources) {
		IuException.unchecked(() -> {
			final var iuComponent = parent.loadClass("edu.iu.type.IuComponent");
			final var of = iuComponent.getMethod("of", ClassLoader.class, ModuleLayer.class, Consumer.class,
					InputStream.class, InputStream[].class);
			component = (AutoCloseable) of.invoke(null, parent, parentLayer, controllerCallback, componentArchiveSource,
					providedDependencyArchiveSources);
			loader = (ClassLoader) iuComponent.getMethod("classLoader").invoke(component);
			layer = (ModuleLayer) iuComponent.getMethod("moduleLayer").invoke(component);
		});
	}

	@Override
	public ClassLoader getClassLoader() {
		return loader;
	}

	@Override
	public ModuleLayer getModuleLayer() {
		return layer;
	}

	@Override
	public synchronized void close() throws Exception {
		if (component != null) {
			component.close();
			component = null;
		}

		if (loader != null)
			loader = null;
		if (layer != null)
			layer = null;
	}

}
