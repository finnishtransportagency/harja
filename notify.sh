#!/bin/bash

if hash terminal-notifier 2>/dev/null; then
    terminal-notifier -title leiningen-notify -group leiningen-notify -message "$1"
elif hash growlnotify 2>/dev/null; then
    growlnotify -d leiningen-notify -m $1
elif hash osascript 2>/dev/null; then
    osascript -e "display notification \"$1\" with title \"leiningen-notify\""
else
    echo $1
fi
