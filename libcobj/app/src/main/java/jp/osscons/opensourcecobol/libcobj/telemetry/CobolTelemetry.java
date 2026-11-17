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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenTelemetryによるトレースの入口。<br>
 * libcobjの他のクラスはこのクラスだけを呼び出し、OpenTelemetry APIを直接参照しない。
 * OpenTelemetry APIを使う実装({@link OtelTracer})は、トレースが有効なときに初めてロードされる。
 * そのため、トレースが無効であればOpenTelemetry APIのクラスがクラスパスに無くても動作する。
 *
 * <p>トレースは環境変数で有効にする。
 *
 * <ul>
 *   <li>{@code COB_OTEL_ENABLED} - {@code Y}で始まる値を設定するとトレースを有効にする(既定は無効)。
 *   <li>{@code COB_OTEL_FILE_TRACE} - ファイル入出力のスパンの粒度。{@code none}(作らない)、 {@code
 *       open-close}(OPEN/CLOSEのみ、既定)、{@code all}(READ/WRITEなどレコード単位の操作も含む)。
 *   <li>{@code TRACEPARENT} / {@code TRACESTATE} - W3C Trace Context形式の親コンテキスト。
 *       実行単位の最初のプログラムのスパンをこのコンテキストの子にする(ジョブスケジューラなどから トレースを引き継ぐために使う)。
 * </ul>
 *
 * <p>スパンの送信先はOpenTelemetryの{@code GlobalOpenTelemetry}に登録されたSDKが決める。
 * OpenTelemetry Java agentを使うか、アプリケーション側でSDKを登録する。 SDKが登録されていなければ、スパンはどこにも送られない。
 */
public final class CobolTelemetry {

    /** ファイル入出力のスパンを作らない */
    public static final int FILE_TRACE_NONE = 0;

    /** ファイルのOPENとCLOSEだけスパンを作る */
    public static final int FILE_TRACE_OPEN_CLOSE = 1;

    /** ファイルの全ての操作についてスパンを作る */
    public static final int FILE_TRACE_ALL = 2;

    private static final AtomicReference<OtelTracer> TRACER = new AtomicReference<>();
    private static final AtomicInteger FILE_TRACE_LEVEL = new AtomicInteger(FILE_TRACE_OPEN_CLOSE);
    private static final Object LOCK = new Object();

    private CobolTelemetry() {}

    /**
     * 環境変数からトレースの設定を読み込む。ランタイムの初期化時に呼び出す。
     *
     * @param enabled {@code COB_OTEL_ENABLED}の値(未設定ならnull)
     * @param fileTrace {@code COB_OTEL_FILE_TRACE}の値(未設定ならnull)
     */
    public static void configure(String enabled, String fileTrace) {
        synchronized (LOCK) {
            FILE_TRACE_LEVEL.set(parseFileTraceLevel(fileTrace));
            if (!isYes(enabled)) {
                TRACER.set(null);
                return;
            }
            // 再初期化のときは、実行中のプログラムのスパンを保持している既存の実装をそのまま使う
            if (TRACER.get() != null) {
                return;
            }
            try {
                TRACER.set(new OtelTracer());
            } catch (LinkageError e) {
                System.err.println(
                        "Warning: COB_OTEL_ENABLED is set, but the OpenTelemetry API is not"
                                + " available. Tracing is disabled.");
            }
        }
    }

    /**
     * トレースの実装を差し替える。テストで使用する。
     *
     * @param newTracer 新しい実装。nullならトレースを無効にする
     * @param newFileTraceLevel ファイル入出力のスパンの粒度
     */
    static void setTracer(OtelTracer newTracer, int newFileTraceLevel) {
        synchronized (LOCK) {
            TRACER.set(newTracer);
            FILE_TRACE_LEVEL.set(newFileTraceLevel);
        }
    }

    private static boolean isYes(String s) {
        return s != null && !s.isEmpty() && (s.charAt(0) == 'y' || s.charAt(0) == 'Y');
    }

    private static int parseFileTraceLevel(String s) {
        if (s == null || s.isEmpty()) {
            return FILE_TRACE_OPEN_CLOSE;
        }
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "none":
                return FILE_TRACE_NONE;
            case "open-close":
                return FILE_TRACE_OPEN_CLOSE;
            case "all":
                return FILE_TRACE_ALL;
            default:
                System.err.println("Warning: COB_OTEL_FILE_TRACE format invalid, ignored.");
                return FILE_TRACE_OPEN_CLOSE;
        }
    }

    /**
     * トレースが有効かどうかを返す。
     *
     * @return トレースが有効ならtrue
     */
    public static boolean isEnabled() {
        return TRACER.get() != null;
    }

    /**
     * COBOLプログラムの実行開始を記録する。プログラムのスパンを開始し、現在のコンテキストにする。
     *
     * @param programId PROGRAM-ID
     */
    public static void programEnter(String programId) {
        OtelTracer t = TRACER.get();
        if (t != null) {
            t.programEnter(programId);
        }
    }

    /** COBOLプログラムの実行終了を記録する。{@link #programEnter}で開始したスパンを終了する。 */
    public static void programExit() {
        OtelTracer t = TRACER.get();
        if (t != null) {
            t.programExit();
        }
    }

    /**
     * 現在のプログラムのスパンにSTOP RUNが実行されたことを記録する。
     *
     * @param returnCode STOP RUNの返り値
     */
    public static void stopRun(int returnCode) {
        OtelTracer t = TRACER.get();
        if (t != null) {
            t.stopRun(returnCode);
        }
    }

    /**
     * ファイル操作のスパンを開始する。
     *
     * @param operation 操作名(OPEN、READなど)
     * @param recordLevel レコード単位の操作(READ/WRITEなど)ならtrue、OPEN/CLOSEならfalse
     * @param fileName SELECT句のファイル名
     * @return スパンのハンドル。スパンを作らない場合はnull
     */
    public static Object fileStart(String operation, boolean recordLevel, String fileName) {
        OtelTracer t = TRACER.get();
        if (t == null) {
            return null;
        }
        int required = recordLevel ? FILE_TRACE_ALL : FILE_TRACE_OPEN_CLOSE;
        if (FILE_TRACE_LEVEL.get() < required) {
            return null;
        }
        return t.fileStart(operation, fileName);
    }

    /**
     * {@link #fileStart}で開始したスパンを終了する。
     *
     * @param handle {@link #fileStart}の返り値。nullなら何もしない
     * @param fileStatus 操作後のファイル状態(2バイト)
     * @param error 操作中に発生した例外。無ければnull
     */
    public static void fileEnd(Object handle, byte[] fileStatus, Throwable error) {
        if (handle != null) {
            OtelTracer.fileEnd(handle, fileStatus, error);
        }
    }

    /** 現在のスレッドで終了していないプログラムのスパンを全て終了し、状態を破棄する。実行単位の終了時に呼び出す。 */
    public static void resetThreadState() {
        OtelTracer t = TRACER.get();
        if (t != null) {
            t.resetThreadState();
        }
    }
}
