/*
 * Copyright (C) 2021-2022 TOKYO SYSTEM HOUSE Co., Ltd.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3.0,
 * or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; see the file COPYING.LIB; if
 * not, write to the Free Software Foundation, 51 Franklin Street, Fifth Floor
 * Boston, MA 02110-1301 USA
 */
package jp.osscons.opensourcecobol.libcobj.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** OpenTelemetry APIを使ってスパンを作る実装。{@link CobolTelemetry}からのみ使用する。 */
final class OtelTracer {

    /** 計装ライブラリ名 */
    static final String INSTRUMENTATION_NAME = "jp.osscons.opensourcecobol.libcobj";

    static final AttributeKey<String> PROGRAM_ID = AttributeKey.stringKey("cobol.program.id");
    static final AttributeKey<Boolean> STOP_RUN = AttributeKey.booleanKey("cobol.stop_run");
    static final AttributeKey<Long> RETURN_CODE = AttributeKey.longKey("cobol.return_code");
    static final AttributeKey<String> FILE_NAME = AttributeKey.stringKey("cobol.file.name");
    static final AttributeKey<String> FILE_OPERATION =
            AttributeKey.stringKey("cobol.file.operation");
    static final AttributeKey<String> FILE_STATUS = AttributeKey.stringKey("cobol.file.status");

    /** 実行中のプログラム1つ分のスパンとスコープ */
    private static final class Frame {
        final Span span;
        final Scope scope;

        Frame(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
    }

    private static final TextMapGetter<Map<String, String>> MAP_GETTER =
            new TextMapGetter<Map<String, String>>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier == null ? null : carrier.get(key);
                }
            };

    private final Tracer tracer;
    private final ThreadLocal<Deque<Frame>> frames =
            new ThreadLocal<Deque<Frame>>() {
                @Override
                protected Deque<Frame> initialValue() {
                    return new ArrayDeque<>();
                }
            };

    /** GlobalOpenTelemetryからTracerを取得して作成する。 */
    OtelTracer() {
        this(GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME));
    }

    /**
     * 指定したTracerを使って作成する。テストで使用する。
     *
     * @param tracer スパンを作るTracer
     */
    OtelTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    void programEnter(String programId) {
        Deque<Frame> stack = frames.get();
        SpanBuilder builder =
                tracer.spanBuilder(programId == null ? "COBOL program" : programId)
                        .setAttribute(PROGRAM_ID, programId == null ? "" : programId);
        if (stack.isEmpty()) {
            Context parent = parentFromEnvironment();
            if (parent != null) {
                builder.setParent(parent);
            }
        }
        Span span = builder.startSpan();
        stack.push(new Frame(span, span.makeCurrent()));
    }

    void programExit() {
        Frame f = frames.get().poll();
        if (f != null) {
            f.scope.close();
            f.span.end();
        }
    }

    void stopRun(int returnCode) {
        Frame f = frames.get().peek();
        if (f != null) {
            f.span.setAttribute(STOP_RUN, true);
            f.span.setAttribute(RETURN_CODE, (long) returnCode);
        }
    }

    Object fileStart(String operation, String fileName) {
        String name = fileName == null ? "" : fileName;
        return tracer.spanBuilder(operation + " " + name)
                .setAttribute(FILE_OPERATION, operation)
                .setAttribute(FILE_NAME, name)
                .startSpan();
    }

    static void fileEnd(Object handle, byte[] fileStatus, Throwable error) {
        Span span = (Span) handle;
        if (fileStatus != null && fileStatus.length >= 2) {
            String status = new String(fileStatus, 0, 2, StandardCharsets.US_ASCII);
            span.setAttribute(FILE_STATUS, status);
            // 0x(成功)、1x(ファイル終了)、2x(キー不正)はプログラムが処理する通常の結果として扱い、
            // 3x以上(永続エラー、論理エラーなど)をエラーとする。
            if (fileStatus[0] >= '3') {
                span.setStatus(StatusCode.ERROR, "file status " + status);
            }
        }
        if (error != null) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR);
        }
        span.end();
    }

    void resetThreadState() {
        Deque<Frame> stack = frames.get();
        Frame f;
        while ((f = stack.poll()) != null) {
            f.span.setStatus(StatusCode.ERROR, "run unit ended before the program returned");
            f.scope.close();
            f.span.end();
        }
        frames.remove();
    }

    /**
     * 環境変数TRACEPARENT/TRACESTATEから親コンテキストを取り出す。
     *
     * @return 親コンテキスト。環境変数が無いか不正な場合はnull
     */
    private static Context parentFromEnvironment() {
        String traceparent = System.getenv("TRACEPARENT");
        if (traceparent == null || traceparent.isEmpty()) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        carrier.put("traceparent", traceparent);
        String tracestate = System.getenv("TRACESTATE");
        if (tracestate != null && !tracestate.isEmpty()) {
            carrier.put("tracestate", tracestate);
        }
        return extract(Collections.unmodifiableMap(carrier));
    }

    /**
     * W3C Trace Contextのヘッダから親コンテキストを取り出す。
     *
     * @param carrier traceparent/tracestateを含むマップ
     * @return 親コンテキスト。有効なスパンコンテキストが無ければnull
     */
    static Context extract(Map<String, String> carrier) {
        Context ctx =
                W3CTraceContextPropagator.getInstance()
                        .extract(Context.root(), carrier, MAP_GETTER);
        if (!Span.fromContext(ctx).getSpanContext().isValid()) {
            return null;
        }
        return ctx;
    }
}
