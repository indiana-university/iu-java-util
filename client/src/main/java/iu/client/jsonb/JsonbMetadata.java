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
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.BeanModel;
import iu.client.BindingMetadata;
import iu.client.JsonAdapters;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbAnnotation;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Reads JSON-B annotations; the only class the IU binding paths reach that
 * refers to the JSON-B API, loaded only when the API is present.
 *
 * <p>
 * A type or property the IU binding paths convert that declares a JSON-B
 * component, by {@code @JsonbTypeAdapter}, {@code @JsonbTypeSerializer}, or
 * {@code @JsonbTypeDeserializer}, converts through a provider created on first
 * use, with no configuration but the IU conversion's options, so the component
 * sees a JSON-B context.
 * </p>
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

	/**
	 * Options of the IU conversion running a JSON-B component on each thread.
	 */
	private static final ThreadLocal<IuJsonSerializationOptions> OPTIONS = new ThreadLocal<>();

	/**
	 * Holds the provider that runs JSON-B components for the IU conversions,
	 * created on first use.
	 */
	private static final class Components {
		private static final IuJsonb JSONB = new IuJsonb(new JsonbConfig().setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS,
				(Supplier<IuJsonSerializationOptions>) OPTIONS::get), IuJson.PROVIDER);
	}

	/**
	 * Whether a type or one of its supertypes declares a component.
	 */
	private static final ClassValue<Boolean> DECLARES = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			if (IuObject.isPlatformName(type.getName()))
				return false;
			if (declaresComponent(type))
				return true;
			for (final var i : type.getInterfaces())
				if (get(i))
					return true;
			final var parent = type.getSuperclass();
			return parent != null && get(parent);
		}
	};

	private static boolean declaresComponent(AnnotatedElement element) {
		return element.isAnnotationPresent(JsonbTypeAdapter.class) //
				|| element.isAnnotationPresent(JsonbTypeSerializer.class) //
				|| element.isAnnotationPresent(JsonbTypeDeserializer.class);
	}

	/**
	 * Runs a provider's conversion with the IU conversion's options in effect.
	 */
	private static IuJsonAdapter<?> scoped(IuJsonAdapter<Object> adapter, Supplier<IuJsonSerializationOptions> options) {
		return new IuJsonAdapter<Object>() {
			private <R> R within(Supplier<R> conversion) {
				final var previous = OPTIONS.get();
				OPTIONS.set(Objects.requireNonNullElse(options.get(), IuJsonSerializationOptions.DEFAULT));
				try {
					return conversion.get();
				} finally {
					if (previous == null)
						OPTIONS.remove();
					else
						OPTIONS.set(previous);
				}
			}

			@Override
			public Object fromJson(JsonValue jsonValue) {
				return within(() -> adapter.fromJson(jsonValue));
			}

			@Override
			public JsonValue toJson(Object javaValue) {
				return within(() -> adapter.toJson(javaValue));
			}

			@Override
			public Object read(JsonParser parser) {
				return within(() -> adapter.read(parser));
			}

			@Override
			public void write(Object value, JsonGenerator generator) {
				within(() -> {
					adapter.write(value, generator);
					return null;
				});
			}
		};
	}

	@Override
	public IuJsonAdapter<?> components(Type type, Supplier<IuJsonSerializationOptions> options) {
		if (DECLARES.get(JsonAdapters.erase(type)))
			return scoped(Components.JSONB.adapt(type), options);
		else
			return null;
	}

	@Override
	public IuJsonAdapter<?> components(Type type, Format date, Format number, AnnotatedElement[] members,
			Supplier<IuJsonSerializationOptions> options) {
		for (final var member : members)
			if (declaresComponent(member))
				return scoped(Components.JSONB.adapt(type, date, number, members), options);
		return null;
	}

	@Override
	public TypeInfo typeInfo(Class<?> type) {
		final var typeInfo = type.getAnnotation(JsonbTypeInfo.class);
		if (typeInfo == null)
			return null;

		final Map<String, Class<?>> subtypes = new LinkedHashMap<>();
		for (final var subtype : typeInfo.value())
			if (subtypes.put(subtype.alias(), subtype.type()) != null)
				throw new IllegalStateException(
						"alias " + subtype.alias() + " declared twice by " + type.getName());
		return new TypeInfo(type, typeInfo.key(), subtypes);
	}

	@Override
	public boolean isCreator(Executable executable) {
		return executable.isAnnotationPresent(JsonbCreator.class);
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
