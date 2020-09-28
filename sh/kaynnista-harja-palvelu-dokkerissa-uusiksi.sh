#!/bin/bash
set -euo pipefail

SERVICE=$1
HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
COMPOSE_ENV_FILE="${HARJA_DIR}/.docker_compose_env"

docker-compose --env-file $COMPOSE_ENV_FILE restart $SERVICE