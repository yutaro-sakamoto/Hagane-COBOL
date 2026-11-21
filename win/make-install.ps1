mkdir C:\hagane-cobol\bin\
mkdir C:\hagane-cobol\lib\
mkdir C:\hagane-cobol\config\
mkdir C:\hagane-cobol\copy\

copy .\x64\Release\cobj.exe C:\hagane-cobol\bin\
copy ..\libcobj\app\build\libs\libcobj.jar C:\hagane-cobol\lib\
copy ..\config\*.conf C:\hagane-cobol\config\
copy ..\copy\*.cpy C:\hagane-cobol\copy\
