/*
 * Copyright © 2024 Indiana University
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
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import edu.iu.type.IuComponentVersion;

/**
 * Implementation of {@link IuComponentVersion}.
 */
class ComponentVersion implements IuComponentVersion {

	private static final Pattern SPEC_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\-\\.]*$");

	/**
	 * Specification version constant for detecting <a href=
	 * "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0">Jakarta
	 * Servlet version 6</a> or higher.
	 */
	static final ComponentVersion SERVLET_6 = new ComponentVersion("jakarta.servlet-api", 6, 0);

	private final String name;
	private final String version;
	private final int major;
	private final int minor;

	/**
	 * Creates a specification version.
	 * 
	 * @param name  extension name
	 * @param major major version number
	 * @param minor minor version number
	 */
	ComponentVersion(String name, int major, int minor) {
		if (name == null || !NAME_PATTERN.matcher(name).matches())
			throw new IllegalArgumentException(
					"Component name must be non-null, start with a letter, and contain only letters, numbers, dots '.', and hyphens '-'");
		if (major < 0)
			throw new IllegalArgumentException("Component major version number must be non-negative");
		if (minor < 0)
			throw new IllegalArgumentException("Component minor version number must be non-negative");
		this.name = Objects.requireNonNull(name);
		this.version = null;
		this.major = major;
		this.minor = minor;
	}

	/**
	 * Creates an implementation version
	 * 
	 * @param name    extension name
	 * @param version implementation version
	 */
	ComponentVersion(String name, String version) {
		if (name == null || !NAME_PATTERN.matcher(name).matches())
			throw new IllegalArgumentException(
					"Component name must be non-null, start with a letter, and contain only letters, numbers, dots '.', and hyphens '-'");

		if (version == null)
			throw new IllegalArgumentException("Missing version for " + name + ", must be a valid semantic version");

		var semverMatcher = SEMANTIC_VERSION_PATTERN.matcher(version);
		if (!semverMatcher.matches())
			throw new IllegalArgumentException("Invalid version for " + name + ", must be a valid semantic version");

		this.name = name;
		this.version = version;
		this.major = Integer.parseInt(semverMatcher.group(1));
		this.minor = Integer.parseInt(semverMatcher.group(2));
	}

	/**
	 * Reads a dependency item from an extension list
	 * 
	 * @param extenstionListItem extension list item
	 * @param mainAttributes     {@link Manifest#getMainAttributes()}
	 */
	ComponentVersion(String extenstionListItem, Attributes mainAttributes) {
		var extensionAttributePrefix = extenstionListItem.replace('.', '_');
		var extensionNameAttribute = extensionAttributePrefix + '-' + Name.EXTENSION_NAME;
		name = mainAttributes.getValue(extensionNameAttribute);
		if (name == null)
			throw new IllegalArgumentException(
					"Missing " + extensionNameAttribute + " in META-INF/MANIFEST.MF main attributes");

		var implementationVersionAttribute = extensionAttributePrefix + '-' + Name.IMPLEMENTATION_VERSION;
		version = mainAttributes.getValue(implementationVersionAttribute);
		if (version != null) {
			var semverMatcher = SEMANTIC_VERSION_PATTERN.matcher(version);
			if (!semverMatcher.matches())
				throw new IllegalArgumentException("Invalid version for " + implementationVersionAttribute
						+ " in META-INF/MANIFEST.MF main attributes, must be a valid semantic version");
			major = Integer.parseInt(semverMatcher.group(1));
			minor = Integer.parseInt(semverMatcher.group(2));

		} else {
			var specificationVersionAttribute = extensionAttributePrefix + '-' + Name.SPECIFICATION_VERSION;
			var specificationVersion = mainAttributes.getValue(specificationVersionAttribute);
			if (specificationVersion == null)
				throw new IllegalArgumentException("Missing " + implementationVersionAttribute + " or "
						+ specificationVersionAttribute + " in META-INF/MANIFEST.MF main attributes");

			var specverMatcher = SPEC_VERSION_PATTERN.matcher(specificationVersion);
			if (!specverMatcher.matches())
				throw new IllegalArgumentException("Invalid version for " + specificationVersionAttribute
						+ " in META-INF/MANIFEST.MF main attributes , must be a valid semantic version");
			major = Integer.parseInt(specverMatcher.group(1));
			minor = Integer.parseInt(specverMatcher.group(2));
		}
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String implementationVersion() {
		return version;
	}

	@Override
	public int major() {
		return major;
	}

	@Override
	public int minor() {
		return minor;
	}

	@Override
	public ComponentVersion specificationVersion() {
		if (version == null)
			return this;
		else
			return new ComponentVersion(name, major, minor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, name, version);
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
		return major == other.major() && minor == other.minor() && Objects.equals(name, other.name())
				&& Objects.equals(version, other.implementationVersion());
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		sb.append(name).append('-');
		if (version == null)
			sb.append(major).append('.').append(minor).append('+');
		else
			sb.append(version);
		return sb.toString();
	}

}
