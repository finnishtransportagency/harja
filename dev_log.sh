#!/bin/sh

if [ -z "$1" ]; then
    echo "usage: $0 <test_env_number>"
    echo "runs tail -f /var/log/messages on given server"
    exit 1
fi

ssh root@harja-dev$1 tail -f /var/log/messages
