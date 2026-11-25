# Security Policy

## Reporting a vulnerability

Please **do not** report security vulnerabilities through public issues, pull requests or discussions.

Report them privately through GitHub's private vulnerability reporting:
<https://github.com/yutaro-sakamoto/Hagane-COBOL/security/advisories/new>

Please include, where available:

- the affected version or commit;
- the affected component (`cobj`, `libcobj`, generated Java code, build or installation scripts, Docker images);
- steps to reproduce, ideally with a minimal COBOL program;
- the observed behavior, expected behavior and potential impact;
- whether the issue is already public anywhere.

Please redact credentials, personal data and confidential production information from logs and examples.

## Scope

In scope: the `cobj` compiler, the `libcobj` runtime, the Java code that `cobj` generates, and the build scripts and Docker images published from this repository.

Behavior consistent with documented COBOL semantics is not, by itself, evidence of a vulnerability. Reports involving a violation of a security boundary or unexpected security impact are welcome. If you are unsure, please report privately.

## Supported versions

| Version | Supported |
|---------|-----------|
| 2.0.x | Yes |
| Earlier Hagane COBOL versions | No |

## Disclosure

The maintainers will coordinate disclosure with the reporter, normally after a fix or mitigation is available. Reporter credit will be discussed with the reporter. Please keep reports private while disclosure is being coordinated.

---

# セキュリティポリシー

## 脆弱性の報告

セキュリティ上の脆弱性は、公開の Issue、Pull Request、Discussion では**報告しないでください**。

GitHub の非公開の脆弱性報告機能から報告してください:
<https://github.com/yutaro-sakamoto/Hagane-COBOL/security/advisories/new>

可能な範囲で、以下を含めてください。

- 影響を受けるバージョンまたはコミット
- 影響を受けるコンポーネント（`cobj`、`libcobj`、生成される Java コード、ビルドやインストールのスクリプト、Docker イメージ）
- 再現手順（できれば最小限の COBOL プログラム）
- 実際の動作、期待される動作、想定される影響
- その問題がすでにどこかで公開されているかどうか

ログや再現例に含まれる認証情報、個人情報、本番環境の機密情報は伏せてください。

## 対象範囲

対象: `cobj` コンパイラ、`libcobj` ランタイム、`cobj` が生成する Java コード、およびこのリポジトリから公開されるビルドスクリプトと Docker イメージ。

文書化された COBOL の仕様に沿った動作は、それだけでは脆弱性の根拠にはなりません。セキュリティ境界の侵害や予期しないセキュリティ上の影響に関する報告は歓迎します。判断に迷う場合は、非公開で報告してください。

## サポート対象のバージョン

| バージョン | サポート |
|------------|----------|
| 2.0.x | あり |
| それ以前の Hagane COBOL バージョン | なし |

## 公開

メンテナーは報告者と公開時期を調整します。通常は修正または緩和策が利用可能になってから公開します。報告者のクレジットについては本人と相談します。公開の調整中は、報告内容を非公開にしてください。
