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

package com.github.pjfanning.pekkohttpjackson3;

import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.scaladsl.model.ExceptionWithErrorInfo;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.util.ByteString;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.core.util.RecyclerPool;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class Jackson {
    private static final String CONFIG_ROOT = "pekko.http.marshallers.jackson";

    private static final ObjectMapper defaultObjectMapper = createMapper(
            ConfigFactory.load().getConfig(CONFIG_ROOT),
            List.of(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));

    /** INTERNAL API */
    public static class JacksonUnmarshallingException extends ExceptionWithErrorInfo {
        public JacksonUnmarshallingException(Class<?> expectedType, Exception cause) {
            super(
                    new ErrorInfo(
                            "Cannot unmarshal JSON as " + expectedType.getSimpleName(), cause.getMessage()),
                    cause);
        }
    }

    public static <T> Marshaller<T, RequestEntity> marshaller() {
        return marshaller(defaultObjectMapper);
    }

    public static <T> Marshaller<T, RequestEntity> marshaller(ObjectMapper mapper) {
        return Marshaller.wrapEntity(
                u -> toJSON(mapper, u), Marshaller.stringToEntity(), MediaTypes.APPLICATION_JSON);
    }

    public static <T> Unmarshaller<ByteString, T> byteStringUnmarshaller(Class<T> expectedType) {
        return byteStringUnmarshaller(defaultObjectMapper, expectedType);
    }

    public static <T> Unmarshaller<HttpEntity, T> unmarshaller(Class<T> expectedType) {
        return unmarshaller(defaultObjectMapper, expectedType);
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
        } catch (Exception e) {
            throw new JacksonUnmarshallingException(expectedType, e);
        }
    }

    static ObjectMapper createMapper(final Config config, final List<MapperFeature> featureList) {
        StreamReadConstraints streamReadConstraints =
                StreamReadConstraints.builder()
                        .maxNestingDepth(config.getInt("read.max-nesting-depth"))
                        .maxNumberLength(config.getInt("read.max-number-length"))
                        .maxStringLength(config.getInt("read.max-string-length"))
                        .maxNameLength(config.getInt("read.max-name-length"))
                        .maxDocumentLength(config.getLong("read.max-document-length"))
                        .maxTokenCount(config.getLong("read.max-token-count"))
                        .build();
        StreamWriteConstraints streamWriteConstraints =
                StreamWriteConstraints.builder()
                        .maxNestingDepth(config.getInt("write.max-nesting-depth"))
                        .build();
        final JsonFactoryBuilder jsonFactoryBuilder =
                JsonFactory.builder()
                        .streamReadConstraints(streamReadConstraints)
                        .streamWriteConstraints(streamWriteConstraints)
                        .recyclerPool(getBufferRecyclerPool(config));
        final JsonMapper.Builder builder = JsonMapper.builder(jsonFactoryBuilder.build());
        for (MapperFeature feature : featureList) {
            builder.enable(feature);
        }
        return builder.build();
    }

    private static RecyclerPool<BufferRecycler> getBufferRecyclerPool(final Config cfg) {
        final String poolType = cfg.getString("buffer-recycler.pool-instance");
        switch (poolType) {
            case "thread-local":
                return JsonRecyclerPools.threadLocalPool();
            case "concurrent-deque":
                return JsonRecyclerPools.newConcurrentDequePool();
            case "shared-concurrent-deque":
                return JsonRecyclerPools.sharedConcurrentDequePool();
            case "bounded":
                return JsonRecyclerPools.newBoundedPool(cfg.getInt("buffer-recycler.bounded-pool-size"));
            case "non-recycling":
                return JsonRecyclerPools.nonRecyclingPool();
            default:
                throw new IllegalArgumentException("Unknown recycler-pool: " + poolType);
        }
    }

    private Jackson() {}
}
