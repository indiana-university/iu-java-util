package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentModuleFinderTest {

	@Test
	public void testRuntime() throws IOException {
		var path = TestArchives.getModulePath("testruntime");
		ModuleReference ref;
		try (var finder = new ComponentModuleFinder(path)) {
			Set<String> names = new HashSet<>();
			finder.findAll().forEach(r -> names.add(r.descriptor().name()));
			assertTrue(names.remove("iu.util.type.testruntime"), names.toString());
			assertTrue(names.remove("org.eclipse.parsson"), names.toString());
			assertTrue(names.isEmpty(), names.toString());

			ref = finder.find("iu.util.type.testruntime").get();
			try (var reader = ref.open()) {
				assertSame(reader, ref.open());
				var url = reader.find("META-INF/runtime.properties").get().toURL();
				var c = url.openConnection();
				c.setUseCaches(false);
				Properties props = new Properties();
				try (var in = c.getInputStream()) {
					props.load(in);
				}
				assertEquals("a property value", props.get("test.property"));
			}
			finder.close(); // extra call should not throw
			assertThrows(IllegalStateException.class, () -> finder.find(""));
			assertThrows(IllegalStateException.class, () -> finder.findAll());
		}
		assertThrows(IllegalStateException.class, () -> ref.open());
		for (var p : path)
			Files.delete(p);
	}

}
