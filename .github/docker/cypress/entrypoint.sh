#!/usr/bin/env bash

set -x

if [[ "$(uname -a)" = *"arm"* || "$(uname -a)" = *"aarch64"* ]]; then
    echo "Varoitus: Käytät Cypress-imagen ARM-versiota."
    echo "Ainoastaan Cypressin electron-selain käytettävissä ARM-imagessa, muut selainversiot eivät tue vielä ARM-arkkitehtuuria."
fi

echo "Ajetaan CMD"
# Ajetaan ulkopuolelta annettu CMD (esim. cypress run --browser chrome)
exec "$@"
