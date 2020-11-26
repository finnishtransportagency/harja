#!/bin/bash
set -euo pipefail

SERVICE=$1
# shellcheck source=harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit
ENV_FILE="${HARJA_DIR}/yhdistetty_dc_env"

sudo docker-compose --env-file "$ENV_FILE" restart "$SERVICE"
