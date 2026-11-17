package jp.osscons.opensourcecobol.libcobj.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.osscons.opensourcecobol.libcobj.call.CobolResolve;
import jp.osscons.opensourcecobol.libcobj.common.CobolRunUnit;
import jp.osscons.opensourcecobol.libcobj.exceptions.CobolStopRunException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CobolTelemetryTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
    }

    @AfterEach
    void tearDown() {
        CobolRunUnit.resetThreadState();
        CobolTelemetry.setTracer(null, CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        provider.close();
    }

    private void enable(int fileTraceLevel) {
        CobolTelemetry.setTracer(
                new OtelTracer(provider.get(OtelTracer.INSTRUMENTATION_NAME)), fileTraceLevel);
    }

    private SpanData find(String name) {
        for (SpanData s : exporter.getFinishedSpanItems()) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        fail("span not found: " + name);
        return null;
    }

    @Test
    void disabledByDefaultCreatesNoSpans() {
        CobolTelemetry.setTracer(null, CobolTelemetry.FILE_TRACE_ALL);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        assertNull(CobolTelemetry.fileStart("OPEN", false, "F1"), "no file span when disabled");
        assertTrue(exporter.getFinishedSpanItems().isEmpty(), "no spans when disabled");
    }

    @Test
    void nestedCallsBecomeParentAndChild() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.pushCallStackList("SUBPROG");
        CobolResolve.popCallStackList();
        CobolResolve.popCallStackList();

        SpanData main = find("MAINPROG");
        SpanData sub = find("SUBPROG");
        assertEquals(main.getSpanContext().getSpanId(), sub.getParentSpanId(), "SUB is child");
        assertEquals(main.getTraceId(), sub.getTraceId(), "same trace");
        assertEquals("SUBPROG", sub.getAttributes().get(OtelTracer.PROGRAM_ID), "program id");
        assertFalse(Span.current().getSpanContext().isValid(), "context is restored");
    }

    @Test
    void stopRunIsRecordedOnTheProgramSpan() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolTelemetry.stopRun(4);
        CobolResolve.popCallStackList();

        SpanData main = find("MAINPROG");
        assertEquals(Boolean.TRUE, main.getAttributes().get(OtelTracer.STOP_RUN), "stop run");
        assertEquals(4L, main.getAttributes().get(OtelTracer.RETURN_CODE), "return code");
    }

    @Test
    void handleAtFrameAtRootEndsTheSpan() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        int rc = CobolStopRunException.handleAtFrame(8);
        assertEquals(8, rc, "return code");
        assertEquals(8L, find("MAINPROG").getAttributes().get(OtelTracer.RETURN_CODE), "rc");
    }

    @Test
    void unfinishedSpansAreEndedAtRunUnitEnd() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolRunUnit.resetThreadState();

        SpanData main = find("MAINPROG");
        assertEquals(StatusCode.ERROR, main.getStatus().getStatusCode(), "orphan is error");
    }

    @Test
    void fileTraceLevelControlsRecordLevelSpans() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        assertNull(CobolTelemetry.fileStart("READ", true, "F1"), "READ is not traced");
        Object open = CobolTelemetry.fileStart("OPEN", false, "F1");
        assertNotNull(open, "OPEN is traced");
        CobolTelemetry.fileEnd(open, "00".getBytes(StandardCharsets.US_ASCII), null);

        enable(CobolTelemetry.FILE_TRACE_NONE);
        assertNull(CobolTelemetry.fileStart("OPEN", false, "F1"), "nothing is traced");

        enable(CobolTelemetry.FILE_TRACE_ALL);
        Object read = CobolTelemetry.fileStart("READ", true, "F1");
        assertNotNull(read, "READ is traced");
        CobolTelemetry.fileEnd(read, "10".getBytes(StandardCharsets.US_ASCII), null);

        SpanData openSpan = find("OPEN F1");
        assertEquals("00", openSpan.getAttributes().get(OtelTracer.FILE_STATUS), "status");
        assertEquals("F1", openSpan.getAttributes().get(OtelTracer.FILE_NAME), "file name");
        SpanData readSpan = find("READ F1");
        assertEquals(StatusCode.UNSET, readSpan.getStatus().getStatusCode(), "EOF is not error");
    }

    @Test
    void permanentFileErrorsMarkTheSpanAsError() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        Object open = CobolTelemetry.fileStart("OPEN", false, "F1");
        CobolTelemetry.fileEnd(open, "35".getBytes(StandardCharsets.US_ASCII), null);
        assertEquals(StatusCode.ERROR, find("OPEN F1").getStatus().getStatusCode(), "error");
    }

    @Test
    void traceparentIsExtracted() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        Context ctx = OtelTracer.extract(carrier);
        assertNotNull(ctx, "valid traceparent");
        assertEquals(
                "0af7651916cd43dd8448eb211c80319c",
                Span.fromContext(ctx).getSpanContext().getTraceId(),
                "trace id");

        carrier.put("traceparent", "invalid");
        assertNull(OtelTracer.extract(carrier), "invalid traceparent is ignored");
    }

    @Test
    void configureParsesEnvironmentValues() {
        CobolTelemetry.configure("N", "all");
        assertFalse(CobolTelemetry.isEnabled(), "disabled");
        CobolTelemetry.configure("Y", "none");
        assertTrue(CobolTelemetry.isEnabled(), "enabled");
        assertNull(CobolTelemetry.fileStart("OPEN", false, "F1"), "file trace none");
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertTrue(spans.isEmpty(), "test exporter is not the global one");
    }
}
