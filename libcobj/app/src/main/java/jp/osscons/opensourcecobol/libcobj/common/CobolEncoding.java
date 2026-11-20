package jp.osscons.opensourcecobol.libcobj.common;

/**
 * 文字コードを表す列挙型
 */
public enum CobolEncoding {
    /**
     * UTF-8を表す
     */
    UTF8,
    /**
     * 変換を行わず、ランタイムの文字セット({@link CobolCharset#CHARSET})のまま入出力することを表す。<br>
     * 名前は環境変数COB_CHARSETの導入前(文字セットがShift_JIS固定だった頃)の後方互換のために残している。
     */
    SHIFT_JIS,
};
