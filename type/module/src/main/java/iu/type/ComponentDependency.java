package iu.type;

import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.regex.Pattern;

class ComponentDependency {

	/**
	 * @see <a href="https://semver.org/">Semantic Versioning</a>
	 */
	private static final Pattern VALID_VERSION = Pattern.compile(
			"^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

	private static final Pattern SPEC_VERSION = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

	static final ComponentDependency SERVLET_6 = new ComponentDependency("jakarta.servlet-api", 6, 0);

	private static int getMajorVersion(String version) {
		return Integer.parseInt(version.substring(0, version.indexOf('.')));
	}

	private static int getMinorVersion(String version) {
		var dot = version.indexOf('.');
		var nextDot = version.indexOf('.', dot + 1);
		return Integer.parseInt(nextDot == -1 ? version.substring(dot + 1) : version.substring(dot + 1, nextDot));
	}

	private final String name;
	private final String version;
	private final int major;
	private final int minor;

	private ComponentDependency(String name, int major, int minor) {
		this.name = name;
		this.version = null;
		this.major = major;
		this.minor = minor;
	}

	ComponentDependency(String extenstionListItem, Attributes mainAttributes) {
		var extensionAttributePrefix = extenstionListItem.replace('.', '_');
		var extensionNameAttribute = extensionAttributePrefix + '-' + Name.EXTENSION_NAME;
		name = mainAttributes.getValue(extensionNameAttribute);
		if (name == null)
			throw new IllegalArgumentException(
					"Missing " + extensionNameAttribute + " in META-INF/MANIFEST.MF main attributes");

		var implementationVersionAttribute = extensionAttributePrefix + '-' + Name.IMPLEMENTATION_VERSION;
		version = mainAttributes.getValue(implementationVersionAttribute);
		if (version != null) {
			if (!VALID_VERSION.matcher(version).matches())
				throw new IllegalArgumentException("Invalid " + implementationVersionAttribute
						+ " in META-INF/MANIFEST.MF main attributes, must be a valid semantic version");

			major = getMajorVersion(version);
			minor = getMinorVersion(version);

		} else {
			var specificationVersionAttribute = extensionAttributePrefix + '-' + Name.SPECIFICATION_VERSION;
			var specificationVersion = mainAttributes.getValue(specificationVersionAttribute);

			if (specificationVersion == null)
				throw new IllegalArgumentException("Missing " + implementationVersionAttribute + " or "
						+ specificationVersionAttribute + " in META-INF/MANIFEST.MF main attributes");
			else if (!SPEC_VERSION.matcher(specificationVersion).matches())
				throw new IllegalArgumentException("Invalid " + specificationVersionAttribute
						+ " in META-INF/MANIFEST.MF main attributes, must be a valid semantic minor version");

			major = getMajorVersion(specificationVersion);
			minor = getMinorVersion(specificationVersion);
		}
	}

	boolean isMetBy(ComponentArchive archive) {
		if (!name.equals(archive.name()))
			return false;

		var version = archive.version();
		if (!VALID_VERSION.matcher(version).matches())
			throw new IllegalArgumentException(
					"Invalid version for " + archive.name() + ", must be a valid semantic version");

		if (this.version != null)
			return this.version.equals(version);

		return major == getMajorVersion(version) && minor <= getMinorVersion(version);
	}

	boolean isMetBy(Iterable<ComponentArchive> archiveSet) {
		for (ComponentArchive archive : archiveSet)
			if (isMetBy(archive))
				return true;
		return false;
	}

	boolean isAtLeast(ComponentDependency specificationLevel) {
		if (!name.equals(specificationLevel.name))
			return false;

		if (specificationLevel.version != null)
			return this.version.equals(specificationLevel.version);

		return major >= specificationLevel.major
				|| (major == specificationLevel.major && minor >= specificationLevel.minor);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		sb.append("ComponentDependency on ").append(name).append('-');
		if (version == null)
			sb.append(major).append('.').append(minor).append("+, less than ").append(major + 1).append(".0");
		else
			sb.append(version);
		return sb.toString();
	}

}