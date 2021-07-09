#!/bin/bash
set -euo pipefail

if [[ $# -eq 0 ]]
then
  ENV_PROFILE=false
else
  ENV_PROFILE=true
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit
bash ${HARJA_DIR}/sh/tarkkaile_less.sh
if [[ "$ENV_PROFILE" = "true" ]]
then
  lein do less once, build-dev
else
  lein do less once, build-dev-no-env
fi