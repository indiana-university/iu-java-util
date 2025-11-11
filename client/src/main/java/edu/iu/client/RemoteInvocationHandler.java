package edu.iu.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;

import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.UnsafeConsumer;

/**
 * May be extended for synchronous client=side remote invocation of a Java
 * interface via HTTP POST.
 */
public abstract class RemoteInvocationHandler implements InvocationHandler {

	/**
	 * Default constructor.
	 */
	protected RemoteInvocationHandler() {
	}

	/**
	 * Supplies the remote invocation URI.
	 * 
	 * @param method remote method
	 * @return {@link URI}
	 */
	protected abstract URI uri(Method method);

	/**
	 * Adds authorization headers to a pending remote call request
	 * 
	 * @param requestBuilder pending remote call request
	 */
	protected abstract void authorize(HttpRequest.Builder requestBuilder);

	/**
	 * Adds request payload to a pending remote call request.
	 * 
	 * <p>
	 * Default behavior is to POST arguments as a JSON array, using
	 * {@link #adapt(Type)} for conversion.
	 * </p>
	 * 
	 * @param requestBuilder pending remote call request
	 * @param method         method
	 * @param args           arguments
	 */
	protected void payload(HttpRequest.Builder requestBuilder, Method method, Object[] args) {
		final var parameters = method.getParameters();
		final var requestBody = IuJson.array();
		for (var i = 0; i < parameters.length; i++)
			requestBody.add(adapt(parameters[i].getParameterizedType()).toJson(args[i]));

		final var request = requestBody.build().toString();
		requestBuilder.header("Content-Type", "application/json");
		requestBuilder.POST(BodyPublishers.ofString(request));
	}

	/**
	 * Get a {@link IuJsonAdapter} for converting to a generic type.
	 * 
	 * @param type Java type
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	protected <T> IuJsonAdapter<T> adapt(Type type) {
		if (type == RemoteInvocationFailure.class //
				|| type == RemoteInvocationDetail.class)
			return (IuJsonAdapter<T>) IuJsonAdapter.from((Class<?>) type,
					IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, a -> adapt(a));
		else
			return IuJsonAdapter.of(type, a -> adapt(a));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "hashCode":
			return System.identityHashCode(proxy);
		case "equals":
			return proxy == args[0];
		case "toString":
			return toString();
		}

		final UnsafeConsumer<HttpRequest.Builder> request = builder -> {
			authorize(builder);
			payload(builder, method, args);
		};

		try {
			final var type = method.getGenericReturnType();
			if (type == void.class)
				return IuHttp.send(uri(method), request, IuHttp.NO_CONTENT);
			else
				return adapt(type).fromJson(IuHttp.send(uri(method), request, IuHttp.READ_JSON));
		} catch (HttpException e) {
			Throwable remoteError;
			String body = null;
			try {
				body = IuText.utf8(IuStream.read(e.getResponse().body()));
				remoteError = new RemoteInvocationException(
						(RemoteInvocationFailure) adapt(RemoteInvocationFailure.class).fromJson(IuJson.parse(body)));
				remoteError.addSuppressed(e);
			} catch (Throwable errorHandlingFailure) {
				remoteError = new IllegalStateException(body, e);
				remoteError.addSuppressed(errorHandlingFailure);
			}
			throw remoteError;
		}
	}

}
