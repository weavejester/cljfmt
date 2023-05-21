set GRAALVM_HOME=%CIRCLE_WORKING_DIRECTORY%\graalvm\graalvm-ce-java17-22.3.2
set PATH=%PATH%;%GRAALVM_HOME%\bin
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
call ..\lein native-image
