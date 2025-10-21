/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package com.github.pjfanning.pekkohttpjackson;

import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.ExceptionWithErrorInfo;
import org.apache.pekko.util.ByteString;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class Jackson {

    public static class JacksonUnmarshallingException extends ExceptionWithErrorInfo {
        public JacksonUnmarshallingException(Class<?> expectedType, IOException cause) {
            super(
                    new ErrorInfo(
                            "Cannot unmarshal JSON as " + expectedType.getSimpleName(), cause.getMessage()),
                    cause);
        }
    }

    public static <T> Marshaller<T, RequestEntity> marshaller(ObjectMapper mapper) {
        return Marshaller.wrapEntity(
                u -> toJSON(mapper, u), Marshaller.stringToEntity(), MediaTypes.APPLICATION_JSON);
    }

    public static <T> Unmarshaller<HttpEntity, T> unmarshaller(
            ObjectMapper mapper, Class<T> expectedType) {
        return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
                .thenApply(s -> fromJSON(mapper, s, expectedType));
    }

    public static <T> Unmarshaller<ByteString, T> byteStringUnmarshaller(
            ObjectMapper mapper, Class<T> expectedType) {
        return Unmarshaller.sync(s -> fromJSON(mapper, s.utf8String(), expectedType));
    }

    private static String toJSON(ObjectMapper mapper, Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot marshal to JSON: " + object, e);
        }
    }

    private static <T> T fromJSON(ObjectMapper mapper, String json, Class<T> expectedType) {
        try {
            return mapper.readerFor(expectedType).readValue(json);
        } catch (IOException e) {
            throw new JacksonUnmarshallingException(expectedType, e);
        }
    }

    private Jackson() {}
}
