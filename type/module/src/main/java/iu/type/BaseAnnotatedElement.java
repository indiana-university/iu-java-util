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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuObject;

/**
 * Represents an annotated element.
 * 
 * @param <E> element type
 */
public abstract class BaseAnnotatedElement<E extends AnnotatedElement> implements IuAnnotatedElement {

	private Map<Class<? extends Annotation>, Annotation> annotations;
	private final E element;

	/**
	 * Base constructor.
	 * 
	 * @param element resolved element
	 */
	protected BaseAnnotatedElement(E element) {
		this.element = element;
	}

	/**
	 * Gets the undecorated annotated element.
	 * 
	 * @return element
	 */
	public E deref() {
		return element;
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return BackwardsCompatibilityHelper.isAnnotationPresent(annotationType, element);
	}

	@Override
	public <A extends Annotation> A annotation(Class<A> annotationType) {
		return BackwardsCompatibilityHelper.getAnnotation(annotationType, element);
	}

	@Override
	public Map<Class<? extends Annotation>, Annotation> annotations() {
		if (annotations == null)
			annotations = BackwardsCompatibilityHelper.getAnnotations(element);
		return annotations;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(element);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		BaseAnnotatedElement<?> other = (BaseAnnotatedElement<?>) obj;
		return IuObject.equals(element, other.element);
	}

}
