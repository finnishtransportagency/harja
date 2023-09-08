#!/usr/bin/env bash

# Exit, mikäli tulee virheitä tai löytyy määrittelemätön muuttuja
set -Eeu

# shellcheck disable=SC2034
IMAGE_REPO="ghcr.io/finnishtransportagency"
# shellcheck disable=SC2034
IMAGE_NAME="harja_cypress"

readonly NPM_CYPRESS_VERSION=$(node -pe 'require("../../../package").dependencies.cypress')

# Note: "--" optiota käyttämällä voi passata suoraan docker build komennolle optioita
#   Tässä annetaan ulkopuolelta tulevat optiot scriptille parsittavaksi ja passataan lisäksi --build-arg komento
#   mitä cypress Dockerimage tarvitsee
source ../scripts/build-image.sh "$@" -- \
   --build-arg="NPM_CYPRESS_VERSION=${NPM_CYPRESS_VERSION}"
