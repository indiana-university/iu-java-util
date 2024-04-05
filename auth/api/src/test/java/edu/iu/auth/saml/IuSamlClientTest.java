package edu.iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class IuSamlClientTest {

@Test
public void testSamlClientDefault() {
	final var client = IuTest.mockWithDefaults(IuSamlClient.class);
	assertNull(client.getMetaDataUrls());
	assertNull(client.getIdentityProviderURL());
	assertEquals(client.getMetadataTtl(), Duration.ofMillis(300000L));
}
}
