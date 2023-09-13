package edu.iu.type.testcomponent;

import javax.annotation.Resource;
import javax.interceptor.Interceptors;

@Interceptors(TestInterceptor.class)
@Resource
public class TestResource {

}
