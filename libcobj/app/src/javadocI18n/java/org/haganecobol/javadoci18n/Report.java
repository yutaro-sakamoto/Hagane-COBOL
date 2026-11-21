package org.haganecobol.javadoci18n;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 翻訳の結果と、見つかった問題の集計。 */
final class Report {

    /** 失敗したときに表示する、直し方の説明。 */
    static final String HOW_TO_FIX =
            "Run `./gradlew syncJavadocTranslations` in libcobj/, fill in the empty `en` fields"
                    + " in libcobj/javadoc-i18n/en/, run `./gradlew syncJavadocTranslations` again"
                    + " (it rewrites the files in the expected format), and commit the YAML files";

    private final List<Problem> problems = new ArrayList<>();
    private int units;
    private int translated;

    void unit() {
        units++;
    }

    void translated() {
        translated++;
    }

    void add(Problem problem) {
        problems.add(problem);
    }

    void addAll(Collection<Problem> list) {
        problems.addAll(list);
    }

    int count(Problem.Kind kind) {
        int n = 0;
        for (Problem p : problems) {
            if (p.kind == kind) {
                n++;
            }
        }
        return n;
    }

    /**
     * 失敗として扱う問題があるかどうか。
     *
     * @param strict 未翻訳と、{@code sync}の結果と一致しない翻訳ファイルも失敗として扱う場合はtrue
     * @return 失敗として扱う問題があればtrue
     */
    boolean failed(boolean strict) {
        for (Problem p : problems) {
            if (isFailure(p, strict)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFailure(Problem p, boolean strict) {
        switch (p.kind) {
            case ERROR:
                return true;
            case UNTRANSLATED:
            case OUT_OF_SYNC:
                return strict;
            default:
                return false;
        }
    }

    /**
     * 集計と問題の一覧を出力する。GitHub Actionsの注釈は1ステップにつき最初の10件しか表示されないため、直し方を含む集計を先に出力する。
     *
     * @param out 出力先
     * @param githubActions GitHub Actionsの注釈の形式で出力する場合はtrue
     * @param strict 未翻訳と、{@code sync}の結果と一致しない翻訳ファイルも失敗として扱う場合はtrue
     * @param repoRoot 表示するパスの基準にするディレクトリ。nullならパスをそのまま表示する
     * @param details 個々の未翻訳の単位も出力する場合はtrue
     */
    void print(
            PrintStream out,
            boolean githubActions,
            boolean strict,
            Path repoRoot,
            boolean details) {
        String summary =
                String.format(
                        "Javadoc translation: %d of %d units translated, %d untranslated,"
                                + " %d translation files out of sync, %d errors, %d warnings",
                        translated,
                        units,
                        count(Problem.Kind.UNTRANSLATED),
                        count(Problem.Kind.OUT_OF_SYNC),
                        count(Problem.Kind.ERROR),
                        count(Problem.Kind.WARNING));
        boolean fixable = count(Problem.Kind.UNTRANSLATED) + count(Problem.Kind.OUT_OF_SYNC) > 0;
        if (failed(strict)) {
            if (fixable && strict) {
                summary += ". " + HOW_TO_FIX + ".";
            }
            out.println(githubActions ? "::error::" + escapeData(summary) : summary);
        } else if (githubActions && !problems.isEmpty()) {
            out.println("::warning::" + escapeData(summary));
        } else {
            out.println(summary);
        }
        for (Problem p : problems) {
            if (p.kind == Problem.Kind.UNTRANSLATED && !details) {
                continue;
            }
            out.println(format(p, githubActions, strict, repoRoot));
        }
    }

    private static String format(Problem p, boolean githubActions, boolean strict, Path repoRoot) {
        String label;
        switch (p.kind) {
            case UNTRANSLATED:
                label = "untranslated";
                break;
            case OUT_OF_SYNC:
                label = "out of sync";
                break;
            case ERROR:
                label = "error";
                break;
            default:
                label = "warning";
                break;
        }
        String path = display(p.file, repoRoot);
        if (githubActions) {
            StringBuilder sb = new StringBuilder("::");
            sb.append(isFailure(p, strict) ? "error" : "warning");
            sb.append(" file=").append(escapeProperty(path));
            if (p.line > 0) {
                sb.append(",line=").append(p.line);
            }
            sb.append(",title=").append(escapeProperty("Javadoc translation: " + label));
            return sb.append("::").append(escapeData(p.message)).toString();
        }
        return label + ": " + path + (p.line > 0 ? ":" + p.line : "") + ": " + p.message;
    }

    private static String display(Path file, Path repoRoot) {
        if (repoRoot == null) {
            return file.toString().replace('\\', '/');
        }
        Path abs = file.toAbsolutePath().normalize();
        Path root = repoRoot.toAbsolutePath().normalize();
        return abs.startsWith(root) ? Files2.relative(root, abs) : abs.toString();
    }

    /**
     * GitHub Actionsのワークフローコマンドのメッセージ部分をエスケープする。
     *
     * @param s メッセージ
     * @return エスケープしたメッセージ
     */
    static String escapeData(String s) {
        return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    /**
     * GitHub Actionsのワークフローコマンドのプロパティの値をエスケープする。
     *
     * @param s プロパティの値
     * @return エスケープした値
     */
    static String escapeProperty(String s) {
        return escapeData(s).replace(":", "%3A").replace(",", "%2C");
    }
}
