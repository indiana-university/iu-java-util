package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import edu.iu.type.IuComponent.Kind;

@SuppressWarnings("javadoc")
public class ComponentArchiveTest {

	private static final byte[] B0 = new byte[0];
	private static final byte[] EMPTY_JAR;

	static {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			var manifest = new Manifest();
			var mainAttributes = manifest.getMainAttributes();
			mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
			manifest.write(jar);
			jar.closeEntry();
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
		EMPTY_JAR = out.toByteArray();
	}

	private ArchiveSource createSource(String name, String version, Iterable<String> classPath,
			Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			var manifest = new Manifest();
			var mainAttributes = manifest.getMainAttributes();
			mainAttributes.put(Name.MANIFEST_VERSION, "1.0");

			if (name != null)
				mainAttributes.put(Name.EXTENSION_NAME, name);
			if (version != null)
				mainAttributes.put(Name.IMPLEMENTATION_VERSION, version);
			if (classPath != null) {
				StringBuilder classPathAttribute = new StringBuilder();
				for (var classPathEntry : classPath) {
					if (classPathAttribute.length() > 0)
						classPathAttribute.append(' ');
					classPathAttribute.append(classPathEntry);
				}
				if (classPathAttribute.length() > 0)
					mainAttributes.put(Name.CLASS_PATH, classPathAttribute.toString());
			}

			manifest.write(jar);
			jar.closeEntry();

			for (var entry : entries.entrySet()) {
				jar.putNextEntry(new JarEntry(entry.getKey()));
				jar.write(entry.getValue());
				jar.closeEntry();
			}
		}
		return new ArchiveSource(new ByteArrayInputStream(out.toByteArray()));
	}

	private void assertInvalidNameOrVersion(String expectedMessage, String name, String version) {
		assertInvalidSource(expectedMessage, name, version, null);
	}

	private void assertInvalidEntries(String expectedMessage, String... entryNames) {
		assertInvalidSource(expectedMessage, null, null, null, entryNames);
	}

	private void assertInvalidSource(String expectedMessage, String name, String version, Iterable<String> classPath,
			String... entryNames) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		for (var entryName : entryNames)
			if (entryName.endsWith(".jar"))
				entries.put(entryName, EMPTY_JAR);
			else
				entries.put(entryName, new byte[0]);

		var threw = assertThrows(IllegalArgumentException.class,
				() -> ComponentArchive.of(createSource(name, version, classPath, entries)));
		try {
			assertEquals(expectedMessage, threw.getMessage());
		} catch (AssertionFailedError e) {
			e.addSuppressed(threw);
			throw e;
		}
	}

	@Test
	public void testRequiresNameAndVersion() {
		var expectedMessage = "Component archive must provide a name as either Extension-Name in META-INF/MANIFEST.MF or artifactId in pom.properties";
		assertInvalidNameOrVersion(expectedMessage, null, null);
		assertInvalidNameOrVersion(expectedMessage, "", null);
	}

	@Test
	public void testRequiresVersion() {
		var expectedMessage = "Component archive must provide a version as either Implementation-Version in META-INF/MANIFEST.MF or version in pom.properties";
		assertInvalidNameOrVersion(expectedMessage, "component-name", null);
		assertInvalidNameOrVersion(expectedMessage, "component-name", "");
	}

	@Test
	public void testReadsModularJar() throws IOException {
		var source = createSource("component-name", "component-version", null, Map.of("module-info.class", B0));
		var archive = ComponentArchive.of(source);
		assertEquals(Kind.MODULAR_JAR, archive.kind());
	}

	@Test
	public void testEmbedsNamedLib() throws IOException {
		var expectedName = "embedded-lib";
		var embeddedLib = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(embeddedLib)) {
			var manifest = new Manifest();
			var mainAttributes = manifest.getMainAttributes();
			mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
			mainAttributes.put(Name.EXTENSION_NAME, expectedName);
			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			manifest.write(jar);
			jar.closeEntry();
		}
		var source = createSource("component-name", "component-version", Set.of("embedded-lib.jar"),
				Map.of("module-info.class", B0, "embedded-lib.jar", embeddedLib.toByteArray()));
		var archive = ComponentArchive.of(source);
		var embeddedComponents = archive.embeddedComponents().iterator();
		assertTrue(embeddedComponents.hasNext());
		assertEquals(expectedName, embeddedComponents.next().name());
		assertFalse(embeddedComponents.hasNext());
	}

	@Test
	public void testRejectsEarOrRar() {
		var expectedMessage = "Component archive must not be an Enterprise Application (ear) or Resource Adapter Archive (rar) file";
		assertInvalidEntries(expectedMessage, //
				"META-INF/ejb/endorsed/.jar", "META-INF/ejb/lib/.jar", "META-INF/lib/.jar", ".jar");
		assertInvalidEntries(expectedMessage, "META-INF/application.xml");
		assertInvalidEntries(expectedMessage, "META-INF/ra.xml");
		assertInvalidEntries(expectedMessage, "any/.war");
		assertInvalidEntries(expectedMessage, "any/.rar");
		assertInvalidEntries(expectedMessage, "any/.so");
		assertInvalidEntries(expectedMessage, "any/.dll");
	}

	@Test
	public void testRejectsUberJar() {
		var expectedMessage = "Component archive must not be a shaded (uber-)jar";
		assertInvalidEntries(expectedMessage, "META-INF/maven/a/pom.properties", "META-INF/maven/b/pom.properties");
	}

	@Test
	public void testRejectsTypesAsWebResource() {
		var expectedMessage = "Web archive must not define types outside WEB-INF/classes/";
		assertInvalidEntries(expectedMessage, ".class", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", ".class");
	}

	@Test
	public void testRejectsWebEmbeddingDependenciesOutsideWebInfLib() {
		var expectedMessage = "Web archive must not embed components outside WEB-INF/lib/";
		assertInvalidEntries(expectedMessage, "META-INF/lib/.jar", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/lib/.jar");
	}

	@Test
	public void testRejectsIuTypePropertiesAsWebResource() {
		var expectedMessage = "Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu-type.properties", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/iu-type.properties");
	}

	@Test
	public void testRejectsIuPropertiesAsWebResource() {
		var expectedMessage = "Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "WEB-INF/");
		assertInvalidEntries(expectedMessage, "WEB-INF/", "META-INF/iu.properties");
	}

	@Test
	public void testRejectsIuPropertiesInModularComponent() {
		var expectedMessage = "Modular component archive must not include META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "module-info.class");
		assertInvalidEntries(expectedMessage, "module-info.class", "META-INF/iu.properties");
	}

	@Test
	public void testRejectsWebModularComponent() {
		var expectedMessage = "Modular component archive must not include META-INF/iu.properties";
		assertInvalidEntries(expectedMessage, "META-INF/iu.properties", "module-info.class");
		assertInvalidEntries(expectedMessage, "module-info.class", "META-INF/iu.properties");
	}

	@Test
	public void testRejectsWebComponentWithExtensionName() {
		var expectedMessage = "Web archive must not include Extension-Name in META-INF/MANIFEST.MF";
		assertInvalidSource(expectedMessage, "invalid-war", "1.0", null, "WEB-INF/");
	}

	@Test
	public void testRejectsWebComponentWithImplementationVersion() {
		var expectedMessage = "Web archive must not include Implementation-Version in META-INF/MANIFEST.MF";
		assertInvalidSource(expectedMessage, null, "1.0", null, "WEB-INF/");
	}
}
