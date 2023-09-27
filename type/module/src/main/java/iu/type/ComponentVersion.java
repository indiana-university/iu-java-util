package iu.type;

import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.regex.Pattern;

import edu.iu.type.IuComponentVersion;

class ComponentVersion implements IuComponentVersion {

	private static final Pattern SPEC_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

	static final ComponentVersion SERVLET_6 = new ComponentVersion("jakarta.servlet-api", 6, 0);

	private final String name;
	private final String version;
	private final int major;
	private final int minor;

	ComponentVersion(String name, int major, int minor) {
		if (major < 0)
			throw new IllegalArgumentException("Component major version number must be non-negative");
		if (minor < 0)
			throw new IllegalArgumentException("Component minor version number must be non-negative");
		this.name = Objects.requireNonNull(name);
		this.version = null;
		this.major = major;
		this.minor = minor;
	}

	ComponentVersion(String name, String version) {
		this.name = Objects.requireNonNull(name);
		this.version = Objects.requireNonNull(version);

		var semverMatcher = SEMANTIC_VERSION_PATTERN.matcher(version);
		if (!semverMatcher.matches())
			throw new IllegalArgumentException("Invalid version for " + name + ", must be a valid semantic version");
		major = Integer.parseInt(semverMatcher.group(1));
		minor = Integer.parseInt(semverMatcher.group(2));
	}

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
						+ " in META-INF/MANIFEST.MF main attributes , must be a valid semantic version");
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
	public IuComponentVersion specificationVersion() {
		if (version == null)
			return this;
		else
			return new ComponentVersion(name, major, minor);
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
	public int hashCode() {
		return Objects.hash(major, minor, name, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ComponentVersion other = (ComponentVersion) obj;
		return major == other.major && minor == other.minor && Objects.equals(name, other.name)
				&& Objects.equals(version, other.version);
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
