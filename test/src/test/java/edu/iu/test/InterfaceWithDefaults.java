package edu.iu.test;

@SuppressWarnings("javadoc")
public interface InterfaceWithDefaults {

	String getAbstractString();

	default String getDefaultString() {
		return "foobar";
	}
	
	default void throwsUnsupportedOperationException() {
		throw new UnsupportedOperationException();
	}
	
}
