#!/bin/bash
set -euo pipefail

SERVICE=$1
HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
ENV_FILE="${HARJA_DIR}/yhdistetty_dc_env"

sudo docker-compose --env-file $ENV_FILE restart $SERVICE
