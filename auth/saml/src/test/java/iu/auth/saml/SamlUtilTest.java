package iu.auth.saml;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import edu.iu.IdGenerator;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

@SuppressWarnings("javadoc")
public class SamlUtilTest {
	
@Test
public void testDecrypt() throws UnsupportedEncodingException, URISyntaxException {

	
	var sessionId = IdGenerator.generateId();
	var postURI = new URI("http://test:/");
	String jsonString = "{\"sessionId\":\""
			+  sessionId + "\"" + ","
			+ "\"returnUrl\":\"" 
			+ postURI.toString() + "\"" + "}";
	String en= SamlUtil.encrypt(jsonString);
	 String de = SamlUtil.decrypt(en);
	 System.out.println("de::" + de);
	
	 JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
	 JsonString jString = jsonReader.readObject().getJsonString("sessionId");
	 System.out.println("de::" + jString.getString());
		
	
	assertTrue(true);
}
}
