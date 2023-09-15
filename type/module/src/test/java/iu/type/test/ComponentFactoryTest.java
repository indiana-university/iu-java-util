package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuComponent;
import iu.type.ComponentFactory;

@SuppressWarnings("javadoc")
public class ComponentFactoryTest {

	@Test
	public void testHandlesInvalidPath() throws MalformedURLException {
		var path = mock(Path.class);
		var uri = mock(URI.class);
		when(path.toUri()).thenReturn(uri);
		when(uri.toURL()).thenThrow(MalformedURLException.class);
		assertThrows(IllegalArgumentException.class, () -> ComponentFactory.newComponent(path));
	}

	@Test
	public void testLoadsComponent() throws Throwable {
		var properties = new Properties();
		try (var in = ClassLoader.getSystemClassLoader().getResourceAsStream("META-INF/testcomponent.properties")) {
			properties.load(in);
		}

		Queue<Path> modulepath = new ArrayDeque<>();

		var testcomponentJar = Path.of(properties.getProperty("testcomponent.jar")).toRealPath();
		modulepath.offer(testcomponentJar);

		for (var jar : Files.newDirectoryStream(Path.of(properties.getProperty("testcomponent.deps")).toRealPath()))
			modulepath.offer(jar);

		IuComponent component = ComponentFactory.newComponent(modulepath.toArray(new Path[modulepath.size()]));
		assertNotNull(component);
	}

}
