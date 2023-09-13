package edu.iu.type.testcomponent;

import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
public class TestInterceptor {

	@AroundConstruct
	public Object interceptConstruct(InvocationContext ctx) throws Exception {
		throw new UnsupportedOperationException("called interceptConstruct");
	}
	
	@AroundInvoke
	public Object interceptMethod(InvocationContext ctx) throws Exception {
		throw new UnsupportedOperationException("called interceptMethod");
	}
	
}
