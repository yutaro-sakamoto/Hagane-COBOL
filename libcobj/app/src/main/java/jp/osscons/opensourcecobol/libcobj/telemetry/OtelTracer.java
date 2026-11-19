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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenTelemetry APIを使ってスパンを作る実装。{@link CobolTelemetry}からのみ使用する。
 *
 * <p>プログラムのスパンはOpenTelemetryのスレッドローカルなコンテキスト({@link Context#current()})に 登録せず、自前のスタックで管理して親を明示する。
 * COBOLプログラムが例外で抜けて後始末が行われなかった場合でも、 スレッドプールのスレッドにコンテキストが残り、ホストアプリケーションの無関係なスパンが
 * COBOLのスパンの子になってしまうことを防ぐためである。 埋め込みSQLの実行中だけは{@link #activate()}で現在のプログラムのスパンをコンテキストに登録し、
 * JDBCの計装(OpenTelemetry Java agentなど)が作るスパンをその子にする。
 * ファイル操作のスパンも、操作の実行中だけコンテキストに登録する(INDEXEDファイルが内部で使うSQLiteへの JDBCのスパンをその子にするため)。
 */
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
    static final AttributeKey<Long> RECORDS_READ = AttributeKey.longKey("cobol.file.records_read");
    static final AttributeKey<Long> RECORDS_WRITTEN =
            AttributeKey.longKey("cobol.file.records_written");
    static final AttributeKey<Long> RECORDS_REWRITTEN =
            AttributeKey.longKey("cobol.file.records_rewritten");
    static final AttributeKey<Long> RECORDS_DELETED =
            AttributeKey.longKey("cobol.file.records_deleted");

    /** ランタイムエラーを記録するスパンイベントの名前 */
    static final String RUNTIME_ERROR_EVENT = "cobol.runtime_error";

    static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("message");
    static final AttributeKey<String> CODE_FILE_PATH = AttributeKey.stringKey("code.file.path");
    static final AttributeKey<Long> CODE_LINE_NUMBER = AttributeKey.longKey("code.line.number");

    /** SDKが登録されていないときの警告 */
    static final String NO_SDK_WARNING =
            "Warning: COB_OTEL_ENABLED is set, but no OpenTelemetry SDK is registered, so the spans"
                    + " are discarded. Attach the OpenTelemetry Java agent 2.23.0 or later, or"
                    + " register an SDK as GlobalOpenTelemetry.";

    /** 実行中のプログラム1つ分のスパンと、それを含むコンテキスト */
    private static final class Frame {
        final Span span;
        final Context context;

        Frame(Span span, Context context) {
            this.span = span;
            this.context = context;
        }
    }

    /** 実行中のファイル操作のスパンと、それを現在のコンテキストにしたスコープ */
    private static final class FileSpan {
        final Span span;
        final Scope scope;

        FileSpan(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
    }

    /** スレッドごとのトレースの状態 */
    private static final class ThreadState {
        /** 実行中のプログラムのスタック */
        final Deque<Frame> frames = new ArrayDeque<>();

        /**
         * 最後に終了した実行単位のルートのプログラムのコンテキスト。 ルートのプログラムが復帰した後に行われる暗黙のCLOSEなどのスパンを同じトレースに含めるために使う。
         */
        Context lastRoot;

        /** 実行中の実行単位でSTOP RUNを記録済みならtrue(巻き戻される呼び出し元には記録しない) */
        boolean stopRunRecorded;
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

    /** テストなどで明示的に指定されたTracer。nullならGlobalOpenTelemetryから取得する */
    private final Tracer fixedTracer;

    /** GlobalOpenTelemetryが設定された後に取得したTracer */
    private Tracer globalTracer;

    /**
     * 環境変数TRACEPARENT/TRACESTATEから取り出した親コンテキスト。無ければnull。<br>
     * COBOLのランタイム設定ではなくプロセスの起動元が渡すものなので、SET ENVIRONMENTの影響を受けない System.getenvで読む。
     */
    private final Context environmentParent;

    private final ThreadLocal<ThreadState> state = ThreadLocal.withInitial(ThreadState::new);

    /** {@link #NO_SDK_WARNING}を出力済みならtrue */
    private final AtomicBoolean noSdkWarned = new AtomicBoolean();

    /**
     * GlobalOpenTelemetryを使い、環境変数TRACEPARENT/TRACESTATEを親コンテキストとして作成する。
     *
     * @throws LinkageError OpenTelemetry APIが無いか、1.57より古い場合
     */
    OtelTracer() {
        this(null, parentFromEnvironment());
        // 1.57で追加されたメソッドを初期化時に一度呼び、古いAPIが選ばれていれば
        // COBOLプログラムの実行中ではなくここでLinkageErrorにする
        GlobalOpenTelemetry.isSet();
    }

    /**
     * 指定したTracerと親コンテキストを使って作成する。テストで使用する。
     *
     * @param tracer スパンを作るTracer。nullならGlobalOpenTelemetryから取得する
     * @param environmentParent 外部から引き継ぐ親コンテキスト。無ければnull
     */
    OtelTracer(Tracer tracer, Context environmentParent) {
        this.fixedTracer = tracer;
        this.environmentParent = environmentParent;
    }

    /**
     * スパンを作るTracerを返す。<br>
     * GlobalOpenTelemetryが未設定の間は{@link GlobalOpenTelemetry#get()}を呼ばない。
     * 呼ぶとnoopの実装が登録されてしまい、ホストアプリケーションが後からSDKを登録できなくなるためである。
     */
    private Tracer tracer() {
        if (fixedTracer != null) {
            return fixedTracer;
        }
        Tracer t = globalTracer;
        if (t != null) {
            return t;
        }
        if (!GlobalOpenTelemetry.isSet()) {
            return OpenTelemetry.noop().getTracer(INSTRUMENTATION_NAME);
        }
        t = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
        globalTracer = t;
        return t;
    }

    /**
     * 新しいスパンの親にするコンテキストを返す。
     *
     * @param st 現在のスレッドの状態
     * @return 実行中のプログラムがあればそのコンテキスト、無ければ呼び出し元の現在のコンテキスト。
     *     呼び出し元に有効なスパンが無く環境変数TRACEPARENTがあれば、そのコンテキスト
     */
    private Context parentContext(ThreadState st) {
        Frame top = st.frames.peek();
        if (top != null) {
            return top.context;
        }
        Context current = Context.current();
        if (Span.fromContext(current).getSpanContext().isValid()) {
            return current;
        }
        if (st.lastRoot != null) {
            return st.lastRoot;
        }
        return environmentParent != null ? environmentParent : current;
    }

    void programEnter(String programId) {
        ThreadState st = state.get();
        Frame top = st.frames.peek();
        if (top != null && isFromAnotherTrace(top)) {
            // 例外でrun_moduleを抜けたプログラムのフレームが、CobolRunUnit.end()を呼ばない
            // ホスト(スレッドプール)のスレッドに残っている。新しいリクエストを無関係な
            // 古いスパンの子にしないよう、残ったフレームを捨ててルートとして扱う。
            endAbandonedFrames(st, "abandoned: the program did not return");
            top = null;
        }
        Context parent;
        if (top != null) {
            parent = top.context;
        } else {
            // 新しい実行単位のルートなので、前の実行単位の状態は使わない
            st.lastRoot = null;
            st.stopRunRecorded = false;
            parent = parentContext(st);
        }
        String id = programId == null ? "" : programId;
        Span span =
                tracer().spanBuilder(id.isEmpty() ? "COBOL program" : id)
                        .setParent(parent)
                        .setAttribute(PROGRAM_ID, id)
                        .startSpan();
        st.frames.push(new Frame(span, parent.with(span)));
    }

    /**
     * ホストアプリケーションの現在のスパンが、実行中のフレームとは別のトレースに属するかを返す。
     *
     * @param top 実行中の最も内側のフレーム
     * @return 別のトレースのスパンが現在のコンテキストにあればtrue
     */
    private static boolean isFromAnotherTrace(Frame top) {
        SpanContext current = Span.current().getSpanContext();
        return current.isValid()
                && !current.getTraceId().equals(top.span.getSpanContext().getTraceId());
    }

    private static void endAbandonedFrames(ThreadState st, String description) {
        Frame f;
        while ((f = st.frames.poll()) != null) {
            f.span.setStatus(StatusCode.ERROR, description);
            f.span.end();
        }
    }

    void programExit() {
        ThreadState st = state.get();
        Frame f = st.frames.poll();
        if (f != null) {
            f.span.end();
            if (st.frames.isEmpty()) {
                st.lastRoot = f.context;
                warnIfNoSdk();
            }
        }
    }

    /** SDKが登録されないままルートのプログラムが終了したら、一度だけ警告する。 */
    private void warnIfNoSdk() {
        if (fixedTracer == null
                && globalTracer == null
                && !GlobalOpenTelemetry.isSet()
                && noSdkWarned.compareAndSet(false, true)) {
            System.err.println(NO_SDK_WARNING);
        }
    }

    void stopRun(int returnCode) {
        ThreadState st = state.get();
        Frame f = st.frames.peek();
        if (f == null) {
            return;
        }
        // STOP RUNを実行したプログラムにだけ記録する。巻き戻される呼び出し元のうち、
        // 実行単位のルートには終了コードとして返り値だけを記録する。
        if (!st.stopRunRecorded) {
            st.stopRunRecorded = true;
            f.span.setAttribute(STOP_RUN, true);
            f.span.setAttribute(RETURN_CODE, (long) returnCode);
        } else if (st.frames.size() == 1) {
            f.span.setAttribute(RETURN_CODE, (long) returnCode);
        }
    }

    void programError(String message, String sourceFile, int sourceLine) {
        ThreadState st = state.get();
        Frame top = st.frames.peek();
        if (top == null) {
            return;
        }
        AttributesBuilder attrs = Attributes.builder().put(ERROR_MESSAGE, message);
        if (sourceFile != null) {
            attrs.put(CODE_FILE_PATH, sourceFile).put(CODE_LINE_NUMBER, (long) sourceLine);
        }
        top.span.addEvent(RUNTIME_ERROR_EVENT, attrs.build());
        // ランタイムエラーは実行単位全体を終了させるので、実行中の全てのプログラムを失敗とする
        for (Frame f : st.frames) {
            f.span.setStatus(StatusCode.ERROR, message);
        }
    }

    Object fileStart(String operation, String fileName) {
        String name = fileName == null ? "" : fileName;
        Span span =
                tracer().spanBuilder(operation + " " + name)
                        .setParent(parentContext(state.get()))
                        .setAttribute(FILE_OPERATION, operation)
                        .setAttribute(FILE_NAME, name)
                        .startSpan();
        return new FileSpan(span, span.makeCurrent());
    }

    static void fileRecordCounts(
            Object handle, long read, long written, long rewritten, long deleted) {
        Span span = ((FileSpan) handle).span;
        span.setAttribute(RECORDS_READ, read);
        span.setAttribute(RECORDS_WRITTEN, written);
        span.setAttribute(RECORDS_REWRITTEN, rewritten);
        span.setAttribute(RECORDS_DELETED, deleted);
    }

    static void fileEnd(Object handle, byte[] fileStatus, Throwable error) {
        FileSpan fs = (FileSpan) handle;
        fs.scope.close();
        Span span = fs.span;
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

    /**
     * 実行中のプログラムのスパンを現在のコンテキストにする。<br>
     * 実行中のプログラムが無ければ、最後に終了した実行単位のルートのプログラムのスパンを使う
     * (実行単位の終了時に行う埋め込みSQLの後始末を同じトレースに含めるため)。
     *
     * @return 元のコンテキストに戻すためのスコープ。対象のスパンが無ければnull
     */
    CobolTelemetry.Activation activate() {
        ThreadState st = state.get();
        Frame f = st.frames.peek();
        Context ctx = f != null ? f.context : st.lastRoot;
        if (ctx == null) {
            return null;
        }
        Scope scope = ctx.makeCurrent();
        return scope::close;
    }

    void resetThreadState() {
        endAbandonedFrames(state.get(), "run unit ended before the program returned");
        state.remove();
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
        return extract(carrier);
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
