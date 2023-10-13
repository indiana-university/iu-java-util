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

import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Implementation of {@link IuTypeReference}.
 * 
 * @param <T> referent type
 * @param <R> referrer type
 */
class TypeReference<T, R extends IuAnnotatedElement> implements IuTypeReference<T, R> {

	private final IuReferenceKind kind;
	private final R referrer;
	private final IuType<T> referent;
	private final String name;
	private final int index;

	/**
	 * Gets a non-named, non-indexed reference.
	 * 
	 * @param kind     non-named, non-indexed kind
	 * @param referrer referrer element
	 * @param referent referent type
	 */
	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert !kind.named() && !kind.indexed();
		this.kind = Objects.requireNonNull(kind);
		this.referrer = referrer;
		this.referent = Objects.requireNonNull(referent);
		this.name = null;
		this.index = -1;
	}

	/**
	 * Gets a named reference.
	 * 
	 * @param kind     named kind
	 * @param referrer referrer element
	 * @param referent referent type
	 * @param name     name
	 */
	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent, String name) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert kind.named();
		this.kind = Objects.requireNonNull(kind);
		this.referrer = Objects.requireNonNull(referrer);
		this.referent = Objects.requireNonNull(referent);
		this.name = Objects.requireNonNull(name);
		this.index = -1;
	}

	/**
	 * Gets a indexed reference.
	 * 
	 * @param kind     indexed kind
	 * @param referrer referrer element
	 * @param referent referent type
	 * @param index    index
	 */
	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent, int index) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert kind.indexed();
		assert index >= 0;
		this.kind = Objects.requireNonNull(kind);
		this.referrer = Objects.requireNonNull(referrer);
		this.referent = Objects.requireNonNull(referent);
		this.name = null;
		this.index = index;
	}

	@Override
	public IuReferenceKind kind() {
		return kind;
	}

	@Override
	public R referrer() {
		return referrer;
	}

	@Override
	public IuType<T> referent() {
		return referent;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public int index() {
		return index;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(System.identityHashCode(referrer), index, kind, name, referent);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!IuObject.typeCheck(this, obj))
			return false;
		TypeReference<?, ?> other = (TypeReference<?, ?>) obj;
		return referrer == other.referrer //
				&& index == other.index //
				&& kind == other.kind //
				&& IuObject.equals(name, other.name) //
				&& IuObject.equals(referent, other.referent);
	}

}
