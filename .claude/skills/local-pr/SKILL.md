---
name: local-pr
description: 自分のフォークのデフォルトブランチに対してDraft Pull Requestを出す（上流に出す前の自己確認用）
---

# local-pr

自分のフォーク（`git remote -v` で確認する。上流の opensourcecobol/opensourcecobol4j ではない方）の
**デフォルトブランチ**に対して Draft Pull Request を出す。上流へ出す前に、自分のリポジトリのCIで確認するための手順。

デフォルトブランチ名は決め打ちにせず、`gh repo view <owner>/<repo> --json defaultBranchRef -q .defaultBranchRef.name`
で取得する（`gh pr create` は `--base` を省略すればデフォルトブランチを使う）。

- PRのタイトルと本文の言語は`CLAUDE.md`の「IssueとPull Requestの書き方」に従う(タイトルは英語、本文は前半が英語・後半が同じ内容の日本語)。
- タイトルにも本文にも、「Claudeが書いた」といった記述は一切入れない。
- `$ARGUMENTS` に `not-draft` が指定されていたら、Draftではなく通常のPull Requestを作成する。
