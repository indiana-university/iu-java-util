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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import edu.iu.client.IuJsonPropertyNameFormat;
import iu.client.BeanModel;
import iu.client.BindingMetadata;
import iu.client.JsonAdapters;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * The properties of a business object type, as the provider converts them: a
 * {@link BeanModel} discovered with the provider's visibility and order
 * strategies, and each property's configured conversions.
 */
final class IuJsonbModel {

	/**
	 * One property, with its configured conversions.
	 */
	static final class Property {
		private final BeanModel.Property property;
		private final IuJsonb jsonb;
		private volatile IuJsonbValueAdapter<Object> readAdapter;
		private volatile IuJsonbValueAdapter<Object> writeAdapter;

		private Property(BeanModel.Property property, IuJsonb jsonb) {
			this.property = property;
			this.jsonb = jsonb;
		}

		/**
		 * Gets the JSON name the property is written as.
		 *
		 * @param naming how the call names properties
		 * @return declared name, or the Java name named that way
		 */
		String jsonName(IuJsonbNaming naming) {
			return property.readName(naming);
		}

		private IuJsonbValueAdapter<Object> readAdapter() {
			var adapter = readAdapter;
			if (adapter == null)
				readAdapter = adapter = jsonb.adapt(property.readType(), property.readDateFormat(),
						property.readNumberFormat(), property.readMembers());
			return adapter;
		}

		private IuJsonbValueAdapter<Object> writeAdapter() {
			var adapter = writeAdapter;
			if (adapter == null)
				writeAdapter = adapter = jsonb.adapt(property.writeType(), property.writeDateFormat(),
						property.writeNumberFormat(), property.writeMembers());
			return adapter;
		}

		/**
		 * Determines if a value converts as a present value: not null, and not an
		 * empty optional unless the call's options keep one.
		 */
		private static boolean isPresent(Object value, IuSerializationContext context) {
			if (value == null)
				return false;
			else if (context.options().isEmptyOptionalPresent())
				return true;
			else
				return !BeanModel.isAbsent(value);
		}

		/**
		 * Determines if an absent value is written as null: as declared on the
		 * property, its type, or its package, else as the call says.
		 */
		private boolean isNillable(IuSerializationContext context) {
			final var nillable = property.nillable();
			return nillable != null ? nillable : context.isIncludeNullProperties();
		}

		/**
		 * Adds this property of a bean to an object builder.
		 *
		 * <p>
		 * A null value, or an empty {@link java.util.Optional},
		 * {@link java.util.OptionalInt}, {@link java.util.OptionalLong}, or
		 * {@link java.util.OptionalDouble}, converts like any other when the
		 * property is nillable, or the call includes null properties. Otherwise
		 * only a configured {@link jakarta.json.bind.adapter.JsonbAdapter} is
		 * consulted, given null, and the property is omitted unless it adapts the
		 * null to a value.
		 * </p>
		 *
		 * @param bean    bean
		 * @param builder object builder
		 * @param context call in progress
		 * @return true if added; false if omitted
		 */
		boolean add(Object bean, JsonObjectBuilder builder, IuSerializationContext context) {
			final var name = jsonName(context.naming());
			context.push(name);
			try {
				final var value = property.get(bean);
				final var adapter = readAdapter();
				if (isPresent(value, context) || isNillable(context)) {
					builder.add(name, adapter.toJson(value));
					return true;
				}

				final var adapted = adapter.nullProperty(context, List.of());
				if (adapted == null)
					return false;
				builder.add(name, adapted);
				return true;
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Writes this property of a bean to a generator, with null handling as in
		 * {@link #add(Object, JsonObjectBuilder, IuSerializationContext)}.
		 *
		 * @param bean      bean
		 * @param generator generator, in an object context
		 * @param context   call in progress
		 * @return true if written; false if omitted
		 */
		boolean write(Object bean, JsonGenerator generator, IuSerializationContext context) {
			final var name = jsonName(context.naming());
			context.push(name);
			try {
				final var value = property.get(bean);
				final var adapter = readAdapter();
				if (isPresent(value, context) || isNillable(context)) {
					generator.writeKey(name);
					adapter.write(value, generator);
					return true;
				} else
					return adapter.writeNullProperty(name, generator, context, List.of());
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Sets this property of a bean from a JSON value.
		 *
		 * @param bean    bean
		 * @param key     JSON property name, for the path
		 * @param value   JSON value
		 * @param context call in progress
		 */
		void fromJson(Object bean, String key, JsonValue value, IuDeserializationContext context) {
			set(bean, key, fromJson(key, value, context), context);
		}

		/**
		 * Converts a JSON value for this property, without setting it.
		 *
		 * @param key     JSON property name, for the path
		 * @param value   JSON value
		 * @param context call in progress
		 * @return value
		 */
		Object fromJson(String key, JsonValue value, IuDeserializationContext context) {
			context.push(key);
			try {
				return writeAdapter().fromJson(value);
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Reads a value for this property from a parser, without setting it.
		 *
		 * @param key     JSON property name, for the path
		 * @param parser  parser, positioned at the value's first event
		 * @param context call in progress
		 * @return value
		 */
		Object read(String key, JsonParser parser, IuDeserializationContext context) {
			context.push(key);
			try {
				return writeAdapter().read(parser);
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Sets this property of a bean to a value already converted.
		 *
		 * @param bean    bean
		 * @param key     JSON property name, for the path
		 * @param value   value
		 * @param context call in progress
		 */
		void set(Object bean, String key, Object value, IuDeserializationContext context) {
			context.push(key);
			try {
				property.set(bean, value);
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Sets this property of a bean from a parser.
		 *
		 * @param bean    bean
		 * @param key     JSON property name, for the path
		 * @param parser  parser, positioned at the value's first event
		 * @param context call in progress
		 */
		void read(Object bean, String key, JsonParser parser, IuDeserializationContext context) {
			set(bean, key, read(key, parser, context), context);
		}
	}

	/**
	 * Creates a business object from values read from JSON, with its parameters'
	 * configured conversions.
	 */
	static final class Creator {
		private final BeanModel.Creator creator;
		private final BeanModel.CreatorParameter[] parameters;
		private final IuJsonb jsonb;
		private final IuJsonbValueAdapter<?>[] adapters;

		private Creator(BeanModel.Creator creator, IuJsonb jsonb) {
			this.creator = creator;
			this.jsonb = jsonb;
			parameters = creator.parameters();
			adapters = new IuJsonbValueAdapter<?>[parameters.length];
		}

		/**
		 * Gets the number of parameters.
		 *
		 * @return parameter count
		 */
		int size() {
			return parameters.length;
		}

		/**
		 * Gets the parameter a JSON property names.
		 *
		 * @param naming how the call names properties
		 * @param key    JSON property name
		 * @return parameter index; -1 if none
		 */
		int index(IuJsonbNaming naming, String key) {
			return creator.index(naming, key);
		}

		private IuJsonbValueAdapter<?> adapter(int index) {
			var adapter = adapters[index];
			if (adapter == null) {
				final var parameter = parameters[index];
				adapters[index] = adapter = jsonb.adapt(parameter.type(), parameter.dateFormat(),
						parameter.numberFormat(), parameter.members());
			}
			return adapter;
		}

		/**
		 * Reads a parameter's value from a parser.
		 *
		 * @param index   parameter index
		 * @param key     JSON property name, for the path
		 * @param parser  parser, positioned at the value's first event
		 * @param context call in progress
		 * @return value
		 */
		Object read(int index, String key, JsonParser parser, IuDeserializationContext context) {
			context.push(key);
			try {
				return adapter(index).read(parser);
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Converts a parameter's value from JSON.
		 *
		 * @param index   parameter index
		 * @param key     JSON property name, for the path
		 * @param value   JSON value
		 * @param context call in progress
		 * @return value
		 */
		Object fromJson(int index, String key, JsonValue value, IuDeserializationContext context) {
			context.push(key);
			try {
				return adapter(index).fromJson(value);
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Creates an instance: a parameter not read is null, a primitive's
		 * default, or an empty optional, unless
		 * {@link jakarta.json.bind.JsonbConfig#CREATOR_PARAMETERS_REQUIRED}.
		 *
		 * @param arguments one per parameter
		 * @param read      which parameters were read
		 * @return new instance
		 * @throws JsonbException if a parameter wasn't read, and parameters are
		 *                        required
		 */
		Object create(Object[] arguments, boolean[] read) {
			for (var i = 0; i < arguments.length; i++)
				if (!read[i])
					if (jsonb.isCreatorParametersRequired())
						throw new JsonbException("missing creator parameter " + parameters[i].name() + " of "
								+ creator);
					else
						arguments[i] = JsonAdapters.undefined(parameters[i].type());
			return creator.create(arguments);
		}
	}

	private final IuJsonb jsonb;
	private final BeanModel model;
	private final Creator creator;
	private final Map<BeanModel.Property, Property> properties = new ConcurrentHashMap<>();
	private final Map<IuJsonbNaming, Property[]> readable = new ConcurrentHashMap<>();

	/**
	 * Introspects a business object type.
	 *
	 * @param type  business object type, or a parameterized type of one
	 * @param jsonb provider, for its visibility and order strategies and
	 *              property adapters
	 * @throws IllegalStateException if a property is declared inconsistently
	 */
	IuJsonbModel(Type type, IuJsonb jsonb) {
		this.jsonb = jsonb;
		final var configured = jsonb.propertyVisibilityStrategy();
		model = new BeanModel(type, BeanModel.Discovery.of( //
				configured == null ? null : JsonbMetadata.visibility(configured), //
				jsonb.propertyOrderStrategy()));
		final var creator = model.creator();
		this.creator = creator == null ? null : new Creator(creator, jsonb);
	}

	private Property property(BeanModel.Property property) {
		return properties.computeIfAbsent(property, p -> new Property(p, jsonb));
	}

	/**
	 * Gets readable properties in serialization order.
	 *
	 * @param naming how the call names properties
	 * @return readable properties
	 */
	Property[] readable(IuJsonbNaming naming) {
		return readable.computeIfAbsent(naming,
				n -> Stream.of(model.readable(n)).map(this::property).toArray(Property[]::new));
	}

	/**
	 * Gets readable properties in serialization order.
	 *
	 * @param format property name format
	 * @return readable properties
	 */
	Property[] readable(IuJsonPropertyNameFormat format) {
		return readable(IuJsonbNaming.of(format));
	}

	/**
	 * Gets a writable property by JSON name.
	 *
	 * @param naming   how the call names properties
	 * @param jsonName JSON property name, matched as the naming matches names
	 * @return writable property; null if not defined
	 */
	Property writable(IuJsonbNaming naming, String jsonName) {
		final var property = model.writable(naming, jsonName);
		return property == null ? null : property(property);
	}

	/**
	 * Gets a writable property by JSON name.
	 *
	 * @param format   property name format
	 * @param jsonName formatted property name
	 * @return writable property; null if not defined
	 */
	Property writable(IuJsonPropertyNameFormat format, String jsonName) {
		return writable(IuJsonbNaming.of(format), jsonName);
	}

	/**
	 * Creates a new instance using the no-arg constructor.
	 *
	 * @return new instance
	 */
	Object newInstance() {
		return model.newInstance();
	}

	/**
	 * Gets the creator.
	 *
	 * @return creator; null if created by the no-arg constructor
	 */
	Creator creator() {
		return creator;
	}

	/**
	 * Gets the type information properties an instance writes first.
	 *
	 * @return alias by key
	 * @see BeanModel#typeKeys()
	 */
	Map<String, String> typeKeys() {
		return model.typeKeys();
	}

	/**
	 * Gets the type information that picks the subtype to read an object as.
	 *
	 * @return type information; null if none applies
	 * @see BeanModel#dispatch()
	 */
	BindingMetadata.TypeInfo dispatch() {
		return model.dispatch();
	}

	/**
	 * Gets the subtype an alias names for reading this type.
	 *
	 * @param alias alias
	 * @return subtype
	 * @see BeanModel#subtype(String)
	 */
	Class<?> subtype(String alias) {
		return model.subtype(alias);
	}

}
