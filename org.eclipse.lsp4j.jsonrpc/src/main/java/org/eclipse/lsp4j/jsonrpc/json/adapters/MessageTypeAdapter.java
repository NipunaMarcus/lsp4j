/*******************************************************************************
 * Copyright (c) 2016, 2017 TypeFox GmbH (http://www.typefox.io) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4j.jsonrpc.json.adapters;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageConstants;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.MethodProvider;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * The type adapter for messages dispatches between the different message types: {@link RequestMessage},
 * {@link ResponseMessage}, and {@link NotificationMessage}.
 */
public class MessageTypeAdapter extends TypeAdapter<Message> {
	
	public static class Factory implements TypeAdapterFactory {
		
		private final MessageJsonHandler handler;
		
		public Factory(MessageJsonHandler handler) {
			this.handler = handler;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
			if (!Message.class.isAssignableFrom(typeToken.getRawType()))
				return null;
			return (TypeAdapter<T>) new MessageTypeAdapter(handler, gson);
		}
		
	}
	
	private static Type[] EMPTY_TYPE_ARRAY = {};

	private final MessageJsonHandler handler;
	private final Gson gson;
	
	public MessageTypeAdapter(MessageJsonHandler handler, Gson gson) {
		this.handler = handler;
		this.gson = gson;
	}

	@Override
	public Message read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		
		in.beginObject();
		String jsonrpc = null, id = null, method = null;
		Object rawParams = null;
		Object result = null;
		ResponseError error = null;
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
			case "jsonrpc": {
				jsonrpc = in.nextString();
				break;
			}
			case "id": {
				id = in.nextString();
				break;
			}
			case "method": {
				method = in.nextString();
				break;
			}
			case "params": {
				rawParams = parseParams(in, method);
				break;
			}
			case "result": {
				Type type = null;
				MethodProvider methodProvider = handler.getMethodProvider();
				if (methodProvider != null && id != null) {
					String resolvedMethod = methodProvider.resolveMethod(id);
					if (resolvedMethod != null) {
						JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(resolvedMethod);
						if (jsonRpcMethod != null)
							type = jsonRpcMethod.getReturnType();
					}
				}
				if (type == null)
					result = new JsonParser().parse(in);
				else
					result = gson.fromJson(in, type);
				break;
			}
			case "error": {
				error = gson.fromJson(in, ResponseError.class);
				break;
			}
			default:
				in.skipValue();
			}
		}
		in.endObject();
		Object params = parseParams(rawParams, method);
		return createMessage(jsonrpc, id, method, params, result, error);
	}
	
	protected Object parseParams(JsonReader in, String method) throws IOException {
		JsonToken next = in.peek();
		if (next == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		Type[] parameterTypes = getParameterTypes(method);
		if (parameterTypes.length == 1) {
			return fromJson(in, parameterTypes[0]);
		}
		if (parameterTypes.length > 1 && next == JsonToken.BEGIN_ARRAY) {
			List<Object> parameters = new ArrayList<Object>(parameterTypes.length);
			int index = 0;
			in.beginArray();
			while (in.hasNext()) {
				Type parameterType = index < parameterTypes.length ? parameterTypes[index] : null;
				Object parameter = fromJson(in, parameterType);
				parameters.add(parameter);
				index++;
			}
			in.endArray();
			while (index < parameterTypes.length) {
				parameters.add(null);
				index++;
			}
			return parameters;
		}
		return new JsonParser().parse(in);
	}

	protected Object parseParams(Object params, String method) {
		if (isNull(params)) {
			return null;
		}
		if (!(params instanceof JsonElement)) {
			return params;
		}
		JsonElement rawParams = (JsonElement) params;
		Type[] parameterTypes = getParameterTypes(method);
		if (parameterTypes.length == 1) {
			return fromJson(rawParams, parameterTypes[0]);
		}
		if (parameterTypes.length > 1 && rawParams instanceof JsonArray) {
			JsonArray array = (JsonArray) rawParams;
			List<Object> parameters = new ArrayList<Object>(Math.max(array.size(), parameterTypes.length));
			int index = 0;
			Iterator<JsonElement> iterator = array.iterator();
			while (iterator.hasNext()) {
				Type parameterType = index < parameterTypes.length ? parameterTypes[index] : null;  
				Object parameter = fromJson(iterator.next(), parameterType);
				parameters.add(parameter);
				index++;
			}
			while (index < parameterTypes.length) {
				parameters.add(null);
				index++;
			}
			return parameters;
		}
		return rawParams;
	}

	protected Object fromJson(JsonReader in, Type type) {
		if (isNullOrVoidType(type)) {
			return new JsonParser().parse(in);
		}
		return gson.fromJson(in, type);
	}
	
	protected Object fromJson(JsonElement element, Type type) {
		if (isNull(element)) {
			return null;
		}
		if (isNullOrVoidType(type)) {
			return element;
		}
		Object value = gson.fromJson(element, type);
		if (isNull(value)) {
			return null;
		}
		return value;
	}

	protected boolean isNull(Object value) {
		return value == null || value instanceof JsonNull;
	}
	
	protected boolean isNullOrVoidType(Type type) {
		return type == null || Void.class == type;
	}

	protected Type[] getParameterTypes(String method) {
		if (method != null) {
			JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(method);
			if (jsonRpcMethod != null)
				return jsonRpcMethod.getParameterTypes();
		}
		return EMPTY_TYPE_ARRAY;
	}
	
	protected Message createMessage(String jsonrpc, String id, String method, Object params, Object result, ResponseError error) {
		if (id != null && method != null) {
			RequestMessage message = new RequestMessage();
			message.setJsonrpc(jsonrpc);
			message.setId(id);
			message.setMethod(method);
			message.setParams(params);
			return message;
		} else if (id != null) {
			ResponseMessage message = new ResponseMessage();
			message.setJsonrpc(jsonrpc);
			message.setId(id);
			if (error != null) {
				message.setError(error);
			} else {
				if (result instanceof JsonElement) {
					// Type of result could not be resolved - try again with the parsed JSON tree
					MethodProvider methodProvider = handler.getMethodProvider();
					if (methodProvider != null) {
						String resolvedMethod = methodProvider.resolveMethod(id);
						if (resolvedMethod != null) {
							JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(resolvedMethod);
							if (jsonRpcMethod != null)
								result = gson.fromJson((JsonElement) result, jsonRpcMethod.getReturnType());
						}
					}
				}
				message.setResult(result);
			}
			return message;
		} else if (method != null) {
			NotificationMessage message = new NotificationMessage();
			message.setJsonrpc(jsonrpc);
			message.setMethod(method);
			message.setParams(params);
			return message;
		} else {
			throw new JsonParseException("Unable to identify the input message.");
		}
	}

	@Override
	public void write(JsonWriter out, Message message) throws IOException {
		out.beginObject();
		out.name("jsonrpc");
		out.value(message.getJsonrpc() == null ? MessageConstants.JSONRPC_VERSION : message.getJsonrpc());
		
		if (message instanceof RequestMessage) {
			RequestMessage requestMessage = (RequestMessage) message;
			out.name("id");
			out.value(requestMessage.getId());
			out.name("method");
			out.value(requestMessage.getMethod());
			out.name("params");
			Object params = requestMessage.getParams();
			if (params == null)
				out.nullValue();
			else
				gson.toJson(params, params.getClass(), out);
		} else if (message instanceof ResponseMessage) {
			ResponseMessage responseMessage = (ResponseMessage) message;
			out.name("id");
			out.value(responseMessage.getId());
			if (responseMessage.getError() != null) {
				out.name("error");
				gson.toJson(responseMessage.getError(), ResponseError.class, out);
			} else {
				out.name("result");
				Object result = responseMessage.getResult();
				if (result == null)
					out.nullValue();
				else
					gson.toJson(result, result.getClass(), out);
			}
		} else if (message instanceof NotificationMessage) {
			NotificationMessage notificationMessage = (NotificationMessage) message;
			out.name("method");
			out.value(notificationMessage.getMethod());
			out.name("params");
			Object params = notificationMessage.getParams();
			if (params == null)
				out.nullValue();
			else
				gson.toJson(params, params.getClass(), out);
		}
		
		out.endObject();
	}
	
}
