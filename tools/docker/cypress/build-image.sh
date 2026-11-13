#!/usr/bin/env bash

# Exit, mikäli tulee virheitä tai löytyy määrittelemätön muuttuja
set -Eeu

# shellcheck disable=SC2034
IMAGE_REPO="ghcr.io/finnishtransportagency"
# shellcheck disable=SC2034
IMAGE_NAME="harja_cypress"

NPM_CYPRESS_VERSION=$(jq -r '.devDependencies.cypress' ../../../package.json)

if [[ ! "$NPM_CYPRESS_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
   echo "Virheellinen Cypress-versio: '${NPM_CYPRESS_VERSION}'." \
      "package.json:n devDependencies.cypress pitää olla tarkka versio muodossa X.Y.Z (esim. 10.2.0), ei versioväli." >&2
   exit 1
fi

readonly NPM_CYPRESS_VERSION

# Note: "--" optiota käyttämällä voi passata suoraan docker build komennolle optioita
#   Tässä annetaan ulkopuolelta tulevat optiot scriptille parsittavaksi ja passataan lisäksi --build-arg komento
#   mitä cypress Dockerimage tarvitsee
source ../scripts/build-image.sh "$@" -- \
   --build-arg="NPM_CYPRESS_VERSION=${NPM_CYPRESS_VERSION}"
