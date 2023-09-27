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
package edu.iu.type;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>Refers</strong> to a {@link IuComponent component's}
 * <strong>version</strong>.
 * 
 * <p>
 * All {@link IuComponent components} are <strong>named</strong> and may be
 * <strong>referred to</strong> by <strong>version</strong>. <strong>Version
 * references</strong> are immutable, {@link Comparable comparable}, and
 * <em>must</em> implement {@link #hashCode()} and {@link #equals(Object)}.
 * <a href="https://semver.org/">Semantic Versioning</a> <em>must</em> be used
 * to ensure consistency across a variety of <strong>components</strong>.
 * </p>
 *
 * <p>
 * A {@link IuComponent component's} <strong>implementation version</strong> is
 * defined by its {@link IuComponent component archive}. A {@link IuComponent
 * dependency} <em>may</em> require a specific {@link #implementationVersion()
 * implementation version}, or a minimum {@link #specificationVersion()
 * specification version}, to be present on the {@link IuComponent component's
 * path}.
 * </p>
 * 
 * @see #implementationVersion()
 * @see #specificationVersion()
 */
public interface IuComponentVersion extends Comparable<IuComponentVersion> {

	/**
	 * Regular expression for validating a semantic version.
	 * 
	 * @see <a href="https://semver.org/">Semantic Versioning</a>
	 */
	static final Pattern SEMANTIC_VERSION_PATTERN = Pattern.compile(
			"^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

	/**
	 * Gets the component <strong>name</strong>.
	 * 
	 * <p>
	 * The component <strong>name</strong> <em>should</em> be universally unique,
	 * and <em>must</em> be unique within the {@link IuComponent component's path}.
	 * The <strong>name</strong> is part of of the {@link IuComponent component's}
	 * version.
	 * </p>
	 * 
	 * @return name
	 */
	String name();

	/**
	 * Gets the <strong>implementation version</strong> of a {@link IuComponent
	 * component} or <strong>dependency</strong>.
	 * 
	 * <p>
	 * <em>Must</em> return a value for which
	 * {@link #SEMANTIC_VERSION_PATTERN}{@link Pattern#matcher(CharSequence)
	 * .matcher(implementationVersion())}{@link Matcher#matches() .matches()}
	 * returns {@code true}, or {@code null}.
	 * </p>
	 * 
	 * @return Full <a href="https://semver.org/">Semantic</a>
	 *         <strong>implementation version</strong> of the {@link IuComponent
	 *         component}.<br>
	 *         <em>May</em> be {@code null} when referring to a
	 *         <strong>dependency</strong> on the <strong>component's specification
	 *         version</strong>.<br>
	 *         <em>Must</em> {@link String#startsWith(String) start with}
	 *         {@code major() + '.' + minor() + '.'} when {@code non-null}.
	 */
	String implementationVersion();

	/**
	 * Gets the <strong>major</strong> version number as defined by
	 * <a href="https://semver.org/">Semantic Versioning</a>.
	 * 
	 * @return <strong>Major</strong> version; <em>must</em> be non-negative,
	 *         <em>should</em> be positive. Non-development applications
	 *         <em>may</em> reject <strong>versions</strong> with a major version of
	 *         {@code 0}.
	 */
	int major();

	/**
	 * Gets the <strong>minor</strong> version number as defined by
	 * <a href="https://semver.org/">Semantic Versioning</a>.
	 * 
	 * @return <strong>Minor</strong> version; <em>must</em> be non-negative
	 */
	int minor();

	/**
	 * Gets the <strong>specification version</strong> implied by this
	 * <strong>version reference</strong>.
	 * 
	 * <p>
	 * The <strong>specification version</strong> relates to the
	 * <strong>minor</strong> version of the <strong>implementation</strong>. For
	 * example, if the <strong>implementation version</strong> is {@code
	 * 1.2.34-SNAPSHOT}, then the <strong>specification version</strong> is
	 * {@code 1.2}.
	 * </p>
	 * 
	 * @return <strong>Specification version</strong> implied by this
	 *         <strong>version reference</strong>. For an <strong>implementation
	 *         version</strong>, the version of the <strong>implemented
	 *         specification</strong> is returned. For a <strong>specification
	 *         version</strong>, {@code this} is returned.
	 * @see #major()
	 * @see #minor()
	 */
	default IuComponentVersion specificationVersion() {
		var implementationVersion = implementationVersion();
		if (implementationVersion == null)
			return this;
		else
			return new IuComponentVersion() {
				@Override
				public String name() {
					return IuComponentVersion.this.name();
				}

				@Override
				public String implementationVersion() {
					return null;
				}

				@Override
				public int major() {
					return IuComponentVersion.this.major();
				}

				@Override
				public int minor() {
					return IuComponentVersion.this.minor();
				}

				@Override
				public int hashCode() {
					return Objects.hash(major(), minor(), name(), null);
				}

				@Override
				public boolean equals(Object obj) {
					if (this == obj)
						return true;
					if (obj == null)
						return false;
					if (!(obj instanceof IuComponentVersion))
						return false;

					IuComponentVersion other = (IuComponentVersion) obj;
					return major() == other.major() && minor() == other.minor() && Objects.equals(name(), other.name())
							&& other.implementationVersion() == null;
				}

				@Override
				public String toString() {
					return name() + '-' + major() + '.' + minor() + '+';
				}
			};
	}

	/**
	 * Determines if the <strong>version</strong> implied by {@code this}
	 * <strong>version reference</strong> meets the <strong>version</strong>
	 * required by another <strong>version reference</strong>.
	 * 
	 * <p>
	 * Note that {@link #compareTo(IuComponentVersion) comparing} two
	 * <strong>version references</strong> is not sufficient to determine if a
	 * <strong>dependency</strong> requirement is met since the {@link #major()
	 * major versions} <em>must</em> be identical in order to consider the
	 * requirement met.
	 * </p>
	 * 
	 * @param requiredVersion <strong>Reference</strong> to the required
	 *                        <strong>version</strong>.
	 * @return True if the {@code this} <strong>refers to</strong> a
	 *         <strong>version</strong> with the same {@link #name() name}, and
	 *         either the same {@link #implementationVersion() implementation
	 *         version} or same {@link #major() major version} and the same or
	 *         higher {@link #minor() minor version} as the <strong>version
	 *         reference</strong> passed as an argument to the
	 *         {@code requiredVersion} parameter.
	 */
	default boolean meets(IuComponentVersion requiredVersion) {
		if (!name().equals(requiredVersion.name()))
			return false;

		var requiredImplementationVersion = requiredVersion.implementationVersion();
		if (requiredImplementationVersion != null)
			return requiredImplementationVersion.equals(implementationVersion());
		else
			return major() == requiredVersion.major() && minor() >= requiredVersion.minor();
	}

	/**
	 * Compares two <strong>version references</strong>.
	 * 
	 * <ul>
	 * <li><strong>Version references</strong> with different {@link #name() names}
	 * <em>must not</em> return {@code 0}; otherwise, the return value enforces
	 * natural ordering of the {@link #name() component name}.</li>
	 * <li>An {@link #implementationVersion() implementation version} compared to
	 * another {@link #implementationVersion() implementation version} will return a
	 * value that:
	 * <ul>
	 * <li>enforces the numeric order of the {@link #major() major}, {@link #minor()
	 * minor}, and <a href="https://semver.org/">patch</a> version numbers</li>
	 * <li>enforces the natural order (i.e. +build.12, -SNAPSHOT, -alpha.2, -beta.1)
	 * of the combined <a href="https://semver.org/">build and pre-release
	 * identifiers</a></li>
	 * <li>is {@code 0} when {@link #implementationVersion()} strings are
	 * identical</li>
	 * </ul>
	 * </li>
	 * <li>A {@link #specificationVersion() specification version} compared to
	 * another <strong>version</strong> will return a value that enforces the
	 * numeric order of the {@link #major() major} and {@link #minor() minor}
	 * version numbers</li>
	 * <li>An {@link #implementationVersion() implementation version} is greater
	 * than a {@link #specificationVersion() specification version} with matching
	 * {@link #major() major} and {@link #minor() minor} version numbers</li>
	 * </ul>
	 * 
	 * <p>
	 * Note that comparing two <strong>version references</strong> is not sufficient
	 * to determine if a <strong>dependency</strong> requirement is
	 * {@link #meets(IuComponentVersion) met} since the {@link #major() major
	 * versions} <em>must</em> be identical in order to consider the requirement
	 * met.
	 * </p>
	 * 
	 * <p>
	 * Note that all <strong>versions</strong> are case-sensitive.
	 * </p>
	 * 
	 * {@inheritDoc}
	 */
	@Override
	default int compareTo(IuComponentVersion o) {
		int rv = name().compareTo(o.name());
		if (rv != 0)
			return rv;

		var iv1 = implementationVersion();
		var iv2 = o.implementationVersion();
		if (iv1 != null && iv1.equals(iv2))
			return 0;

		var x1 = major();
		var x2 = o.major();
		rv = Integer.compare(x1, x2);
		if (rv != 0)
			return rv;

		var y1 = minor();
		var y2 = o.minor();
		rv = Integer.compare(y1, y2);
		if (rv != 0)
			return rv;

		if (iv1 == iv2) // both null
			return 0;
		else if (iv1 == null)
			return -1;
		else if (iv2 == null)
			return 1;

		var m1 = SEMANTIC_VERSION_PATTERN.matcher(iv1);
		if (!m1.matches())
			throw new IllegalStateException();
		var z1 = Integer.parseInt(m1.group(3));

		var m2 = SEMANTIC_VERSION_PATTERN.matcher(iv2);
		if (!m2.matches())
			throw new IllegalStateException();
		var z2 = Integer.parseInt(m2.group(3));

		rv = Integer.compare(z1, z2);
		if (rv != 0)
			return rv;

		return iv1.compareTo(iv2);
	}

	/**
	 * <p>
	 * Note that when using {@link IuComponentVersion} in a {@link Set} or as a key
	 * in a {@link HashMap} or {@link Hashtable} that is important to be aware
	 * whether of whether or not the members and/or keys are expected to represent a
	 * {@link #specificationVersion() specification version}.
	 * </p>
	 * 
	 * {@inheritDoc}
	 */
	@Override
	int hashCode();

	/**
	 * <p>
	 * Two <strong>implementation versions</strong> are {@link Object#equals(Object)
	 * equal} if both {@link #name() name} and {@link #implementationVersion()
	 * implementation version} are equal. Two <strong>specification
	 * versions</strong> are {@link Object#equals(Object) equal} if {@link #name()},
	 * {@link #major()}, and {@link #minor()} are equal.
	 * </p>
	 * 
	 * <p>
	 * An <strong>implementation version</strong> is never
	 * {@link Object#equals(Object) equal} to a <strong>specification
	 * version</strong>.
	 * </p>
	 * 
	 * <p>
	 * Note that <strong>versions</strong> are case-sensitive.
	 * </p>
	 * 
	 * {@inheritDoc}
	 */
	@Override
	boolean equals(Object o);

}
