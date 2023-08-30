#!/usr/bin/env bash

set -x

echo "Ajetaan CMD"
# Ajetaan ulkopuolelta annettu CMD (esim. cypress run --browser chrome)
exec "$@"
