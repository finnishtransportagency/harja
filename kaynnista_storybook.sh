#!/bin/bash
set -euo pipefail
# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/sh/harja_dir.sh" || exit
bash ${HARJA_DIR}/sh/tarkkaile_less.sh
lein do less once, build-dev-storybook