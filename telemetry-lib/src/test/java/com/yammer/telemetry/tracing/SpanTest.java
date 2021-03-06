package com.yammer.telemetry.tracing;

import com.google.common.base.Optional;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.EmptyStackException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpanTest {
    @Rule
    public SpanContextRule spanContextRule = new SpanContextRule();


    @After
    public void clearSpanSinkRegistry() {
        SpanSinkRegistry.clear();
    }

    @Test
    public void testRootSpan() {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);

        final Span span = SpanHelper.startTrace("testSpan");
        span.end();

        verify(sink).record(span);

        assertNotNull(span.getSpanId());
        assertEquals(Optional.<BigInteger>absent(), span.getParentSpanId());
        assertEquals("testSpan", span.getName());
        assertTrue(span.getDuration() >= 0);
    }

    @Test
    public void testRootlessSpans() {
        final Span outer = SpanHelper.startTrace("outerSpan");
        final Span inner = SpanHelper.startSpan("innerSpan");
        inner.end();
        outer.end();
    }

    @Test
    public void testNestedSpan() {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);

        final Span outer = SpanHelper.startTrace("outerSpan");
        final Span inner = SpanHelper.startSpan("innerSpan");

        inner.end();
        verify(sink).record(inner);

        outer.end();
        verify(sink).record(outer);

        assertEquals(Optional.of(outer.getSpanId()), inner.getParentSpanId());
    }

    @Test
    public void testMultipleSinks() {
        final SpanSink first = mock(SpanSink.class);
        final SpanSink second = mock(SpanSink.class);
        SpanSinkRegistry.register(first);
        SpanSinkRegistry.register(second);

        final Span span = SpanHelper.startTrace("testSpan");
        span.end();

        verify(first).record(span);
        verify(second).record(span);
    }

    @Test
    public void testMultipleThreads() throws InterruptedException {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);
        final CyclicBarrier inside = new CyclicBarrier(2);

        final Thread one = new Thread(new BarrierSpanRunner("threadOne", inside));
        final Thread two = new Thread(new BarrierSpanRunner("threadTwo", inside));

        one.start();
        two.start();

        one.join();
        two.join();

        final ArgumentCaptor<Span> captor = ArgumentCaptor.forClass(Span.class);
        verify(sink, times(2)).record(captor.capture());

        final List<Span> spans = captor.getAllValues();
        assertEquals(2, spans.size());
        assertEquals(Optional.<BigInteger>absent(), spans.get(0).getParentSpanId());
        assertEquals(Optional.<BigInteger>absent(), spans.get(1).getParentSpanId());
    }

    @Test
    public void testSpansClosedInIncorrectOrderClearsContextButDoesNotLogUnclosedSpans() {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);

        Span trace = SpanHelper.startTrace("The Trace");

        // These are deliberately not closed
        SpanHelper.startSpan("one");
        SpanHelper.startSpan("two");

        trace.end();

        assertTrue(SpanHelper.captureSpans().isEmpty());

        verify(sink).record(trace);
        verifyZeroInteractions(sink);
    }

    @Test(expected = EmptyStackException.class)
    public void testEndingASpanMoreThanOnce() {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);

        Span trace = SpanHelper.startTrace("The Trace");

        trace.end();
        assertTrue(SpanHelper.captureSpans().isEmpty());
        verify(sink).record(trace);

        trace.end();
    }

    @Test(expected = IllegalStateException.class)
    public void testEndingASpanFromInvalidThreadContext() throws Throwable {
        final SpanSink sink = mock(SpanSink.class);
        SpanSinkRegistry.register(sink);

        try (final Span trace = SpanHelper.startTrace("The Trace")) {

            final CountDownLatch successLatch = new CountDownLatch(1);
            final ArrayBlockingQueue<Throwable> expectedException = new ArrayBlockingQueue<>(1);

            // contrived to allow ending a span in a different thread where the spanContext is unavailable.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        trace.end();
                        successLatch.countDown();
                    } catch (Throwable t) {
                        expectedException.offer(t.fillInStackTrace());
                    }
                }
            }).start();

            //noinspection ThrowableResultOfMethodCallIgnored
            throw expectedException.poll(100, TimeUnit.MILLISECONDS);
        }
    }

    private class BarrierSpanRunner implements Runnable {
        private final String spanName;
        private final CyclicBarrier barrier;

        public BarrierSpanRunner(String spanName, CyclicBarrier barrier) {
            this.spanName = spanName;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try (Span ignored = SpanHelper.startTrace(spanName)) {
                barrier.await();
            } catch (Exception e) {
                throw new RuntimeException("Problem tracing a span", e);
            }
        }
    }
}
