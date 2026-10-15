//package com.ldang03.common.kafka.interceptor;
//
//import com.ldang03.common.http.CorrelationIdInterceptor;
//import com.ldang03.common.http.HttpHeaderUtil;
//import org.apache.kafka.clients.producer.ProducerInterceptor;
//import org.apache.kafka.clients.producer.ProducerRecord;
//import org.apache.kafka.clients.producer.RecordMetadata;
//import org.slf4j.MDC;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Map;
//
//public class CorrelationIdProducerInterceptor implements ProducerInterceptor<String, Object> {
//
//    @Override
//    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
//        String correlationId = MDC.get(CorrelationIdInterceptor.MDC_KEY);
//        if (correlationId == null || correlationId.isBlank()) {
//            correlationId = HttpHeaderUtil.newCorrelationId();
//        }
//
//        record.headers().add(
//                HttpHeaderUtil.CORRELATION_ID_HEADER,
//                correlationId.getBytes(StandardCharsets.UTF_8));
//
//        return record;
//    }
//
//    @Override
//    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
//        // no-op
//    }
//
//    @Override
//    public void close() {
//        // no-op
//    }
//
//    @Override
//    public void configure(Map<String, ?> configs) {
//        // no-op
//    }
//}
