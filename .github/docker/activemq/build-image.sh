#!/usr/bin/env bash

# Exit, mikäli tulee virheitä tai löytyy määrittelemätön muuttuja
set -Eeu

# shellcheck disable=SC2034
IMAGE_REPO="ghcr.io/finnishtransportagency"
# shellcheck disable=SC2034
IMAGE_NAME="harja_activemq"

source ../scripts/build-image.sh
