package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;



@SuppressWarnings("javadoc")
public class SamlPrincipalTest {

	@Test
	public void testEquals()  {
		final List<SamlPrincipal> principals = new ArrayList<>();
		final var uri_1 = URI.create("test://ldp/" + IdGenerator.generateId());
		
		final var names = List.of("foo", "bar");
		final var realms = List.of(IdGenerator.generateId(), IdGenerator.generateId());
		for (final var realm : realms) {

			for (final var name : names) {
				final Map<String, Object> claims = new LinkedHashMap<>();
				principals.add(new SamlPrincipal(name, name, name + "@iu.edu", uri_1.toString(), realm, claims));
			}
		}

		for (var i = 0; i < principals.size(); i++)
			for (var j = 0; j < principals.size(); j++) {
				final var pi = principals.get(i);
				final var pj = principals.get(j);
				if (i == j) {
					assertNotEquals(pi, new Object());
					assertEquals(pi, pj);
					assertEquals(pi.hashCode(), pj.hashCode());
				} else {
					assertNotEquals(pi, pj);
					assertNotEquals(pj, pi);
					assertNotEquals(pi.hashCode(), pj.hashCode());
				}
			}
	}
	
	@Test
	public void testPrincipal() {
		final Map<String, Object> claims = new LinkedHashMap<>();
		final var entityId = "test://ldp/";
		final var realm = IdGenerator.generateId();
		final var name = "foo";
		final var emailAddress = "foo@iu.edu";
		
		SamlPrincipal samlPrincipal = new SamlPrincipal("foo", "foo", emailAddress, entityId
				, realm, claims);
		
		assertEquals("SAML Principal ID [" + name + "; " + name + "; " + emailAddress + "; " + entityId + "; " + realm
				+ "] ", samlPrincipal.toString());
		
		assertEquals(name,samlPrincipal.getDisplayName());
		assertEquals(name,samlPrincipal.getName());
		assertEquals(emailAddress,samlPrincipal.getEmailAddress());
		//assertEquals(name,samlPrincipal.));
		final var subject = samlPrincipal.getSubject();
		assertSame(samlPrincipal, subject.getPrincipals().iterator().next());
		assertEquals(1, subject.getPrincipals().size());
		assertTrue(subject.getPrivateCredentials().isEmpty());
		assertTrue(subject.getPublicCredentials().isEmpty());
		
		assertEquals(realm, samlPrincipal.realm());
		assertEquals(0,samlPrincipal.getClaims().size());
	}

}
