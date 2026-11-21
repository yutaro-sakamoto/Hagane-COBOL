Hagane COBOL 2.0.0 drops the names inherited from opensource COBOL 4J: the Java package, the install paths and the package name used by `configure` now belong to Hagane COBOL. Programs translated by 1.0.0 must be translated and compiled again.

## Breaking Changes

- **The Java package of libcobj has changed** from `jp.osscons.opensourcecobol.libcobj` to `org.haganecobol.libcobj`, and the Maven groupId from `jp.osscons.opensourcecobol` to `org.haganecobol`.
  - Java translated by an earlier `cobj` must be translated and compiled again.
  - Java code that uses libcobj or generated programs directly must change its `import` statements (for example, `import jp.osscons.opensourcecobol.libcobj.common.CobolRunUnit;` becomes `import org.haganecobol.libcobj.common.CobolRunUnit;`).
  - Logging settings keyed by the package name change accordingly: use `org.slf4j.simpleLogger.log.org.haganecobol.libcobj...` instead of `org.slf4j.simpleLogger.log.jp.osscons.opensourcecobol.libcobj...`.
- **The install paths have changed.** Update `CLASSPATH`, `PATH` and any scripts that refer to the old paths. `make install` does not remove the files of an earlier version, so delete them by hand if they are no longer needed.

  | | 1.0.0 | 2.0.0 |
  | -- | -- | -- |
  | `libcobj.jar` (Linux) | `<prefix>/lib/opensourcecobol4j/libcobj.jar` | `<prefix>/lib/hagane-cobol/libcobj.jar` |
  | config and copy files (Linux) | `<prefix>/share/opensource-cobol-4j-1.0.0/` | `<prefix>/share/hagane-cobol-2.0.0/` |
  | Windows | `C:\opensourcecobol4j\` (`bin`, `lib`, `config`, `copy`) | `C:\hagane-cobol\` |

  `<prefix>` is the one given to `configure --prefix` (`/usr` in the installation guide). The Docker images (`ghcr.io/yutaro-sakamoto/hagane-cobol:2.0.0`, `2.0.0-utf8` and `latest`) set `CLASSPATH` to the new path.
- The package name used by `configure` (the tarball and `share/` directory name) is now `hagane-cobol-2.0.0` instead of `opensource-cobol-4j-1.0.0`, and bugs are reported to [GitHub Issues](https://github.com/yutaro-sakamoto/Hagane-COBOL/issues) instead of the OSS Consortium mail address.

## Security

- `CALL` with a program name resolved at run time no longer loads classes of the JDK (`java.`, `javax.`, `sun.`, `jdk.`, `com.sun.`) or names containing `..`, `/` or `\`. (#60)
- Embedded SQL: cursor names are validated to prevent SQL injection. (#62)
- File names containing a null byte are rejected. (#61)
- `cobj`: bounds checks prevent buffer overflows on long `-D`/`-I` options and on truncated UTF-8 in PROGRAM-ID. (#59)

## Bug Fixes

- INDEXED files: REWRITE and DELETE of a key that does not exist return status 23 (so INVALID KEY is executed) instead of 30. (#109)
- Doubles whose absolute value is at least 1e7 or less than 1e-3, such as arguments passed to `execute(...)` of a generated program, are no longer corrupted when moved to numeric items. (#110)
- MOVE from a group item that contains half-width spaces to a PIC N item. (#58)

## Others

- The website with installation and usage guides and the Javadoc of `libcobj` is published at <https://haganecobol.org/>. (#56, #113)
- Dependencies of `libcobj` were updated, including sqlite-jdbc 3.54.1.0. (#66)
