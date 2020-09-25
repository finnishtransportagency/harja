#!/bin/bash

echo "Generoidaan less -> CSS taustalla..."
bash sh/tarkkaile_less.sh
echo "Käynnistetään figwheel"
lein do less once, build-dev