package iu.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.annotation.Resource;

@ExtendWith(LegacyContextSupport.class)
@SuppressWarnings("javadoc")
public class AnnotationBridgeTest {

	@Test
	public void testPotentiallyRemote() {
		final var legacyResource = BackwardsCompatibility.getCompatibleClass(Resource.class,
				LegacyContextSupport.loader);
	}

}
