/*******************************************************************************
 * Copyright (c) 2016 TypeFox GmbH (http://www.typefox.io) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4j.jsonrpc.json;

import java.lang.reflect.Type;

/**
 * A description of a JSON-RPC method. 
 */
public class JsonRpcMethod {
	
	private final String methodName;
	private final Type[] parameterTypes;
	private final Type returnType;
	private final boolean isNotification;
	
	private JsonRpcMethod(String methodName, Type[] parameterTypes, Type returnType, boolean isNotification) {
		if (methodName == null)
			throw new NullPointerException("methodName");
		this.methodName = methodName;
		this.parameterTypes = parameterTypes;
		this.returnType = returnType;
		this.isNotification = isNotification;
	}

	public String getMethodName() {
		return methodName;
	}
	
	public Type[] getParameterTypes() {
		return parameterTypes;
	}
	
	public Type getReturnType() {
		return returnType;
	}
	
	public boolean isNotification() {
		return isNotification;
	}
	
	public static JsonRpcMethod notification(String name, Type... parameterTypes) {
		return new JsonRpcMethod(name, parameterTypes, Void.class, true);
	}
	
	public static JsonRpcMethod request(String name, Type returnType, Type... parameterTypes) {
		return new JsonRpcMethod(name, parameterTypes, returnType, false);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (isNotification)
			builder.append("JsonRpcMethod (notification) {\n");
		else
			builder.append("JsonRpcMethod (request) {\n");
		builder.append("\tmethodName: ").append(methodName).append('\n');
		if (parameterTypes != null)
			builder.append("\tparameterTypes: ").append(parameterTypes).append('\n');
		if (returnType != null)
			builder.append("\treturnType: ").append(returnType).append('\n');
		builder.append("}");
		return builder.toString();
	}

}
