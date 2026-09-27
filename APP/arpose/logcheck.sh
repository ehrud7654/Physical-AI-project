#!/usr/bin/env bash
# 업로드/인증/패키징 로그만 뽑아본다. 인자로 tail 줄 수(기본 20).
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" logcat -d -s UPLOAD:V AUTH:V PACKAGE:V 2>&1 | tail -"${1:-20}"
