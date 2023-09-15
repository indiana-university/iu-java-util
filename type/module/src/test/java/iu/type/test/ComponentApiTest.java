package iu.type.test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuComponent;
import iu.type.ComponentFactory;

@SuppressWarnings("javadoc")
public class ComponentApiTest {

	@Test
	public void testNewComponent() {
		var path = mock(Path.class);
		try (var componentFactory = mockStatic(ComponentFactory.class)) {
			IuComponent.of(path);
			componentFactory.verify(() -> ComponentFactory.newComponent(path));
		}
	}

}
