/*
 * Copyright 2004-present Facebook. All Rights Reserved.
 */
package com.facebook.swift.codec.internal.builtin;

import com.facebook.swift.codec.ThriftCodec;
import com.facebook.swift.codec.internal.TProtocolReader;
import com.facebook.swift.codec.internal.TProtocolWriter;
import com.facebook.swift.codec.metadata.ThriftType;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class IntegerThriftCodec implements ThriftCodec<Integer> {
  @Override
  public ThriftType getType() {
    return ThriftType.I32;
  }

  @Override
  public Integer read(TProtocolReader protocol) throws Exception {
    Preconditions.checkNotNull(protocol, "protocol is null");
    return protocol.readI32();
  }

  @Override
  public void write(Integer value, TProtocolWriter protocol) throws Exception {
    Preconditions.checkNotNull(value, "value is null");
    Preconditions.checkNotNull(protocol, "protocol is null");
    protocol.writeI32(value);
  }
}
