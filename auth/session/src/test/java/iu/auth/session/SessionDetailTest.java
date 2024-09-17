package iu.auth.session;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("javadoc")
class SessionDetailTest {
    private Map<String, Object> attributes;
    private Session session;
    private SessionDetail sessionDetail;
    
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
        attributes = new HashMap<>();
        session = Mockito.mock(Session.class);
        sessionDetail = new SessionDetail(attributes, session);
    }
    
    @Test
    void invokeWithHashCode() throws Throwable {
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        assertEquals(proxy.hashCode(), sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("hashCode"), null));
    }

     @Test
    void invokeWithEquals() throws Throwable {
        Object proxy = Proxy.newProxyInstance(SessionDetail.class.getClassLoader(), new Class[]{}, sessionDetail);
        assertTrue((Boolean) sessionDetail.invoke(proxy, Object.class.getMethod("equals", Object.class), new Object[]{proxy}));
        assertFalse((Boolean) sessionDetail.invoke(proxy, Object.class.getMethod("equals", Object.class), new Object[]{new Object()}));
    }

 
    @Test
    void invokeWithToString() throws Throwable {
        Object proxy = Proxy.newProxyInstance(SessionDetail.class.getClassLoader(), new Class[]{}, sessionDetail);
        assertEquals(attributes.toString(), sessionDetail.invoke(proxy, Object.class.getMethod("toString"), null));
    }
    
    @Test
    void invokeWithIsMethod() throws Throwable {
        attributes.put("notThere", true);
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        assertTrue((boolean)sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("isNotThere"), null));
    }

    @Test
    void invokeWithGetMethod() throws Throwable {
        attributes.put("givenName", "foo");
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        assertEquals("foo", sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("getGivenName"), null));
    }

    @Test
    void invokeWithSetMethodExistingAttribute() throws Throwable {
    	attributes.put("givenName", "foo");
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("setGivenName", String.class), new Object[]{"foo"});
        assertEquals("foo", attributes.get("givenName"));
        assertFalse(session.isChange());
    }
    
    @Test
    void invokeWithSetMethodForNonMatchAttributeValue() throws Throwable {
    	attributes.put("givenName", "foo");
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("setGivenName", String.class), new Object[]{"bar"});
        assertEquals("bar", attributes.get("givenName"));
        Mockito.verify(session).setChange(true);
    }
    
    @Test
    void invokeWithSetMethod() throws Throwable {
      Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("setGivenName", String.class), new Object[]{"bar"});
        assertEquals("bar", attributes.get("givenName"));
        Mockito.verify(session).setChange(true);
    }

    @Test
    void invokeWithUnsupportedMethod() {
        Object proxy = Proxy.newProxyInstance(SessionDetailInterface.class.getClassLoader(), new Class[]{SessionDetailInterface.class}, sessionDetail);
        assertThrows(UnsupportedOperationException.class, () -> sessionDetail.invoke(proxy, SessionDetailInterface.class.getMethod("unsupported"), null));
    }
}
