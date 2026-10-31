package jp.osscons.opensourcecobol.libcobj.common;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;

/**
 * ランタイムがCOBOLデータ項目のバイト列とJavaの文字列を相互に変換するときに使う文字セットを一元管理するクラス<br>
 * 文字セットは環境変数COB_CHARSETで指定する。未設定または空文字列の場合は後方互換のためShift_JISを使用する。
 * 値はこのクラスの初期化時に1回だけ読み込まれる。
 */
public final class CobolCharset {
    /** 文字セットを指定する環境変数の名前 */
    public static final String ENV_NAME = "COB_CHARSET";

    /** 環境変数COB_CHARSETが未設定の場合に使用する文字セットの名前 */
    public static final String DEFAULT_NAME = "SHIFT-JIS";

    /** ASCII互換性の確認に使う文字列 */
    private static final String ASCII_SAMPLE = " 0123456789+-.,AZaz";

    /** ランタイムが使用する文字セット */
    public static final Charset CHARSET = resolve(System.getenv(ENV_NAME));

    private CobolCharset() {}

    /**
     * 環境変数COB_CHARSETの値から文字セットを決定する。
     *
     * @param name 環境変数COB_CHARSETの値。nullまたは空文字列の場合は{@link #DEFAULT_NAME}を使用する
     * @return 決定した文字セット
     * @throws IllegalArgumentException Javaがサポートしない文字セット名、またはASCIIと互換でない文字セットが指定された場合
     */
    static Charset resolve(String name) {
        if (name == null || name.isEmpty()) {
            return Charset.forName(DEFAULT_NAME);
        }
        Charset charset;
        try {
            charset = Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException(
                    ENV_NAME + ": unsupported character set '" + name + "'", e);
        }
        // ランタイムは空白や数字などのASCII文字が1バイトで表現されることを前提としている
        if (!Arrays.equals(
                ASCII_SAMPLE.getBytes(charset), ASCII_SAMPLE.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(
                    ENV_NAME + ": character set '" + name + "' is not ASCII compatible");
        }
        return charset;
    }
}
