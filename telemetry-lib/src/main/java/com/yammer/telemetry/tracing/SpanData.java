package com.yammer.telemetry.tracing;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public interface SpanData {
    BigInteger getTraceId();

    BigInteger getSpanId();

    Optional<BigInteger> getParentSpanId();

    String getName();

    String getHost();

    long getStartTime();

    long getDuration();

    List<AnnotationData> getAnnotations();
}
