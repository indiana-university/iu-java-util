/*
 * Copyright © 2026 Indiana University
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
package edu.iu.dao;

/**
 * An entity that can bind to a parent entity, and so is valid only as a child
 * of the parent it is bound to.
 *
 * <p>
 * When an entity implements this interface, {@link IuEntityDao} invokes
 * {@link #bind(Object)} with the active parent when a new entity is created and
 * each time a stored entity is loaded, saved, merged or deleted, and
 * {@link IuEntityDao#tryBinding} defers to {@link #tryBinding(Object)} to
 * filter search results.
 * </p>
 */
public interface ChildEntity {

	/**
	 * Binds a parent entity to this entity, invoked by the default implementation
	 * of {@link IuEntityDao#bind} for new and stored entities alike.
	 *
	 * <p>
	 * The default implementation fails fast: it throws
	 * {@link IllegalArgumentException} when {@link #tryBinding} returns false, so
	 * an implementation normally supplies only {@link #tryBinding}. The child's
	 * reference to the parent should be populated upon return, and not changed from
	 * a previously set value, so binding a stored entity to the parent it already
	 * has is a no-op.
	 * </p>
	 * 
	 * @param parent parent entity; may be null if a parent binding is not required
	 * @throws IllegalArgumentException if {@link #tryBinding} returns false, i.e.,
	 *                                  the parent cannot be bound to this child
	 */
	default void bind(Object parent) {
		if (!tryBinding(parent))
			throw new IllegalArgumentException("unexpected parent " + parent + " for child " + this);
	}

	/**
	 * Attempts to bind an entity.
	 *
	 * <p>
	 * Used by {@link IuEntityDao#tryBinding} to filter search results by the active
	 * parent, so it should return false, rather than throw, for a parent that is
	 * not bound to this entity.
	 * </p>
	 * 
	 * <p>
	 * An implementation MAY initialize the entity's internal state to ensure that
	 * true is returned for no more than one parent, for example by capturing a
	 * non-null parent reference. This is a viable strategy for bulk assignment from
	 * a child search across a link table. Implementations SHOULD be non-destructive
	 * and fail fast.
	 * </p>
	 * 
	 * @param parent parent entity
	 * @return true if the parent is bound to this child; else false
	 */
	boolean tryBinding(Object parent);

}
