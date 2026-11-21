package org.haganecobol.javadoci18n;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ファイル操作の小さな関数の集まり。 */
final class Files2 {

    private Files2() {}

    /**
     * ディレクトリ以下のファイルを、パスの順に並べて返す。
     *
     * @param dir 対象のディレクトリ
     * @param suffix ファイル名の末尾。nullなら全てのファイル
     * @return ファイルの一覧
     * @throws IOException ディレクトリを読めない場合
     */
    static List<Path> list(Path dir, String suffix) throws IOException {
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (suffix == null || file.toString().endsWith(suffix)) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        Collections.sort(files);
        return files;
    }

    /**
     * {@code /}区切りの相対パスを返す。
     *
     * @param dir 基準のディレクトリ
     * @param file 対象のファイル
     * @return 相対パス
     */
    static String relative(Path dir, Path file) {
        return dir.relativize(file).toString().replace('\\', '/');
    }

    /**
     * ファイルをUTF-8として読む。UTF-8として正しくないバイト列があればエラーにする。
     *
     * @param file 対象のファイル
     * @return ファイルの内容
     * @throws IOException 読み込みに失敗した場合や、UTF-8として正しくない場合
     */
    static String readUtf8(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException(file + " is not valid UTF-8", e);
        }
    }

    /**
     * ファイルの親ディレクトリを作成する。
     *
     * @param file 対象のファイル
     * @throws IOException 作成に失敗した場合
     */
    static void createParentDirectories(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * ファイルの親ディレクトリのうち空になったものを、{@code root}の手前まで削除する。
     *
     * @param file 削除したファイル
     * @param root 削除しないディレクトリ
     * @throws IOException 削除に失敗した場合
     */
    static void removeEmptyParents(Path file, Path root) throws IOException {
        Path stop = root.toAbsolutePath().normalize();
        Path dir = file.toAbsolutePath().normalize().getParent();
        while (dir != null && !dir.equals(stop) && dir.startsWith(stop)) {
            try (java.util.stream.Stream<Path> children = Files.list(dir)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(dir);
            dir = dir.getParent();
        }
    }
}
