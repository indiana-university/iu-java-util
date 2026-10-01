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
package iu.client;

import java.beans.Introspector;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.TypeValue;

/**
 * The properties of a business object type, as JSON-B discovers them, shared by
 * the JSON-B provider and the IU binding paths.
 *
 * <p>
 * A property merges the nearest visible field, getter, and setter sharing its
 * name, walking the type, its non-platform superclasses, and its non-platform
 * interfaces. A getter is preferred over a field for reading, and a setter over
 * a non-final field for writing; under the default visibility, a getter or
 * setter that isn't public closes that direction, so its field isn't used in
 * its place. Property types resolve against the type the model is for, so a
 * type variable of a generic superclass reads as the argument supplied for it.
 * </p>
 *
 * <p>
 * Binding annotations apply as {@link BindingMetadata} reads them: a visibility
 * or property order a type declares, a name a member declares, a member that
 * excludes its property, and whether a property writes null.
 * </p>
 */
public final class BeanModel {

	/**
	 * Decides which fields and methods of a class are properties.
	 */
	public interface Visibility {
		/**
		 * Determines if a field is a property.
		 *
		 * @param field field
		 * @return true if visible
		 */
		boolean isVisible(Field field);

		/**
		 * Determines if an accessor method is a property.
		 *
		 * @param method accessor
		 * @return true if visible
		 */
		boolean isVisible(Method method);
	}

	/**
	 * JSON-B default visibility: public fields and public accessors.
	 */
	public static final Visibility PUBLIC = new Visibility() {
		@Override
		public boolean isVisible(Field field) {
			return Modifier.isPublic(field.getModifiers());
		}

		@Override
		public boolean isVisible(Method method) {
			return Modifier.isPublic(method.getModifiers());
		}
	};

	/**
	 * Visibility before 7.1: public accessors only.
	 */
	static final Visibility ACCESSORS = new Visibility() {
		@Override
		public boolean isVisible(Field field) {
			return false;
		}

		@Override
		public boolean isVisible(Method method) {
			return Modifier.isPublic(method.getModifiers());
		}
	};

	/**
	 * How a model discovers properties.
	 */
	public static final class Discovery {
		/**
		 * As JSON-B discovers properties by default, honoring binding annotations.
		 */
		public static final Discovery DEFAULT = of(null, "LEXICOGRAPHICAL");

		/**
		 * As the IU binding paths discovered properties before 7.1: public accessors
		 * only, without binding annotations.
		 */
		public static final Discovery LEGACY = new Discovery(ACCESSORS, "LEXICOGRAPHICAL", BindingMetadata.NONE, true);

		/**
		 * Gets a discovery honoring binding annotations.
		 *
		 * @param visibility visibility for a class that declares none of its own; null
		 *                   for {@link BeanModel#PUBLIC}
		 * @param order      {@code LEXICOGRAPHICAL}, {@code REVERSE}, or {@code ANY},
		 *                   as JSON-B names them
		 * @return {@link Discovery}
		 */
		public static Discovery of(Visibility visibility, String order) {
			return new Discovery(visibility, order, BindingMetadata.get(), false);
		}

		private final Visibility visibility;
		private final String order;
		private final BindingMetadata metadata;
		private final boolean legacy;

		/**
		 * Constructor.
		 *
		 * @param visibility visibility for a class that declares none of its own; null
		 *                   for {@link BeanModel#PUBLIC}
		 * @param order      property order strategy
		 * @param metadata   binding annotations
		 * @param legacy     true to apply {@code visibility} to every class, whatever
		 *                   it declares
		 */
		Discovery(Visibility visibility, String order, BindingMetadata metadata, boolean legacy) {
			this.visibility = visibility == null ? PUBLIC : visibility;
			this.order = order;
			this.metadata = metadata;
			this.legacy = legacy;
		}

		private Visibility visibility(Class<?> declaringClass) {
			if (legacy)
				return visibility;
			final var declared = metadata.visibility(declaringClass);
			return declared == null ? visibility : declared;
		}
	}

	/**
	 * One logical property.
	 */
	public final class Property {
		private final String name;
		private final int discovered;
		private Method getter;
		private Method getGetter;
		private Method isGetter;
		private Class<?> getterClass;
		private boolean readHidden;
		private Field readField;
		private Method setter;
		private final List<Method> setters = new ArrayList<>();
		private Class<?> setterClass;
		private boolean writeHidden;
		private Field writeField;
		private final List<AnnotatedElement> members = new ArrayList<>();
		private AnnotatedElement[] readMembers;
		private AnnotatedElement[] writeMembers;
		private String readName;
		private String writeName;
		private Boolean nillable;
		private BindingMetadata.Format readDateFormat;
		private BindingMetadata.Format writeDateFormat;
		private BindingMetadata.Format readNumberFormat;
		private BindingMetadata.Format writeNumberFormat;
		private final Map<PropertyNaming, String> readNames = new ConcurrentHashMap<>();
		private final Map<PropertyNaming, String> writeNames = new ConcurrentHashMap<>();

		private Property(String name, int discovered) {
			this.name = name;
			this.discovered = discovered;
		}

		/**
		 * Considers a getter. The nearest class declaring one decides: its visible
		 * getter is used, an {@code is} getter over a {@code get} getter, and a getter
		 * that isn't visible but {@code closes} closes reading.
		 */
		private void getter(Method method, boolean visible, boolean closes) {
			if (!visible && !closes)
				return;

			final var declaringClass = method.getDeclaringClass();
			if (getterClass != null && getterClass != declaringClass)
				return;
			getterClass = declaringClass;

			if (!visible)
				readHidden = true;
			else if (method.getName().startsWith("is"))
				isGetter = method;
			else
				getGetter = method;
		}

		/**
		 * Considers a setter. The nearest class declaring one decides: its visible
		 * setters are candidates, and a setter that isn't visible but {@code closes}
		 * closes writing.
		 */
		private void setter(Method method, boolean visible, boolean closes) {
			if (!visible && !closes)
				return;

			final var declaringClass = method.getDeclaringClass();
			if (setterClass != null && setterClass != declaringClass)
				return;
			setterClass = declaringClass;

			if (visible)
				setters.add(method);
			else
				writeHidden = true;
		}

		/**
		 * Settles the members used once the whole hierarchy has been scanned, then
		 * applies their binding annotations.
		 *
		 * @throws IllegalStateException if several setters could write the property and
		 *                               none takes the type it reads as, or a member
		 *                               excluding the property conflicts with another
		 *                               binding annotation on it
		 */
		private void settle(BindingMetadata metadata, Field declaredField) {
			getter = isGetter != null ? isGetter : getGetter;
			if (getter != null)
				getter = accessor(getter);
			else if (readHidden)
				readField = null;

			if (setters.size() == 1)
				setter = setters.get(0);
			else if (!setters.isEmpty()) {
				final var type = getter != null ? getter.getReturnType()
						: readField != null ? readField.getType() : null;
				for (final var candidate : setters)
					if (candidate.getParameterTypes()[0] == type)
						setter = candidate;
				if (setter == null)
					throw new IllegalStateException("ambiguous setters for property " + name + " of "
							+ setterClass.getName() + "; declare a getter or field of the type to write");
			}

			if (setter != null)
				setter = accessor(setter);
			else if (writeHidden)
				writeField = null;

			// a field excludes its property; a getter only reading, a setter only
			// writing. A field that isn't visible still declares annotations.
			final var field = readField != null ? readField //
					: writeField != null ? writeField //
							: declaredField;
			final var fieldTransient = field != null && metadata.isTransient(field);
			final var readTransient = fieldTransient || (getter != null && metadata.isTransient(getter));
			final var writeTransient = fieldTransient || (setter != null && metadata.isTransient(setter));

			for (final var member : new AnnotatedElement[] { field, getter, setter })
				if (member != null)
					members.add(member);
			if (readTransient || writeTransient)
				for (final var member : members)
					if (metadata.isCustomized(member))
						throw new IllegalStateException("property " + name + " of " + type.getName()
								+ " is transient, so can't be customized by " + member);

			if (readTransient) {
				getter = null;
				readField = null;
			}
			if (writeTransient) {
				setter = null;
				writeField = null;
			}

			// an accessor's declaration overrides its field's
			final var fieldName = field == null ? null : metadata.name(field);
			readName = getter != null && metadata.name(getter) != null ? metadata.name(getter) : fieldName;
			writeName = setter != null && metadata.name(setter) != null ? metadata.name(setter) : fieldName;

			for (final var member : new AnnotatedElement[] { getter, field })
				if (member != null && nillable == null)
					nillable = metadata.nillable(member);
			if (nillable == null)
				nillable = metadata.nillable(type);

			readMembers = Stream.of(getter, field).filter(Objects::nonNull).toArray(AnnotatedElement[]::new);
			writeMembers = Stream.of(setter, field).filter(Objects::nonNull).toArray(AnnotatedElement[]::new);

			readDateFormat = format(metadata::dateFormat, getter, field);
			writeDateFormat = format(metadata::dateFormat, setter, field);
			readNumberFormat = format(metadata::numberFormat, getter, field);
			writeNumberFormat = format(metadata::numberFormat, setter, field);
		}

		/**
		 * Gets the format declared for one direction: on the accessor, then the field,
		 * then the class declaring either, then its package.
		 */
		private BindingMetadata.Format format(Function<AnnotatedElement, BindingMetadata.Format> declared,
				Method accessor, Field field) {
			for (final var member : new AnnotatedElement[] { accessor, field })
				if (member != null) {
					final var format = declared.apply(member);
					if (format != null)
						return format;
				}

			final Member member = accessor != null ? accessor : field;
			return member == null ? null : declared.apply(member.getDeclaringClass());
		}

		/**
		 * Gets the date format declared for writing the property to JSON.
		 *
		 * @return date format; null if not declared
		 */
		public BindingMetadata.Format readDateFormat() {
			return readDateFormat;
		}

		/**
		 * Gets the date format declared for reading the property from JSON.
		 *
		 * @return date format; null if not declared
		 */
		public BindingMetadata.Format writeDateFormat() {
			return writeDateFormat;
		}

		/**
		 * Gets the number format declared for writing the property to JSON.
		 *
		 * @return number format; null if not declared
		 */
		public BindingMetadata.Format readNumberFormat() {
			return readNumberFormat;
		}

		/**
		 * Gets the number format declared for reading the property from JSON.
		 *
		 * @return number format; null if not declared
		 */
		public BindingMetadata.Format writeNumberFormat() {
			return writeNumberFormat;
		}

		/**
		 * Gets the Java property name.
		 *
		 * @return property name
		 */
		public String name() {
			return name;
		}

		/**
		 * Gets the JSON name the property is written as.
		 *
		 * @param naming how properties are named
		 * @return declared name, or the Java name named that way
		 */
		public String readName(PropertyNaming naming) {
			return readNames.computeIfAbsent(naming, n -> readName != null ? readName : n.name(name));
		}

		/**
		 * Gets the JSON name the property is read from.
		 *
		 * @param naming how properties are named
		 * @return declared name, or the Java name named that way
		 */
		public String writeName(PropertyNaming naming) {
			return writeNames.computeIfAbsent(naming, n -> writeName != null ? writeName : n.name(name));
		}

		/**
		 * Determines if the property can be read from a bean, and so written to JSON.
		 *
		 * @return true if readable
		 */
		public boolean isReadable() {
			return getter != null || readField != null;
		}

		/**
		 * Determines if the property can be set on a bean, and so read from JSON.
		 *
		 * @return true if writable
		 */
		public boolean isWritable() {
			return setter != null || writeField != null;
		}

		/**
		 * Gets the type the property reads as.
		 *
		 * @return resolved type
		 */
		public Type readType() {
			if (getter != null)
				return resolve(getter.getGenericReturnType(), getter);
			else
				return resolve(readField.getGenericType(), readField);
		}

		/**
		 * Gets the type the property is set as.
		 *
		 * @return resolved type
		 */
		public Type writeType() {
			if (setter != null)
				return resolve(setter.getGenericParameterTypes()[0], setter);
			else
				return resolve(writeField.getGenericType(), writeField);
		}

		/**
		 * Gets whether a null value is written, as declared on the property, its type,
		 * or its package.
		 *
		 * @return true to write null, false to omit it; null if not declared
		 */
		public Boolean nillable() {
			return nillable;
		}

		/**
		 * Gets the fields and accessors the property is made of.
		 *
		 * @return field, getter, and setter, where present
		 */
		public List<AnnotatedElement> members() {
			return members;
		}

		/**
		 * Gets the members that declare how the property is written to JSON.
		 *
		 * @return getter, then field, where present
		 */
		public AnnotatedElement[] readMembers() {
			return readMembers.clone();
		}

		/**
		 * Gets the members that declare how the property is read from JSON.
		 *
		 * @return setter, then field, where present
		 */
		public AnnotatedElement[] writeMembers() {
			return writeMembers.clone();
		}

		/**
		 * Reads the property from a bean.
		 *
		 * @param bean bean
		 * @return value
		 */
		public Object get(Object bean) {
			if (getter != null)
				return IuException.uncheckedInvocation(() -> getter.invoke(bean));
			else
				return IuException.unchecked(() -> readField.get(bean));
		}

		/**
		 * Sets the property on a bean.
		 *
		 * @param bean  bean
		 * @param value value
		 */
		public void set(Object bean, Object value) {
			if (setter != null)
				IuException.uncheckedInvocation(() -> setter.invoke(bean, value));
			else
				IuException.unchecked(() -> writeField.set(bean, value));
		}

		private Type resolve(Type memberType, Member member) {
			return GenericTypes.resolve(memberType, context, member.getDeclaringClass());
		}
	}

	private static final TypeValue<BeanModel> DEFAULT = new TypeValue<BeanModel>() {
		@Override
		protected BeanModel computeValue(Type type) {
			return new BeanModel(type, Discovery.DEFAULT);
		}
	};

	private static final TypeValue<BeanModel> LEGACY = new TypeValue<BeanModel>() {
		@Override
		protected BeanModel computeValue(Type type) {
			return new BeanModel(type, Discovery.LEGACY);
		}
	};

	/**
	 * Gets the model for a type, discovered as JSON-B discovers properties by
	 * default.
	 *
	 * @param type business object type, or a parameterized type of one
	 * @return {@link BeanModel}
	 */
	public static BeanModel of(Type type) {
		return DEFAULT.get(type);
	}

	/**
	 * Gets the model for a type, discovered as the IU binding paths discovered
	 * properties before 7.1.
	 *
	 * @param type business object type, or a parameterized type of one
	 * @return {@link BeanModel}
	 */
	public static BeanModel legacy(Type type) {
		return LEGACY.get(type);
	}

	private final Class<?> type;
	private final Type context;
	private final Property[] sorted;
	private final Map<Method, Property> byGetter = new HashMap<>();
	private final Map<PropertyNaming, Property[]> readable = new ConcurrentHashMap<>();
	private final Map<PropertyNaming, Map<String, Property>> writable = new ConcurrentHashMap<>();
	private volatile Constructor<?> constructor;
	private final Creator creator;
	private final Map<String, String> typeKeys;
	private final Set<String> keys;
	private final BindingMetadata.TypeInfo dispatch;

	/**
	 * Gets the type information properties an instance writes first: for each type
	 * in the type information chain, outermost first, the alias of the subtype this
	 * type is.
	 *
	 * @return alias by key; empty if no type information applies
	 */
	public Map<String, String> typeKeys() {
		return typeKeys;
	}

	/**
	 * Gets the type information that picks the subtype to read an object as: the
	 * type's own, else the nearest supertype's.
	 *
	 * @return type information; null if none applies
	 */
	public BindingMetadata.TypeInfo dispatch() {
		return dispatch;
	}

	/**
	 * Gets the subtype an alias names for reading this type.
	 *
	 * @param alias alias
	 * @return subtype
	 * @throws IllegalArgumentException if no subtype has the alias, or it isn't a
	 *                                  subtype of this type
	 */
	public Class<?> subtype(String alias) {
		final var subtype = dispatch.subtype(alias);
		if (!type.isAssignableFrom(subtype))
			throw new IllegalArgumentException(
					"alias " + alias + " names " + subtype.getName() + ", which isn't a " + type.getName());
		return subtype;
	}

	/**
	 * Checks that no property shares a name with a type information key.
	 */
	private void checkKeys(String jsonName) {
		if (keys.contains(jsonName))
			throw new IllegalStateException("property " + jsonName + " of " + type.getName()
					+ " conflicts with the type information key of the same name");
	}

	/**
	 * A parameter of a {@link Creator}.
	 */
	public final class CreatorParameter {
		private final String name;
		private final String declaredName;
		private final Type type;
		private final BindingMetadata.Format dateFormat;
		private final BindingMetadata.Format numberFormat;
		private final Parameter parameter;
		private final Map<PropertyNaming, String> names = new ConcurrentHashMap<>();

		private CreatorParameter(String name, Parameter parameter, BindingMetadata metadata) {
			this.name = name;
			this.parameter = parameter;
			declaredName = metadata.name(parameter);
			final var executable = parameter.getDeclaringExecutable();
			type = GenericTypes.resolve(parameter.getParameterizedType(), context, executable.getDeclaringClass());
			final var date = metadata.dateFormat(parameter);
			dateFormat = date == null ? metadata.dateFormat(executable.getDeclaringClass()) : date;
			final var number = metadata.numberFormat(parameter);
			numberFormat = number == null ? metadata.numberFormat(executable.getDeclaringClass()) : number;
		}

		/**
		 * Gets the Java name of the parameter.
		 *
		 * @return parameter name, or the record component's
		 */
		public String name() {
			return name;
		}

		/**
		 * Gets the JSON name the parameter reads.
		 *
		 * @param naming how properties are named
		 * @return declared name, or the Java name named that way
		 */
		public String jsonName(PropertyNaming naming) {
			return names.computeIfAbsent(naming, n -> declaredName != null ? declaredName : n.name(name));
		}

		/**
		 * Gets the type the parameter reads as.
		 *
		 * @return resolved type
		 */
		public Type type() {
			return type;
		}

		/**
		 * Gets the date format declared on the parameter, or its declaring class or
		 * package.
		 *
		 * @return date format; null if not declared
		 */
		public BindingMetadata.Format dateFormat() {
			return dateFormat;
		}

		/**
		 * Gets the number format declared on the parameter, or its declaring class or
		 * package.
		 *
		 * @return number format; null if not declared
		 */
		public BindingMetadata.Format numberFormat() {
			return numberFormat;
		}

		/**
		 * Gets the members that declare how the parameter is read from JSON.
		 *
		 * @return the parameter
		 */
		public AnnotatedElement[] members() {
			return new AnnotatedElement[] { parameter };
		}
	}

	/**
	 * Creates the type from values read from JSON: a constructor or static factory
	 * method declared a creator, or a record's canonical constructor.
	 */
	public final class Creator {
		private final Executable executable;
		private final CreatorParameter[] parameters;
		private final Map<PropertyNaming, Map<String, Integer>> indexes = new ConcurrentHashMap<>();

		private Creator(Executable executable, CreatorParameter[] parameters) {
			this.executable = executable;
			this.parameters = parameters;
		}

		/**
		 * Gets the parameters.
		 *
		 * @return parameters, in order
		 */
		public CreatorParameter[] parameters() {
			return parameters.clone();
		}

		/**
		 * Gets the parameter a JSON property names.
		 *
		 * @param naming   how properties are named
		 * @param jsonName JSON property name, matched as the naming matches names
		 * @return parameter index; -1 if none
		 */
		public int index(PropertyNaming naming, String jsonName) {
			final var index = indexes.computeIfAbsent(naming, n -> {
				final Map<String, Integer> byName = new HashMap<>();
				for (var i = 0; i < parameters.length; i++)
					byName.putIfAbsent(n.key(parameters[i].jsonName(n)), i);
				return Map.copyOf(byName);
			}).get(naming.key(jsonName));
			return index == null ? -1 : index;
		}

		/**
		 * Creates an instance.
		 *
		 * @param arguments one per parameter
		 * @return new instance
		 */
		public Object create(Object[] arguments) {
			if (executable instanceof Constructor)
				return IuException.uncheckedInvocation(() -> ((Constructor<?>) executable).newInstance(arguments));
			else
				return IuException.uncheckedInvocation(() -> ((Method) executable).invoke(null, arguments));
		}

		@Override
		public String toString() {
			return executable.toString();
		}
	}

	/**
	 * Finds the creator: the one constructor or static method declared a creator,
	 * else a record's canonical constructor.
	 *
	 * @throws IllegalStateException if more than one is declared, or one is an
	 *                               instance method, or doesn't return the type, or
	 *                               a parameter has no name
	 */
	private Creator creator(BindingMetadata metadata, RecordComponent[] recordComponents) {
		Executable declared = null;
		for (final Executable candidate : Stream
				.concat(Stream.of(type.getDeclaredConstructors()), Stream.of(type.getDeclaredMethods()))
				.toArray(Executable[]::new))
			if (metadata.isCreator(candidate)) {
				if (declared != null)
					throw new IllegalStateException("more than one creator declared for " + type.getName() + ": "
							+ declared + " and " + candidate);
				if (candidate instanceof Method //
						&& (!Modifier.isStatic(candidate.getModifiers()) //
								|| !type.isAssignableFrom(((Method) candidate).getReturnType())))
					throw new IllegalStateException("creator " + candidate
							+ " must be a constructor, or a static method returning " + type.getName());
				declared = candidate;
			}

		final String[] names;
		if (declared != null) {
			final var parameters = declared.getParameters();
			names = new String[parameters.length];
			for (var i = 0; i < parameters.length; i++) {
				final var parameter = parameters[i];
				if (metadata.name(parameter) == null && !parameter.isNamePresent())
					throw new IllegalStateException("parameter " + i + " of creator " + declared
							+ " has no name; declare it with @JsonbProperty, or compile with -parameters");
				names[i] = parameter.getName();
			}
		} else if (recordComponents != null) {
			final var types = Stream.of(recordComponents).map(RecordComponent::getType).toArray(Class<?>[]::new);
			declared = IuException.unchecked(() -> type.getDeclaredConstructor(types));
			names = Stream.of(recordComponents).map(RecordComponent::getName).toArray(String[]::new);
		} else
			return null;

		accessible((AccessibleObject & Member) declared);
		final var parameters = declared.getParameters();
		final var creatorParameters = new CreatorParameter[parameters.length];
		for (var i = 0; i < parameters.length; i++)
			creatorParameters[i] = new CreatorParameter(names[i], parameters[i], metadata);
		return new Creator(declared, creatorParameters);
	}

	/**
	 * Gets the creator.
	 *
	 * @return creator; null if the type has none, and is created by its no-arg
	 *         constructor
	 */
	public Creator creator() {
		return creator;
	}

	/**
	 * Introspects a business object type.
	 *
	 * @param type      business object type, or a parameterized type of one
	 * @param discovery how properties are discovered
	 * @throws IllegalStateException if a property is declared inconsistently
	 */
	public BeanModel(Type type, Discovery discovery) {
		this.context = type;
		this.type = JsonAdapters.erase(type);
		final var recordComponents = this.type.isRecord() ? this.type.getRecordComponents() : null;

		// discovery order: nearest declaration first, so the first field, getter, or
		// setter found for a name wins
		final Map<String, Property> properties = new LinkedHashMap<>();
		final Map<String, Field> declaredFields = new HashMap<>();
		final Map<Class<?>, Visibility> visibilities = new HashMap<>();
		final List<BindingMetadata.TypeInfo> typeInfos = new ArrayList<>();
		final Set<String> listed = new LinkedHashSet<>();

		final Deque<Class<?>> todo = new ArrayDeque<>();
		final Set<Class<?>> done = new HashSet<>();
		todo.push(this.type);
		while (!todo.isEmpty()) {
			// a platform class, even the type itself, declares no properties
			final var next = todo.pop();
			if (IuObject.isPlatformName(next.getName()) //
					|| !done.add(next))
				continue;

			final var propertyOrder = discovery.metadata.propertyOrder(next);
			if (propertyOrder != null)
				listed.addAll(Arrays.asList(propertyOrder));

			final var typeInfo = discovery.metadata.typeInfo(next);
			if (typeInfo != null)
				typeInfos.add(typeInfo);

			final var visibility = visibilities.computeIfAbsent(next, discovery::visibility);
			for (final var field : next.getDeclaredFields()) {
				final var modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) //
						|| field.isSynthetic())
					continue;

				// a field declares binding annotations for its property whether or
				// not it's visible, as the field behind a getter and setter
				declaredFields.putIfAbsent(field.getName(), field);
				if (Modifier.isTransient(modifiers) //
						|| !visibility.isVisible(field))
					continue;

				accessible(field);
				final var property = property(field.getName(), properties);
				if (property.readField == null)
					property.readField = field;
				if (property.writeField == null && !Modifier.isFinal(modifiers))
					property.writeField = field;
			}

			// declared methods, not Introspector, which reports only public accessors:
			// under the default visibility, a getter or setter that isn't public means
			// its field isn't used either; any other visibility that hides an accessor
			// leaves its field to stand on its own
			final var closes = visibility == PUBLIC;
			// a record reads each component by its accessor
			if (next == this.type && recordComponents != null)
				for (final var component : recordComponents) {
					final var accessor = accessible(component.getAccessor());
					property(component.getName(), properties).getter(accessor, visibility.isVisible(accessor), closes);
				}
			for (final var method : next.getDeclaredMethods()) {
				final var name = accessorName(method);
				if (name == null)
					continue;

				final var property = property(name, properties);
				final var visible = visibility.isVisible(method);
				if (method.getParameterCount() == 0)
					property.getter(method, visible, closes);
				else
					property.setter(method, visible, closes);
			}

			for (final var i : next.getInterfaces())
				todo.push(i);

			final var parent = next.getSuperclass();
			if (parent != null)
				todo.push(parent);
		}

		for (final var property : properties.values()) {
			property.settle(discovery.metadata, declaredFields.get(property.name));
			if (property.getter != null)
				byGetter.put(property.getter, property);
		}

		creator = creator(discovery.metadata, recordComponents);

		// type information forms one chain, outermost first, each key its own
		for (final var a : typeInfos)
			for (final var b : typeInfos)
				if (!a.type().isAssignableFrom(b.type()) && !b.type().isAssignableFrom(a.type()))
					throw new IllegalStateException(this.type.getName() + " inherits type information from both "
							+ a.type().getName() + " and " + b.type().getName() + "; type information can't be merged");
		// outermost first: by how many in the chain it is a subtype of
		typeInfos.sort(Comparator
				.comparingLong(a -> typeInfos.stream().filter(b -> b.type().isAssignableFrom(a.type())).count()));
		final Map<String, String> typeKeys = new LinkedHashMap<>();
		final Set<String> keys = new LinkedHashSet<>();
		for (final var typeInfo : typeInfos) {
			if (!keys.add(typeInfo.key()))
				throw new IllegalStateException(
						"type information key " + typeInfo.key() + " declared twice for " + this.type.getName());
			final var alias = typeInfo.alias(this.type);
			if (alias != null)
				typeKeys.put(typeInfo.key(), alias);
		}
		this.typeKeys = Collections.unmodifiableMap(typeKeys);
		this.keys = keys;
		dispatch = typeInfos.isEmpty() ? null : typeInfos.get(typeInfos.size() - 1);

		final Comparator<Property> strategyOrder;
		if ("ANY".equals(discovery.order))
			strategyOrder = Comparator.comparingInt(p -> p.discovered);
		else if ("REVERSE".equals(discovery.order))
			strategyOrder = Comparator.comparing((Property p) -> p.name).reversed();
		else // LEXICOGRAPHICAL
			strategyOrder = Comparator.comparing(p -> p.name);

		// listed properties first, in listed order, then the rest by strategy
		final Map<String, Integer> rank = new HashMap<>();
		for (final var name : listed)
			rank.putIfAbsent(name, rank.size());
		final Comparator<Property> order = (a, b) -> {
			final var i = rank.get(a.name);
			final var j = rank.get(b.name);
			if (i != null)
				return j != null ? Integer.compare(i, j) : -1;
			else if (j != null)
				return 1;
			else
				return strategyOrder.compare(a, b);
		};
		sorted = properties.values().stream().sorted(order).toArray(Property[]::new);
	}

	/**
	 * Gets the type the model is for.
	 *
	 * @return business object class
	 */
	public Class<?> type() {
		return type;
	}

	/**
	 * Gets readable properties in serialization order; where two share a name once
	 * named, the nearest declaration.
	 *
	 * @param naming how properties are named
	 * @return readable properties
	 */
	public Property[] readable(PropertyNaming naming) {
		return readable.computeIfAbsent(naming, n -> {
			final Map<String, Property> byName = new HashMap<>();
			for (final var property : discoveryOrder())
				if (property.isReadable()) {
					checkKeys(property.readName(n));
					byName.putIfAbsent(property.readName(n), property);
				}
			return Stream.of(sorted).filter(p -> byName.get(p.readName(n)) == p).toArray(Property[]::new);
		});
	}

	/**
	 * Gets a writable property by JSON name; where two share a name once named, the
	 * nearest declaration.
	 *
	 * @param naming   how properties are named
	 * @param jsonName JSON property name, matched as the naming matches names
	 * @return writable property; null if not defined
	 */
	public Property writable(PropertyNaming naming, String jsonName) {
		return writable.computeIfAbsent(naming, n -> {
			final Map<String, Property> byName = new HashMap<>();
			for (final var property : discoveryOrder())
				if (property.isWritable()) {
					checkKeys(property.writeName(n));
					byName.putIfAbsent(n.key(property.writeName(n)), property);
				}
			return Map.copyOf(byName);
		}).get(naming.key(jsonName));
	}

	/**
	 * Gets the property a getter reads.
	 *
	 * @param getter getter
	 * @return property; null if the getter isn't one, or its property is transient
	 */
	public Property property(Method getter) {
		return byGetter.get(getter);
	}

	private List<Property> discoveryOrder() {
		final var properties = new ArrayList<>(Arrays.asList(sorted));
		properties.sort(Comparator.comparingInt(p -> p.discovered));
		return properties;
	}

	/**
	 * Creates a new instance using the no-arg constructor.
	 *
	 * @return new instance
	 * @throws IllegalStateException if the type has no no-arg constructor
	 */
	public Object newInstance() {
		var constructor = this.constructor;
		if (constructor == null)
			this.constructor = constructor = accessible(Stream.of(type.getDeclaredConstructors()) //
					.filter(c -> c.getParameterCount() == 0) //
					.findFirst() //
					.orElseThrow(() -> new IllegalStateException("no default constructor for " + type.getName())));

		final var c = constructor;
		return IuException.uncheckedInvocation(() -> c.newInstance());
	}

	/**
	 * Determines if a property value is absent: null, or an empty optional.
	 *
	 * @param value property value
	 * @return true if absent
	 */
	public static boolean isAbsent(Object value) {
		if (value instanceof Optional)
			return ((Optional<?>) value).isEmpty();
		else if (value instanceof OptionalInt)
			return ((OptionalInt) value).isEmpty();
		else if (value instanceof OptionalLong)
			return ((OptionalLong) value).isEmpty();
		else if (value instanceof OptionalDouble)
			return ((OptionalDouble) value).isEmpty();
		else
			return value == null;
	}

	private Property property(String name, Map<String, Property> properties) {
		var property = properties.get(name);
		if (property == null)
			properties.put(name, property = new Property(name, properties.size()));
		return property;
	}

	/**
	 * Gets the name of the property a method reads or writes by JavaBeans naming:
	 * {@code getX()}, or {@code isX()} returning {@code boolean}, reads {@code x};
	 * {@code void setX(value)} writes it.
	 *
	 * @param method method
	 * @return property name; null if the method is not an accessor
	 */
	public static String accessorName(Method method) {
		if (Modifier.isStatic(method.getModifiers()) //
				|| method.isSynthetic())
			return null;

		final var name = method.getName();
		final var returnType = method.getReturnType();
		final int prefix;
		switch (method.getParameterCount()) {
		case 0:
			if (name.startsWith("get") && returnType != void.class)
				prefix = 3;
			else if (name.startsWith("is") && returnType == boolean.class)
				prefix = 2;
			else
				return null;
			break;

		case 1:
			if (name.startsWith("set") && returnType == void.class)
				prefix = 3;
			else
				return null;
			break;

		default:
			return null;
		}

		if (name.length() == prefix)
			return null;
		else
			return Introspector.decapitalize(name.substring(prefix));
	}

	/**
	 * Suppresses access checks on a member that isn't public, or whose declaring
	 * class isn't, where the declaring module permits it; where it doesn't, access
	 * fails when the member is used, naming the module and package to open. A
	 * public member of a public class is used as-is.
	 */
	private static <A extends AccessibleObject & Member> A accessible(A member) {
		if (!Modifier.isPublic(member.getModifiers()) //
				|| !isPublic(member.getDeclaringClass()))
			member.trySetAccessible();
		return member;
	}

	/**
	 * Gets a method to invoke an accessor through: a public method of a class that
	 * isn't accessible, such as a lambda's or a private implementation of a public
	 * interface, invokes through the nearest public supertype declaring it, in a
	 * package exported to this module, which reaches the same implementation with
	 * no access check to suppress; any other accessor is made accessible where the
	 * declaring module permits it.
	 *
	 * @param method accessor
	 * @return method to invoke
	 */
	static Method accessor(Method method) {
		if (Modifier.isPublic(method.getModifiers()) //
				&& !isPublic(method.getDeclaringClass())) {
			final Deque<Class<?>> todo = new ArrayDeque<>();
			todo.add(method.getDeclaringClass());
			while (!todo.isEmpty()) {
				final var next = todo.poll();
				if (next != method.getDeclaringClass() //
						&& isAccessible(next))
					for (final var candidate : next.getDeclaredMethods())
						if (Modifier.isPublic(candidate.getModifiers()) //
								&& candidate.getName().equals(method.getName())
								&& Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes()))
							return candidate;

				todo.addAll(Arrays.asList(next.getInterfaces()));
				final var parent = next.getSuperclass();
				if (parent != null)
					todo.add(parent);
			}
		}
		return accessible(method);
	}

	/**
	 * Determines if this module can invoke a class's public members with no access
	 * check to suppress.
	 *
	 * @param c class
	 * @return true if the class and every class enclosing it are public, and its
	 *         package is exported to this module
	 */
	static boolean isAccessible(Class<?> c) {
		return isPublic(c) && c.getModule().isExported(c.getPackageName(), BeanModel.class.getModule());
	}

	/**
	 * Determines if a class and every class enclosing it are public.
	 *
	 * @param c class
	 * @return true if public from anywhere its package is exported to
	 */
	public static boolean isPublic(Class<?> c) {
		for (var next = c; next != null; next = next.getEnclosingClass())
			if (!Modifier.isPublic(next.getModifiers()))
				return false;
		return true;
	}

}
