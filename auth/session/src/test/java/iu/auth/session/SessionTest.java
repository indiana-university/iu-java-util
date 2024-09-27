package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

@SuppressWarnings("javadoc")
public class SessionTest {

	private Session session;
	private URI resourceUri;
	private Duration expires;

	interface SessionDetailInterface {
		String getGivenName();

		void setGivenName(String givenName);

		boolean isNotThere();

		void unsupported();

		@Override
		int hashCode();
	}

	@BeforeEach
	void setUp() {
		resourceUri = URI.create("http://localhost");
		expires = Duration.ofHours(1);
		session = new Session(resourceUri, expires);
	}

	@Test
	void testCreateSessionWithValidParameters() {
		assertEquals(resourceUri, session.resourceUri);
		assertNotNull(session.issueAt);
		assertNotNull(session.expires);
		assertTrue(session.expires.isAfter(session.issueAt));
	}

	@Test
	void testGetDetail() {
		Object detail = session.getDetail(SessionDetailInterface.class);
		assertNotNull(detail);
		assertEquals(SessionDetailInterface.class, detail.getClass().getInterfaces()[0]);
	}

	@Test
	void testDetailKeyExistsInSession() {

		Map<String, Map<String, Object>> details = new HashMap<>();
		Map<String, Object> detailMap = new HashMap<>();

		detailMap.put("givenName", "John");
		detailMap.put("notThere", false);
		details.put("SessionDetailInterface", detailMap);

		final var builder = IuJson.object() //
				.add("iat", 1625140800) //
				.add("exp", 1625227200);//
		IuJson.add(builder, "attributes", () -> details, IuJsonAdapter.basic());

		session = new Session(builder.build());
		Object detail = session.getDetail(SessionDetailInterface.class);
		assertNotNull(detail);
		assertEquals(SessionDetailInterface.class, detail.getClass().getInterfaces()[0]);
	}

	@Test
	void testChangeFlagManipulation() {
		assertFalse(session.isChanged());
		session.setChange(true);
		assertTrue(session.isChanged());
	}

	@Test
	void testGetExpires() {
		Instant expirationTime = session.getExpires();
		assertNotNull(expirationTime);
		assertTrue(expirationTime.isAfter(session.issueAt));
	}

	@Test
	void testGetIssueAt() {
		Instant creationTime = session.getIssueAt();
		assertNotNull(creationTime);
		assertTrue(creationTime.isBefore(session.expires));
	}

	@Test
	void testTokenizeWithValidParameters() {
		final var secretKey = new byte[32];
		final var issuerKey = WebKey.ephemeral(Algorithm.HS256);
		final var algorithm = WebKey.Algorithm.HS256;
		String token = session.tokenize(secretKey, issuerKey, algorithm);
		assertNotNull(token);
	}

	@Test
	void testCreateSessionFromValidJsonValue() {
		final var builder = IuJson.object() //
				.add("iat", 1625140800) //
				.add("exp", 1625227200) //
				.add("attributes", IuJson.object().build()).build();
		session = new Session(builder);
		assertNotNull(session);
		assertEquals(Instant.ofEpochSecond(1625140800), session.getIssueAt());
		assertEquals(Instant.ofEpochSecond(1625227200), session.getExpires());
		assertTrue(session.details.isEmpty());
	}
}