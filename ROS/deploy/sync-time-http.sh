#!/bin/bash
# 젯슨은 RTC가 없어 부팅 직후 시간이 틀린다. HTTPS 응답의 Date 헤더로 시스템 시간을 맞춘다.

while true; do
    HTTP_DATE=$(curl -k -sI --max-time 5 https://www.google.com | \
        grep -i '^date:' | \
        sed 's/^[Dd]ate: //')

    if [ -n "$HTTP_DATE" ]; then
        date -s "$HTTP_DATE"
        exit 0
    fi

    sleep 2
done
