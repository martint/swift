/*
 * Copyright 2004-present Facebook. All Rights Reserved.
 */
package com.facebook.swift.codec.internal.reflection;

import com.facebook.swift.codec.ThriftCodec;
import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.codec.internal.TProtocolReader;
import com.facebook.swift.codec.internal.TProtocolWriter;
import com.facebook.swift.codec.metadata.ThriftConstructorInjection;
import com.facebook.swift.codec.metadata.ThriftExtraction;
import com.facebook.swift.codec.metadata.ThriftFieldExtractor;
import com.facebook.swift.codec.metadata.ThriftFieldInjection;
import com.facebook.swift.codec.metadata.ThriftFieldMetadata;
import com.facebook.swift.codec.metadata.ThriftInjection;
import com.facebook.swift.codec.metadata.ThriftMethodExtractor;
import com.facebook.swift.codec.metadata.ThriftMethodInjection;
import com.facebook.swift.codec.metadata.ThriftParameterInjection;
import com.facebook.swift.codec.metadata.ThriftStructMetadata;
import com.facebook.swift.codec.metadata.ThriftType;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;

import javax.annotation.concurrent.Immutable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

@Immutable
public class ReflectionThriftCodec<T> implements ThriftCodec<T> {
  private final ThriftStructMetadata<T> metadata;
  private final SortedMap<Short, ThriftCodec<?>> fields;

  public ReflectionThriftCodec(
      ThriftCodecManager manager,
      ThriftStructMetadata<T> metadata
  ) {
    this.metadata = metadata;
    ImmutableSortedMap.Builder<Short, ThriftCodec<?>> fields = ImmutableSortedMap.naturalOrder();
    for (ThriftFieldMetadata fieldMetadata : metadata.getFields()) {
      fields.put(fieldMetadata.getId(), manager.getCodec(fieldMetadata.getType()));
    }
    this.fields = fields.build();
  }

  @Override
  public ThriftType getType() {
    return ThriftType.struct(metadata);
  }

  @Override
  public T read(TProtocolReader protocol) throws Exception {
    protocol.readStructBegin();

    Map<Short, Object> data = new HashMap<>(metadata.getFields().size());
    while (protocol.nextField()) {
      short fieldId = protocol.getFieldId();

      // do we have a codec for this field
      ThriftCodec<?> codec = fields.get(fieldId);
      if (codec == null) {
        protocol.skipFieldData();
        continue;
      }

      // is this field readable
      ThriftFieldMetadata field = metadata.getField(fieldId);
      if (field.isWriteOnly()) {
        protocol.skipFieldData();
        continue;
      }

      // read the value
      Object value = protocol.readField(codec);
      if (value == null) {
        continue;
      }

      data.put(fieldId, value);
    }
    protocol.readStructEnd();

    // build the struct
    return constructStruct(data);
  }

  @Override
  public void write(T instance, TProtocolWriter protocol) throws Exception {
    protocol.writeStructBegin(metadata.getStructName());
    for (ThriftFieldMetadata fieldMetadata : metadata.getFields()) {
      // is the field writable?
      if (fieldMetadata.isReadOnly()) {
        continue;
      }

      // get the field value
      Object fieldValue = getFieldValue(instance, fieldMetadata);

      // write the field
      if (fieldValue != null) {
        ThriftCodec<Object> codec = (ThriftCodec<Object>) fields.get(fieldMetadata.getId());
        protocol.writeField(fieldMetadata.getName(), fieldMetadata.getId(), codec, fieldValue);
      }
    }
    protocol.writeStructEnd();
  }

  private T constructStruct(Map<Short, Object> data)
      throws Exception {

    // construct instance
    Object instance;
    {
      ThriftConstructorInjection constructor = metadata.getConstructor();
      Object[] parametersValues = new Object[constructor.getParameters().size()];
      for (ThriftParameterInjection parameter : constructor.getParameters()) {
        Object value = data.get(parameter.getId());
        parametersValues[parameter.getParameterIndex()] = value;
      }

      try {
        instance = constructor.getConstructor().newInstance(parametersValues);
      } catch (InvocationTargetException e) {
        if (e.getTargetException() != null) {
          Throwables.propagateIfInstanceOf(e.getTargetException(), Exception.class);
        }
        throw e;
      }
    }

    // inject fields
    for (ThriftFieldMetadata fieldMetadata : metadata.getFields()) {
      for (ThriftInjection injection : fieldMetadata.getInjections()) {
        if (injection instanceof ThriftFieldInjection) {
          ThriftFieldInjection fieldInjection = (ThriftFieldInjection) injection;
          Object value = data.get(fieldInjection.getId());
          if (value != null) {
            fieldInjection.getField().set(instance, value);
          }
        }
      }
    }

    // inject methods
    for (ThriftMethodInjection methodInjection : metadata.getMethodInjections()) {
      boolean shouldInvoke = false;
      Object[] parametersValues = new Object[methodInjection.getParameters().size()];
      for (ThriftParameterInjection parameter : methodInjection.getParameters()) {
        Object value = data.get(parameter.getId());
        if (value != null) {
          parametersValues[parameter.getParameterIndex()] = value;
          shouldInvoke = true;
        }
      }

      if (shouldInvoke) {
        try {
          methodInjection.getMethod().invoke(instance, parametersValues);
        } catch (InvocationTargetException e) {
          if (e.getTargetException() != null) {
            Throwables.propagateIfInstanceOf(e.getTargetException(), Exception.class);
          }
          throw e;
        }
      }
    }

    // builder method
    if (metadata.getBuilderMethod() != null) {
      ThriftMethodInjection builderMethod = metadata.getBuilderMethod();
      Object[] parametersValues = new Object[builderMethod.getParameters().size()];
      for (ThriftParameterInjection parameter : builderMethod.getParameters()) {
        Object value = data.get(parameter.getId());
        parametersValues[parameter.getParameterIndex()] = value;
      }

      try {
        instance = builderMethod.getMethod().invoke(instance, parametersValues);
        if (instance == null) {
          throw new IllegalArgumentException("Builder method returned a null instance");

        }
        if (!metadata.getStructClass().isInstance(instance)) {
          throw new IllegalArgumentException(
              String.format(
                  "Builder method returned instance of type %s, but an instance of %s is required",
                  instance.getClass().getName(),
                  metadata.getStructClass().getName()
              )
          );
        }
      } catch (InvocationTargetException e) {
        if (e.getTargetException() != null) {
          Throwables.propagateIfInstanceOf(e.getTargetException(), Exception.class);
        }
        throw e;
      }
    }

    return (T) instance;
  }

  private Object getFieldValue(Object instance, ThriftFieldMetadata field)
      throws Exception {
    try {
      ThriftExtraction extraction = field.getExtraction();
      Object value;
      if (extraction instanceof ThriftFieldExtractor) {
        ThriftFieldExtractor thriftFieldExtractor = (ThriftFieldExtractor) extraction;
        value = thriftFieldExtractor.getField().get(instance);
      } else if (extraction instanceof ThriftMethodExtractor) {
        ThriftMethodExtractor thriftMethodExtractor = (ThriftMethodExtractor) extraction;
        value = thriftMethodExtractor.getMethod().invoke(instance);
      } else {
        throw new IllegalAccessException(
            "Unsupported field extractor type " + extraction.getClass()
                .getName()
        );
      }

      return value;
    } catch (InvocationTargetException e) {
      if (e.getTargetException() != null) {
        Throwables.propagateIfInstanceOf(e.getTargetException(), Exception.class);
      }
      throw e;
    }
  }
}
