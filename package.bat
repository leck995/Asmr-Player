@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem =====================================================================
rem  AsmrPlayer packaging (Windows)
rem
rem  A default run builds the full release set:
rem     [1/5] jlink runtime image  -> target\app
rem     [2/5] VLC staging          -> target\vlc-app-content\vlc
rem     [3/5] jpackage app-image   -> target\image\AsmrPlayer           (portable)
rem     [4/5] MSI installer        -> target\jpackage\AsmrPlayer-<version>.msi
rem     [5/5] burn installer       -> target\jpackage\AsmrPlayer-<version>.exe
rem
rem  Both installers are burned from the app-image of step 3 with
rem  --app-image, so msi and exe carry byte-identical content and re-burning
rem  them does not repeat the maven/jlink build.
rem
rem  APP_VERSION defaults to the <version> element of pom.xml.
rem  msi/exe need WiX Toolset: candle.exe + light.exe from v3, or wix.exe
rem  from v4/v5, reachable from PATH or from a WiX Toolset v3 install dir.
rem =====================================================================

set "APP_NAME=AsmrPlayer"
if not defined APP_VERSION call :read_pom_version
if not defined APP_VERSION (
    echo [ERROR] could not read the version from pom.xml; set APP_VERSION.
    exit /b 1
)
rem jpackage derives the MSI registry path from --vendor; non-ASCII is mangled
rem to "?" by cmd, so Windows uses the English brand. package.sh keeps the
rem Chinese one.
set "VENDOR=Future Gadget Laboratory"
set "AUTHOR=leck"
set "MAIN_MODULE=cn.tealc.asmr/cn.tealc.asmr.Main"
set "ICON=src\main\resources\cn\tealc\asmr\image\logo.ico"
set "JLINK_IMAGE=target\app"
set "IMAGE_DIR=target\image"
set "PKG_DIR=target\jpackage"
set "ZIP_PATH=%PKG_DIR%\%APP_NAME%-%APP_VERSION%-windows-x64-portable.zip"
set "VLC_DIR=vlc"
set "VLC_PLATFORM=windows-amd64"
set "VLC_STAGE=target\vlc-app-content\vlc"
set "APP_IMAGE=%IMAGE_DIR%\%APP_NAME%"
rem Pinned so a new release replaces the previous one instead of leaving a
rem second entry in "Add or remove programs". Keep it stable across versions.
set "UPGRADE_UUID=fc12e61a-908f-42fe-b08b-c3d33f9aa383"
set "PF86=%ProgramFiles(x86)%"

rem Identity options shared by the app-image and by both installer calls.
set "COMMON_OPTS=--name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --icon "%ICON%" --copyright "Copyright (C) 2026 %AUTHOR%" --description "%APP_NAME% (author: %AUTHOR%)""

set "PKG_TYPE=all"
set "DO_BUILD=1"
set "DO_IMAGE=1"
set "VERBOSE_OPT="
set "USAGE_FAIL="

:parse_args
if "%~1"=="" goto :args_done
set "ARG=%~1"
if /i "%ARG%"=="all" (set "PKG_TYPE=all" & goto :next_arg)
if /i "%ARG%"=="image" (set "PKG_TYPE=image" & goto :next_arg)
if /i "%ARG%"=="portable" (set "PKG_TYPE=portable" & goto :next_arg)
if /i "%ARG%"=="zip" (set "PKG_TYPE=portable" & goto :next_arg)
if /i "%ARG%"=="msi" (set "PKG_TYPE=msi" & goto :next_arg)
if /i "%ARG%"=="exe" (set "PKG_TYPE=exe" & goto :next_arg)
if /i "%ARG%"=="--skip-build" (set "DO_BUILD=" & goto :next_arg)
if /i "%ARG%"=="--skip-image" (set "DO_IMAGE=" & goto :next_arg)
if /i "%ARG%"=="-v" (set "VERBOSE_OPT=--verbose" & goto :next_arg)
if /i "%ARG%"=="--verbose" (set "VERBOSE_OPT=--verbose" & goto :next_arg)
if /i "%ARG%"=="-h" goto :usage
if /i "%ARG%"=="--help" goto :usage
echo [ERROR] unknown option: %ARG%
set "USAGE_FAIL=1"
goto :usage
:next_arg
shift
goto :parse_args

:args_done
set "BUILD_IMAGE=%DO_IMAGE%"
set "BUILD_ZIP="
set "BUILD_MSI="
set "BUILD_EXE="
rem The default target is the full release set: jlink image + app-image + msi + exe.
if /i "%PKG_TYPE%"=="portable" set "BUILD_ZIP=1"
if /i "%PKG_TYPE%"=="msi" set "BUILD_MSI=1"
if /i "%PKG_TYPE%"=="exe" set "BUILD_EXE=1"
if /i "%PKG_TYPE%"=="all" (
    set "BUILD_MSI=1"
    set "BUILD_EXE=1"
)
if defined BUILD_MSI set "NEED_WIX=1"
if defined BUILD_EXE set "NEED_WIX=1"
rem Reusing the app-image must not run maven, because its clean wipes that image.
if not defined BUILD_IMAGE set "DO_BUILD="
if not defined BUILD_IMAGE if not defined BUILD_MSI if not defined BUILD_EXE if not defined BUILD_ZIP (
    echo [ERROR] nothing to build: --skip-image is only useful with portable, msi or exe.
    set "USAGE_FAIL=1"
    goto :usage
)

echo ==========================================
echo  %APP_NAME% %APP_VERSION% packaging
echo ==========================================
if defined DO_BUILD (echo  build  jlink image: %JLINK_IMAGE%) else (echo  reuse  jlink image: %JLINK_IMAGE%)
if defined BUILD_IMAGE (echo  build  app-image: %APP_IMAGE%) else (echo  reuse  app-image: %APP_IMAGE%)
if defined BUILD_ZIP echo  pack   portable zip: %ZIP_PATH%
if not defined BUILD_MSI echo  skip   msi installer
if not defined BUILD_EXE echo  skip   burn installer

rem ------------------------------------------------------------ prereqs
if not defined DO_BUILD goto :prereq_mvn_done
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] mvn not found. Add Maven bin to PATH.
    exit /b 1
)
:prereq_mvn_done
if not defined BUILD_IMAGE if not defined BUILD_MSI if not defined BUILD_EXE goto :prereq_jpackage_done
where jpackage >nul 2>nul
if errorlevel 1 (
    echo [ERROR] jpackage not found. Add JDK 25 bin to PATH.
    exit /b 1
)
:prereq_jpackage_done
if not defined BUILD_IMAGE if not defined BUILD_MSI if not defined BUILD_EXE goto :prereq_icon_done
if not exist "%ICON%" (
    echo [ERROR] icon not found: %ICON%
    exit /b 1
)
:prereq_icon_done
if not defined BUILD_IMAGE goto :prereq_vlc_done
if not exist "%VLC_DIR%\%VLC_PLATFORM%\libvlc.dll" (
    echo [ERROR] bundled VLC not found: %VLC_DIR%\%VLC_PLATFORM%\libvlc.dll
    exit /b 1
)
:prereq_vlc_done
if not defined BUILD_ZIP goto :prereq_powershell_done
where powershell >nul 2>nul
if errorlevel 1 (
    echo [ERROR] powershell not found. The portable zip needs Compress-Archive.
    exit /b 1
)
:prereq_powershell_done
if not defined NEED_WIX goto :prereq_wix_done
call :find_wix
if errorlevel 1 (set "USAGE_FAIL=1" & goto :usage)
:prereq_wix_done

rem ------------------------------------------------------------- clean up
if not defined DO_BUILD goto :clean_build_done
call :wipe "%JLINK_IMAGE%"
if errorlevel 1 (
    echo [ERROR] cannot remove old runtime image: %JLINK_IMAGE%
    echo         Close the running %APP_NAME%.exe, then retry.
    exit /b 1
)
rem maven clean drops the installers too; wiping them here reports a locked
rem msi as this script's error instead of as a maven stack trace.
call :wipe "%PKG_DIR%"
if errorlevel 1 (
    echo [ERROR] cannot remove old installers: %PKG_DIR%
    echo         Close the installer or the running %APP_NAME%.exe, then retry.
    exit /b 1
)
:clean_build_done
if not defined BUILD_IMAGE goto :clean_image_done
call :wipe "%IMAGE_DIR%"
if errorlevel 1 (
    echo [ERROR] cannot remove old app-image: %IMAGE_DIR%
    echo         Close the running %APP_NAME%.exe, then retry.
    exit /b 1
)
:clean_image_done

rem ---------------------------------------------------------------- [1/5]
if not defined BUILD_IMAGE goto :step1_done
if not defined DO_BUILD goto :step1_check
echo.
echo [1/5] Building jlink runtime image...
call mvn -B -DskipTests clean compile javafx:jlink
if errorlevel 1 (
    echo [ERROR] maven clean + compile + jlink failed.
    echo         A "Failed to delete target" line means a packaged build is
    echo         still running; close it and repeat this command.
    exit /b 1
)
:step1_check
if not exist "%JLINK_IMAGE%\bin\java.exe" (
    echo [ERROR] runtime image not found: %JLINK_IMAGE%
    echo         Drop --skip-build to let maven generate it.
    exit /b 1
)
:step1_done

rem ---------------------------------------------------------------- [2/5]
if not defined BUILD_IMAGE goto :step2_done
echo.
echo [2/5] Staging VLC for %VLC_PLATFORM%...
call :wipe "%VLC_STAGE%"
mkdir "%VLC_STAGE%\%VLC_PLATFORM%" 2>nul
xcopy /E /I /Y /Q "%VLC_DIR%\%VLC_PLATFORM%\*" "%VLC_STAGE%\%VLC_PLATFORM%\" >nul
copy /Y "%VLC_DIR%\COPYING-GPL-2.0.txt" "%VLC_STAGE%\" >nul
copy /Y "%VLC_DIR%\COPYING-LGPL-2.1.txt" "%VLC_STAGE%\" >nul
copy /Y "%VLC_DIR%\README.md" "%VLC_STAGE%\" >nul
if not exist "%VLC_STAGE%\%VLC_PLATFORM%\libvlc.dll" (
    echo [ERROR] failed to stage VLC: %VLC_STAGE%
    exit /b 1
)
:step2_done

rem ---------------------------------------------------------------- [3/5]
if not defined BUILD_IMAGE goto :step3_done
echo.
echo [3/5] Building jpackage app-image...
jpackage %VERBOSE_OPT% ^
    --type app-image ^
    %COMMON_OPTS% ^
    --runtime-image "%JLINK_IMAGE%" ^
    --module "%MAIN_MODULE%" ^
    --java-options "-Djavafx.enablePreview=true" ^
    --java-options "-XX:+UseZGC" ^
    --app-content "%VLC_STAGE%" ^
    --dest "%IMAGE_DIR%"
if errorlevel 1 (
    echo [ERROR] jpackage app-image failed.
    exit /b 1
)
:step3_done
if not defined BUILD_MSI if not defined BUILD_EXE if not defined BUILD_ZIP goto :zip_done
if not exist "%APP_IMAGE%\%APP_NAME%.exe" (
    echo [ERROR] app-image not found: %APP_IMAGE%
    echo         Run "package.bat image" without --skip-image.
    exit /b 1
)
if not defined BUILD_ZIP goto :zip_done
call :pack_portable
if errorlevel 1 exit /b 1
:zip_done
if defined NEED_WIX goto :step4
goto :summary

rem ---------------------------------------------------------------- [4/5]
:step4
set "INSTALLER_OPTS=%COMMON_OPTS% --app-image "%APP_IMAGE%" --dest "%PKG_DIR%" --win-dir-chooser --win-menu --win-shortcut --win-upgrade-uuid "%UPGRADE_UUID%""
if not defined BUILD_MSI goto :step5
call :wipe_file "%PKG_DIR%\%APP_NAME%-%APP_VERSION%.msi"
echo.
echo [4/5] Building MSI installer...
jpackage %VERBOSE_OPT% --type msi %INSTALLER_OPTS%
if errorlevel 1 (
    echo [ERROR] jpackage msi failed.
    exit /b 1
)

rem ---------------------------------------------------------------- [5/5]
:step5
if not defined BUILD_EXE goto :summary
call :wipe_file "%PKG_DIR%\%APP_NAME%-%APP_VERSION%.exe"
echo.
echo [5/5] Building burn installer...
jpackage %VERBOSE_OPT% --type exe %INSTALLER_OPTS%
if errorlevel 1 (
    echo [ERROR] jpackage exe failed.
    exit /b 1
)

rem --------------------------------------------------------------- result
:summary
echo.
echo ==========================================
echo  Done: %APP_NAME% %APP_VERSION%
echo ==========================================
if exist "%JLINK_IMAGE%\bin\java.exe" echo  runtime      %JLINK_IMAGE%
if exist "%APP_IMAGE%\%APP_NAME%.exe" echo  portable     %APP_IMAGE%\%APP_NAME%.exe
if defined BUILD_ZIP if exist "%ZIP_PATH%" (
    for %%F in ("%ZIP_PATH%") do echo  portable zip %%~nxF   %%~zF bytes
)
if defined BUILD_MSI if exist "%PKG_DIR%\%APP_NAME%-%APP_VERSION%.msi" (
    for %%F in ("%PKG_DIR%\%APP_NAME%-%APP_VERSION%.msi") do echo  installer    %%~nxF   %%~zF bytes
)
if defined BUILD_EXE if exist "%PKG_DIR%\%APP_NAME%-%APP_VERSION%.exe" (
    for %%F in ("%PKG_DIR%\%APP_NAME%-%APP_VERSION%.exe") do echo  installer    %%~nxF   %%~zF bytes
)
echo.
endlocal
exit /b 0

:usage
echo.
echo  %APP_NAME% %APP_VERSION% packaging
echo.
echo  Usage:  package.bat [all^|image^|portable^|msi^|exe] [options]
echo.
echo    all           jlink image + app-image + msi + exe                 (default)
echo    image         jlink image + app-image only, no installers
echo    portable      jlink image + app-image + portable zip, no installers
echo    zip           same as portable
echo    msi           jlink image + app-image + msi only
echo    exe           jlink image + app-image + burn installer only
echo.
echo    --skip-build  reuse %JLINK_IMAGE% instead of maven + jlink
echo    --skip-image  reuse %APP_IMAGE% instead of rebuilding it
echo                  implies --skip-build, because maven clean wipes target
echo    -v, --verbose pass jpackage --verbose
echo    -h, --help    show this help
echo.
echo  APP_VERSION defaults to the ^<version^> element of pom.xml; set the
echo  environment variable to override it.
echo.
echo  msi and exe need WiX Toolset. Install it from an elevated prompt:
echo      winget install --id WiXToolset.WiXToolset -e
echo.
if defined USAGE_FAIL exit /b 1
exit /b 0

rem ================================================================= helpers

rem Read the project version from pom.xml. The first <version> element in the
rem file is the project's own; dependency versions come later.
:read_pom_version
if not exist "pom.xml" exit /b 0
for /f "tokens=3 delims=<>" %%V in ('findstr /r /c:"^[ ]*<version>" pom.xml') do (
    set "APP_VERSION=%%V"
    goto :eof
)
exit /b 0

rem Put WiX on PATH. Sets WIX_OK when found, otherwise exits 1 with a hint.
:find_wix
set "WIX_OK="
set "WIX_BIN="
where candle.exe >nul 2>nul
if not errorlevel 1 (
    where light.exe >nul 2>nul
    if not errorlevel 1 set "WIX_BIN=PATH"
)
if defined WIX_BIN goto :wix_ready
where wix.exe >nul 2>nul
if not errorlevel 1 set "WIX_BIN=PATH"
if defined WIX_BIN goto :wix_ready
call :wix_dir "%WIX%"
if defined WIX_BIN goto :wix_ready
for /d %%D in ("%PF86%\WiX Toolset v3*") do call :wix_dir "%%D"
if defined WIX_BIN goto :wix_ready
for /d %%D in ("%ProgramFiles%\WiX Toolset v3*") do call :wix_dir "%%D"
if defined WIX_BIN goto :wix_ready
call :wix_dir "%USERPROFILE%\.dotnet\tools"
if defined WIX_BIN goto :wix_ready
echo [ERROR] WiX Toolset not found: msi and exe cannot be built.
echo         candle.exe + light.exe from WiX v3, or wix.exe from WiX v4/v5,
echo         must be reachable from PATH, from WIX or from a WiX install dir.
echo         Install it from an elevated prompt:
echo             winget install --id WiXToolset.WiXToolset -e
echo         Or build only the portable app-image:
echo             package.bat image
echo         Or build the portable zip without WiX:
echo             package.bat portable
exit /b 1

:wix_ready
if not "%WIX_BIN%"=="PATH" set "PATH=%WIX_BIN%;%PATH%"
set "WIX_OK=1"
echo  WiX         %WIX_BIN%
exit /b 0

:wix_dir
if defined WIX_BIN exit /b 0
if "%~1"=="" exit /b 0
if exist "%~1\bin\candle.exe" if exist "%~1\bin\light.exe" set "WIX_BIN=%~1\bin"
if exist "%~1\candle.exe" if exist "%~1\light.exe" set "WIX_BIN=%~1"
if exist "%~1\wix.exe" set "WIX_BIN=%~1"
exit /b 0

rem Remove a directory tree, clearing read-only files first. Errorlevel 1
rem when the tree survives, which almost always means a locked launcher.
:wipe
if not exist "%~1" exit /b 0
attrib -R /S /D "%~1\*" >nul 2>nul
rmdir /s /q "%~1" >nul 2>nul
if exist "%~1" exit /b 1
exit /b 0

:wipe_file
if not exist "%~1" exit /b 0
del /f /q "%~1" >nul 2>nul
exit /b 0

rem Zip the app-image. The archive contains the AsmrPlayer folder, so users
rem unzip it and run AsmrPlayer\AsmrPlayer.exe.
:pack_portable
echo.
echo [portable] Packing %ZIP_PATH% ...
if not exist "%PKG_DIR%" mkdir "%PKG_DIR%" >nul 2>nul
call :wipe_file "%ZIP_PATH%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%APP_IMAGE%' -DestinationPath '%ZIP_PATH%' -Force"
if errorlevel 1 (
    echo [ERROR] portable zip failed: %ZIP_PATH%
    echo         Close any program reading the app-image, then retry.
    exit /b 1
)
exit /b 0
