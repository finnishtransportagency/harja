#!/bin/bash
set -euo pipefail

# Tapa less-tarkkailu, kun tämä prosessi tapetaan
trap 'kill $( pgrep -f [t]arkkaile_less_muutoksia.sh || echo '' ); exit' INT TERM

if [[ $# -eq 0 ]]
then
  ENV_PROFILE=false
else
  ENV_PROFILE=true
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit
bash "${HARJA_DIR}"/sh/tarkkaile_less.sh
if [[ "$ENV_PROFILE" = "true" ]]
then
  lein trampoline build-dev
else
  lein trampoline build-dev-no-env
fi
