#!/bin/bash

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

sed -i '' -e "s/BRANCH=.*/BRANCH=$(git branch --show-current)/g" "${HARJA_DIR}/.docker_compose_env"

docker-compose up --scale harja-app=2