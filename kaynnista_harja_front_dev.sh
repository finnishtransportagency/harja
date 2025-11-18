#!/bin/bash
set -euo pipefail

clean() {
  set +e
  jobs -p | xargs -r kill
}

trap clean EXIT INT TERM

lein deps
[ -d node_modules ] || npm ci

if [[ $# -eq 0 ]]
then
  ENV_PROFILE=false
else
  ENV_PROFILE=true
fi

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit

if [[ "$ENV_PROFILE" = "true" ]]
then
  npm run less & lein trampoline build-dev
else
  npm run less & lein trampoline build-dev-no-env
fi
