package com.ldang03.common.trace;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;


@Component
public class TraceSupport {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final Tracer tracer;
    private final Propagator propagator;
    private final ObjectMapper objectMapper;

    public TraceSupport(Tracer tracer, Propagator propagator, ObjectMapper objectMapper) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.objectMapper = objectMapper;
    }

    public String capture() {
        TraceContext current = tracer.currentTraceContext().context();
        if (current == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(current, carrier, Map::put);
        try {
            return objectMapper.writeValueAsString(carrier);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void runIn(String saved, String spanName, Runnable task) {
        callIn(saved, spanName, () -> {
            task.run();
            return null;
        });
    }

    public <T> T callIn(String saved, String spanName, Supplier<T> task) {
        Span span = propagator.extract(read(saved), Map<String, String>::get).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return task.get();
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private Map<String, String> read(String saved) {
        if (saved == null || saved.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(saved, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
