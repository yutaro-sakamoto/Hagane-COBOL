package jp.osscons.opensourcecobol.libcobj.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jp.osscons.opensourcecobol.libcobj.call.CobolResolve;
import jp.osscons.opensourcecobol.libcobj.common.CobolModule;
import jp.osscons.opensourcecobol.libcobj.common.CobolRunUnit;
import jp.osscons.opensourcecobol.libcobj.common.CobolUtil;
import jp.osscons.opensourcecobol.libcobj.exceptions.CobolStopRunException;
import jp.osscons.opensourcecobol.libcobj.exceptions.CobolStopRunSignal;
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
        enable(fileTraceLevel, null);
    }

    private void enable(int fileTraceLevel, Context parent) {
        CobolTelemetry.setTracer(
                new OtelTracer(provider.get(OtelTracer.INSTRUMENTATION_NAME), parent),
                fileTraceLevel);
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
        assertTrue(exporter.getFinishedSpanItems().isEmpty(), "test exporter is not global");
    }

    private static CobolModule newModule() {
        return new CobolModule(null, null, null, null, 0, '.', '$', ',', 1, 1, 1, 0, null);
    }

    @Test
    void stopRunInSubprogramUnwindsBothSpans() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolModule.push(newModule());
        CobolResolve.pushCallStackList("MAINPROG");
        CobolModule.push(newModule());
        CobolResolve.pushCallStackList("SUBPROG");
        assertThrows(
                CobolStopRunSignal.class,
                () -> CobolStopRunException.handleAtFrame(3),
                "sub frame rethrows to the caller");
        assertEquals(3, CobolStopRunException.handleAtFrame(3), "root returns the code");

        SpanData main = find("MAINPROG");
        SpanData sub = find("SUBPROG");
        assertEquals(main.getSpanContext().getSpanId(), sub.getParentSpanId(), "SUB is child");
        assertEquals(Boolean.TRUE, sub.getAttributes().get(OtelTracer.STOP_RUN), "sub stopped");
        assertEquals(3L, sub.getAttributes().get(OtelTracer.RETURN_CODE), "sub return code");
        assertNull(main.getAttributes().get(OtelTracer.STOP_RUN), "only the program that stopped");
        assertEquals(3L, main.getAttributes().get(OtelTracer.RETURN_CODE), "root exit code");
        assertEquals(StatusCode.UNSET, main.getStatus().getStatusCode(), "not an orphan");
    }

    @Test
    void programSpansDoNotLeakIntoTheThreadContext() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        Context before = Context.current();
        CobolResolve.pushCallStackList("MAINPROG");
        assertSame(before, Context.current(), "context is untouched while running");
        try (CobolTelemetry.Activation ignored = CobolTelemetry.activate()) {
            assertTrue(Span.current().getSpanContext().isValid(), "active during ESQL");
        }
        assertSame(before, Context.current(), "context is restored after activation");
        CobolResolve.popCallStackList();
        assertSame(before, Context.current(), "context is untouched after exit");
    }

    @Test
    void hostSpanIsTheParentAndWinsOverTraceparent() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE, OtelTracer.extract(carrier));

        Span host = provider.get("host").spanBuilder("host").startSpan();
        try (Scope ignored = host.makeCurrent()) {
            CobolResolve.pushCallStackList("MAINPROG");
            CobolResolve.popCallStackList();
        }
        host.end();
        assertEquals(
                host.getSpanContext().getSpanId(),
                find("MAINPROG").getParentSpanId(),
                "host span is the parent");

        exporter.reset();
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        assertEquals("b7ad6b7169203331", find("MAINPROG").getParentSpanId(), "TRACEPARENT");
    }

    @Test
    void implicitCloseAfterTheRootReturnedStaysInTheTrace() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        Object close = CobolTelemetry.fileStart("CLOSE", false, "F1");
        CobolTelemetry.fileEnd(close, "00".getBytes(StandardCharsets.US_ASCII), null);

        SpanData main = find("MAINPROG");
        assertEquals(
                main.getSpanContext().getSpanId(),
                find("CLOSE F1").getParentSpanId(),
                "CLOSE is a child of the root program");
    }

    @Test
    void threadsHaveIndependentStacks() throws Exception {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f =
                    pool.submit(
                            () -> {
                                CobolResolve.pushCallStackList("OTHER");
                                CobolResolve.popCallStackList();
                            });
            f.get();
        } finally {
            pool.shutdown();
        }
        CobolResolve.popCallStackList();
        assertNotEquals(
                find("MAINPROG").getTraceId(), find("OTHER").getTraceId(), "separate traces");
    }

    @Test
    void globalOpenTelemetryIsNotLockedToNoop() {
        GlobalOpenTelemetry.resetForTest();
        CobolTelemetry.setTracer(new OtelTracer(null, null), CobolTelemetry.FILE_TRACE_ALL);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        assertFalse(GlobalOpenTelemetry.isSet(), "host can still register its SDK");
    }

    @Test
    void runtimeErrorMarksTheRunningProgramsAsFailed() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.pushCallStackList("SUBPROG");
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true));
        try {
            CobolUtil.runtimeError("Detected division by zero.");
        } finally {
            System.setErr(err);
        }
        // runtimeErrorの後はstopRunAndThrow(1)からhandleAtFrameで巻き戻る
        CobolTelemetry.stopRun(1);
        CobolResolve.popCallStackList();
        CobolTelemetry.stopRun(1);
        CobolResolve.popCallStackList();
        assertNull(find("SUBPROG").getAttributes().get(OtelTracer.STOP_RUN), "not a STOP RUN");
        assertEquals(1L, find("MAINPROG").getAttributes().get(OtelTracer.RETURN_CODE), "exit code");

        SpanData sub = find("SUBPROG");
        assertEquals(StatusCode.ERROR, sub.getStatus().getStatusCode(), "sub failed");
        assertEquals(StatusCode.ERROR, find("MAINPROG").getStatus().getStatusCode(), "main too");
        EventData event = sub.getEvents().get(0);
        assertEquals(OtelTracer.RUNTIME_ERROR_EVENT, event.getName(), "event name");
        assertEquals(
                "Detected division by zero.",
                event.getAttributes().get(OtelTracer.ERROR_MESSAGE),
                "message");
    }

    @Test
    void framesLeftByAnEarlierRequestAreDiscarded() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        Span first = provider.get("host").spanBuilder("request1").startSpan();
        try (Scope ignored = first.makeCurrent()) {
            // 例外でrun_moduleを抜け、popされなかったプログラム
            CobolResolve.pushCallStackList("LEAKED");
        }
        first.end();
        Span second = provider.get("host").spanBuilder("request2").startSpan();
        try (Scope ignored = second.makeCurrent()) {
            CobolResolve.pushCallStackList("NEXTPROG");
            CobolResolve.popCallStackList();
        }
        second.end();

        assertEquals(StatusCode.ERROR, find("LEAKED").getStatus().getStatusCode(), "abandoned");
        assertEquals(
                second.getSpanContext().getSpanId(),
                find("NEXTPROG").getParentSpanId(),
                "the new request is not a child of the leaked span");
    }

    @Test
    void warnsOnceWhenNoSdkIsRegistered() {
        GlobalOpenTelemetry.resetForTest();
        CobolTelemetry.setTracer(new OtelTracer(null, null), CobolTelemetry.FILE_TRACE_ALL);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream err = System.err;
        System.setErr(new PrintStream(buf, true));
        try {
            for (int i = 0; i < 2; i++) {
                CobolResolve.pushCallStackList("MAINPROG");
                CobolResolve.popCallStackList();
            }
        } finally {
            System.setErr(err);
        }
        String out = buf.toString();
        assertEquals(
                out.indexOf(OtelTracer.NO_SDK_WARNING),
                out.lastIndexOf(OtelTracer.NO_SDK_WARNING),
                "printed once");
        assertTrue(out.contains(OtelTracer.NO_SDK_WARNING), "printed");
    }

    @Test
    void fileSpanIsCurrentDuringTheOperation() {
        enable(CobolTelemetry.FILE_TRACE_ALL);
        CobolResolve.pushCallStackList("MAINPROG");
        Context before = Context.current();
        Object read = CobolTelemetry.fileStart("READ", true, "IDXFILE");
        String currentSpanId = Span.current().getSpanContext().getSpanId();
        CobolTelemetry.fileEnd(read, "00".getBytes(StandardCharsets.US_ASCII), null);
        assertSame(before, Context.current(), "restored after the operation");
        CobolResolve.popCallStackList();
        assertEquals(
                find("READ IDXFILE").getSpanContext().getSpanId(),
                currentSpanId,
                "JDBC calls made by the operation nest under the file span");
    }

    @Test
    void closeSpanCarriesRecordCounts() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        Object close = CobolTelemetry.fileStart("CLOSE", false, "F1");
        CobolTelemetry.fileRecordCounts(close, 10, 3, 2, 1);
        CobolTelemetry.fileEnd(close, "00".getBytes(StandardCharsets.US_ASCII), null);
        Attributes attrs = find("CLOSE F1").getAttributes();
        assertEquals(10L, attrs.get(OtelTracer.RECORDS_READ), "read");
        assertEquals(3L, attrs.get(OtelTracer.RECORDS_WRITTEN), "written");
        assertEquals(2L, attrs.get(OtelTracer.RECORDS_REWRITTEN), "rewritten");
        assertEquals(1L, attrs.get(OtelTracer.RECORDS_DELETED), "deleted");
    }

    @Test
    void exceptionInAFileOperationIsRecorded() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        Object open = CobolTelemetry.fileStart("OPEN", false, "F1");
        CobolTelemetry.fileEnd(open, null, new IllegalStateException("boom"));
        SpanData span = find("OPEN F1");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), "error");
        assertEquals("exception", span.getEvents().get(0).getName(), "exception event");
    }

    @Test
    void esqlCleanupAtRunUnitEndStaysInTheTrace() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        String currentTraceId;
        try (CobolTelemetry.Activation ignored = CobolTelemetry.activate()) {
            currentTraceId = Span.current().getSpanContext().getTraceId();
        }
        assertEquals(find("MAINPROG").getTraceId(), currentTraceId, "same trace as the root");
    }

    @Test
    void tracestateIsCarriedAlong() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        carrier.put("tracestate", "vendor=value");
        Context ctx = OtelTracer.extract(carrier);
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE, ctx);
        CobolResolve.pushCallStackList("MAINPROG");
        CobolResolve.popCallStackList();
        SpanContext sc = find("MAINPROG").getSpanContext();
        assertEquals("value", sc.getTraceState().get("vendor"), "tracestate");
        assertEquals(TraceFlags.getSampled(), sc.getTraceFlags(), "sampled flag");
        assertNotSame(TraceState.getDefault(), sc.getTraceState(), "not default");
    }

    @Test
    void javaSpanInANewTraceDoesNotDiscardRunningPrograms() {
        enable(CobolTelemetry.FILE_TRACE_OPEN_CLOSE);
        // ホストのスパンが無い状態でルートが始まる(cobjrunでの実行など)
        CobolResolve.pushCallStackList("MAINPROG");
        // MAINPROGから呼ばれたJavaのコードが、新しいトレースのスパンを作ってCOBOLを呼ぶ
        Span java = provider.get("app").spanBuilder("java").setNoParent().startSpan();
        try (Scope ignored = java.makeCurrent()) {
            CobolResolve.pushCallStackList("SUBPROG");
            CobolResolve.popCallStackList();
        }
        java.end();
        CobolResolve.popCallStackList();

        SpanData main = find("MAINPROG");
        assertEquals(StatusCode.UNSET, main.getStatus().getStatusCode(), "caller is not discarded");
        assertEquals(
                main.getSpanContext().getSpanId(), find("SUBPROG").getParentSpanId(), "nested");
    }
}
