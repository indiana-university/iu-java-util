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
package iu.client.jsonb;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import edu.iu.IuException;
import iu.client.BeanModel;
import iu.client.BindingMetadata;
import jakarta.json.bind.annotation.JsonbAnnotation;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyVisibilityStrategy;

/**
 * Reads JSON-B annotations; the only class the IU binding paths reach that
 * refers to the JSON-B API, loaded only when the API is present.
 */
public final class JsonbMetadata implements BindingMetadata {

	/**
	 * Singleton instance.
	 */
	public static final JsonbMetadata INSTANCE = new JsonbMetadata();

	private static final ClassValue<BeanModel.Visibility> VISIBILITY = new ClassValue<>() {
		@Override
		protected BeanModel.Visibility computeValue(Class<?> strategyClass) {
			return visibility((PropertyVisibilityStrategy) IuException
					.uncheckedInvocation(() -> strategyClass.getConstructor().newInstance()));
		}
	};

	/**
	 * Adapts a JSON-B visibility strategy.
	 *
	 * @param strategy {@link PropertyVisibilityStrategy}
	 * @return {@link BeanModel.Visibility}
	 */
	static BeanModel.Visibility visibility(PropertyVisibilityStrategy strategy) {
		return new BeanModel.Visibility() {
			@Override
			public boolean isVisible(Field field) {
				return strategy.isVisible(field);
			}

			@Override
			public boolean isVisible(Method method) {
				return strategy.isVisible(method);
			}
		};
	}

	private JsonbMetadata() {
	}

	@Override
	public String[] propertyOrder(Class<?> type) {
		final var order = type.getAnnotation(JsonbPropertyOrder.class);
		return order == null ? null : order.value();
	}

	@Override
	public BeanModel.Visibility visibility(Class<?> type) {
		var declared = type.getAnnotation(JsonbVisibility.class);
		if (declared == null)
			declared = type.getPackage().getAnnotation(JsonbVisibility.class);
		return declared == null ? null : VISIBILITY.get(declared.value());
	}

	@Override
	public String name(AnnotatedElement member) {
		final var property = member.getAnnotation(JsonbProperty.class);
		if (property == null || property.value().isEmpty())
			return null;
		else
			return property.value();
	}

	@Override
	public boolean isTransient(AnnotatedElement member) {
		return member.isAnnotationPresent(JsonbTransient.class);
	}

	@Override
	public boolean isCustomized(AnnotatedElement member) {
		for (final var annotation : member.getAnnotations()) {
			final var annotationType = annotation.annotationType();
			if (annotationType != JsonbTransient.class //
					&& annotationType.isAnnotationPresent(JsonbAnnotation.class))
				return true;
		}
		return false;
	}

	@Override
	public Format dateFormat(AnnotatedElement element) {
		final var format = annotation(element, JsonbDateFormat.class);
		return format == null ? null : new Format(format.value(), format.locale());
	}

	@Override
	public Format numberFormat(AnnotatedElement element) {
		final var format = annotation(element, JsonbNumberFormat.class);
		return format == null ? null : new Format(format.value(), format.locale());
	}

	/**
	 * Gets an annotation a member declares, or a type or its package does.
	 */
	private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
		final var annotation = element.getAnnotation(annotationType);
		if (annotation == null && element instanceof Class)
			return ((Class<?>) element).getPackage().getAnnotation(annotationType);
		else
			return annotation;
	}

	@Override
	@SuppressWarnings("deprecation")
	public Boolean nillable(AnnotatedElement element) {
		var nillable = element.getAnnotation(JsonbNillable.class);
		if (element instanceof Class) {
			if (nillable == null)
				nillable = ((Class<?>) element).getPackage().getAnnotation(JsonbNillable.class);
		} else if (nillable == null) {
			final var property = element.getAnnotation(JsonbProperty.class);
			if (property != null && property.nillable())
				return true;
		}
		return nillable == null ? null : nillable.value();
	}

}
