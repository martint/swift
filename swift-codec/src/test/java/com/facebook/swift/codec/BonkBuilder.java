/*
 * Copyright 2004-present Facebook. All Rights Reserved.
 */
package com.facebook.swift.codec;

import com.facebook.swift.codec.BonkBuilder.Builder;

import javax.annotation.concurrent.Immutable;

@Immutable
@ThriftStruct(value = "Bonk", builder = Builder.class)
public class BonkBuilder {
  private final String message;
  private final int type;

  public BonkBuilder(
      String message,
      int type
  ) {
    this.message = message;
    this.type = type;
  }

  @ThriftField(1)
  public String getMessage() {
    return message;
  }

  @ThriftField(2)
  public int getType() {
    return type;
  }

  @Override
  public int hashCode() {
    int result = message != null ? message.hashCode() : 0;
    result = 31 * result + type;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BonkBuilder that = (BonkBuilder) o;

    if (type != that.type) {
      return false;
    }
    if (message != null ? !message.equals(that.message) : that.message != null) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("BonkBuilder");
    sb.append("{message='").append(message).append('\'');
    sb.append(", type=").append(type);
    sb.append('}');
    return sb.toString();
  }

  public static class Builder {
    private String message;
    private int type;

    @ThriftField
    public Builder setMessage(String message) {
      this.message = message;
      return this;
    }

    @ThriftField
    public Builder setType(int type) {
      this.type = type;
      return this;
    }

    @ThriftConstructor
    public BonkBuilder create() {
      return new BonkBuilder(message, type);
    }
  }
}
