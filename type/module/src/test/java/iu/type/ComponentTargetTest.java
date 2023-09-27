package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentTargetTest {

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		// prevents mockStatic(Files.class) from interfering with class loader
		Class.forName(ComponentTarget.class.getName());
	}

	@Test
	public void testItWorks() throws IOException {
		var path = TemporaryFile.init(temp -> {
			try (var target = new ComponentTarget(temp)) {
				target.put("foo", new ByteArrayInputStream("bar".getBytes()));
			}
			return temp;
		});
		try ( //
				var in = Files.newInputStream(path); //
				var jar = new JarInputStream(in)) {
			assertNotNull(jar.getNextEntry());
			assertEquals("bar", new String(jar.readAllBytes()));
			jar.closeEntry();
			assertNull(jar.getNextEntry());
		}
		Files.delete(path);
	}

}
