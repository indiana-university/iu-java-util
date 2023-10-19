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

import java.util.function.Consumer;

import edu.iu.type.IuAnnotatedElement;

/**
 * Provides initialization behavior for type facade elements.
 */
abstract sealed class ElementBase implements IuAnnotatedElement
		permits AnnotatedElementBase, TypeFacade, PropertyFacade {

	private boolean sealed;
	private Runnable postInit;

	/**
	 * Default constructor, for use by all subclasses extend {@link TypeTemplate}.
	 */
	ElementBase() {
	}

	/**
	 * Constructor for use by {@link TypeTemplate}.
	 * 
	 * @param preInitHook used by {@link TypeFactory}
	 */
	ElementBase(Consumer<TypeTemplate<?, ?>> preInitHook) {
		preInitHook.accept((TypeTemplate<?, ?>) this);
	}

	/**
	 * Checks to verify the element is sealed.
	 * 
	 * <p>
	 * All public methods <em>should</em> invoke this method to verify immutability
	 * before attempting lookups.
	 * </p>
	 * 
	 * @throws IllegalStateException if the element is not sealed.
	 */
	void checkSealed() throws IllegalStateException {
		if (!sealed)
			throw new IllegalStateException("not sealed");
	}

	/**
	 * <em>May</em> be invoked within a base constructor to defer part of
	 * initialization until after the facade instance is fully formed.
	 * 
	 * @param run initialization segment to run after all facade elements are
	 *            populated
	 */
	void postInit(Runnable run) {
		if (sealed)
			run.run();
		else {
			var postInit = this.postInit;
			if (postInit == null)
				this.postInit = run;
			else
				this.postInit = () -> {
					postInit.run();
					run.run();
				};
		}
	}

	/**
	 * <em>Should</em> be called at the end of all concrete subclass constructors.
	 */
	void seal() {
		if (sealed)
			throw new IllegalStateException("Already sealed");

		var postInit = this.postInit;

		this.sealed = true;
		this.postInit = null;

		if (postInit != null)
			postInit.run();
	}

}
