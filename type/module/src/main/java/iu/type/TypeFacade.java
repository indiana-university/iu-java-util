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

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Implementation of {@link IuType}.
 * 
 * @param <T> generic type
 */
final class TypeFacade<T> extends TypeTemplate<T> implements IuType<T> {

	private final TypeReference<T, ?> reference;

	/**
	 * Constructor for a non-named, non-indexed reference.
	 * 
	 * @param template      type template
	 * @param referrer      referrer element
	 * @param referenceKind reference kind
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind) {
		super(template);
		this.reference = new TypeReference<>(referenceKind, referrer, this);
	}

	/**
	 * Constructor for a named reference.
	 * 
	 * @param template      type template
	 * @param referrer      referrer element
	 * @param referenceKind reference kind
	 * @param referenceName reference name
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind,
			String referenceName) {
		super(template);
		this.reference = new TypeReference<>(referenceKind, referrer, this, referenceName);
	}

	/**
	 * Constructor for an indexed reference.
	 * 
	 * @param template       type template
	 * @param referrer       referrer element
	 * @param referenceKind  reference kind
	 * @param referenceIndex reference index
	 */
	TypeFacade(TypeTemplate<T> template, IuAnnotatedElement referrer, IuReferenceKind referenceKind,
			int referenceIndex) {
		super(template);
		this.reference = new TypeReference<>(referenceKind, referrer, this, referenceIndex);
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return reference;
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("IuType[").append(annotatedElement);
		IuTypeReference<?, ?> ref = reference;
		while (ref != null) {
			sb.append(' ').append(ref.kind());
			if (ref.index() >= 0)
				sb.append('(').append(ref.index()).append(") ");
			else if (ref.name() != null)
				sb.append('(').append(ref.name()).append(") ");
			else
				sb.append(" ");

			var referrer = ref.referrer();

			if (referrer instanceof TypeFacade) {
				var referrerType = (TypeFacade<?>) referrer;
				sb.append(referrerType.annotatedElement);
				ref = referrerType.reference;
			} else {
				sb.append(referrer);
				ref = null;
			}
		}
		sb.append(']');
		return sb.toString();
	}

}
