# Install on Windows

Hagane COBOL is built with Visual Studio (for `cobj.exe`) and a JDK (for `libcobj.jar`).
It is tested with OpenJDK 21.

## 1. Prepare the tools

1. Install [Visual Studio](https://visualstudio.microsoft.com/) with the **Desktop development with C++** workload.
2. Install a [JDK](https://adoptium.net/).
3. Download the source code of the [latest release](https://github.com/yutaro-sakamoto/Hagane-COBOL/releases) and extract it.

## 2. Build cobj.exe

1. Open `win/opensourcecobol4j.sln` with Visual Studio.
2. Select the **Release** configuration.
3. Click **Build** → **Build Solution**.

`cobj.exe` is created in `win/x64/Release`.

## 3. Build libcobj.jar

Open PowerShell in the source directory and run:

```powershell
cd libcobj
.\gradlew shadowJar
```

`libcobj.jar` is created in `libcobj\app\build\libs\`.

## 4. Install the files

Run `make-install.ps1` in the `win` directory:

```powershell
cd win
.\make-install.ps1
```

The files are placed as follows:

| File | Location |
| -- | -- |
| cobj.exe | `C:\hagane-cobol\bin` |
| libcobj.jar | `C:\hagane-cobol\lib` |
| config files | `C:\hagane-cobol\config` |
| copy files | `C:\hagane-cobol\copy` |

::: tip
If you built the Debug configuration, change `\x64\Release\cobj.exe` in `win/make-install.ps1` to `\x64\Debug\cobj.exe`.
:::

## 5. Set the environment variables

1. Add `C:\hagane-cobol\bin` to `PATH`.
2. Set `CLASSPATH` to `.;C:\hagane-cobol\lib\libcobj.jar`. The leading `.` is needed to run the programs in the current directory.

::: warning Upgrading from 1.0.0
Hagane COBOL 1.0.0 was installed in `C:\opensourcecobol4j`. Replace that directory in `PATH` and `CLASSPATH`
with `C:\hagane-cobol`, and translate and compile your COBOL programs again.
`make-install.ps1` does not remove `C:\opensourcecobol4j`, so delete it by hand if it is no longer needed.
:::

Open a new terminal and check the installation:

```powershell
cobj --version
```

Next, see [Usage](./usage) to compile and run your first program.
