#!/bin/bash

set -euo pipefail

echo "Luetaan arvot ympäristömuuttujiin"
readarray -t muuttujat < tapahtuman_arvot

for e in "${muuttujat[@]}"
do
  # shellcheck disable=SC2163
  export "${e}";
done;