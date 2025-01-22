package iu.type.container;

import static org.mockito.Mockito.mockStatic;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.logging.IuLogContext;

@SuppressWarnings("javadoc")
public class TypeContainerTestCase {

	Path config;
	MockedStatic<IuLogContext> mockLogContext;

	@BeforeEach
	public void setupConfig() {
		config = Path.of("target").resolve(IdGenerator.generateId());
		System.setProperty("iu.config", config.toString());
		mockLogContext = mockStatic(IuLogContext.class);
	}

	@AfterEach
	public void teardownConfig() {
		mockLogContext.close();
		System.clearProperty("iu.config");
	}

}
