#!/usr/bin/env bash

# Exit, mikäli tulee virheitä
set -Ee

# Vaatii ympäristömuuttujat: IMAGE_REPO ja IMAGE_NAME
if [[ -z "$IMAGE_REPO" ]]; then
    echo "Ympäristömuuttujaa 'IMAGE_REPO' ei ole määritelty"
    exit 1
fi

if [[ -z "$IMAGE_NAME" ]]; then
    echo "Ympäristömuuttujaa 'IMAGE_NAME' ei ole määritelty"
    exit 1
fi

help() {
    echo "Käyttö: $0 [OPTIONS]"
    echo "  -t, --tag TAG    Valinnainen pushattava tag. "
    echo "  --update-latest  Päivitetään myös latest."
    echo "  -h, --help       Näytä tämä ohjeteksti."
    exit 1
}

# Valinnainen ylimääräinen tag.
TAG=""

# Pusketaanko latest
PUSH_LATEST="false"

# Parsi komentorivioptiot
while [[ "$#" -gt 0 ]]; do
    case "$1" in
    -t | --tag)
        TAG="$2"
        shift 2
        ;;
    --update-latest)
        PUSH_LATEST="true"
        shift 1
        ;;
    -h | --help)
        help
        ;;
    *)
        echo "Tuntematon optio: $1"
        help
        ;;
    esac
done


if [[ "$PUSH_LATEST" = "true" ]]; then
    echo "Pushataan ${IMAGE_NAME}:latest..."
    docker push "${IMAGE_REPO}/${IMAGE_NAME}:latest"
else
    echo "Ohitetaan ${IMAGE_NAME}:latest päivitys.."
fi

if [[ -n "$TAG" ]]; then
    echo "Pushataan ${IMAGE_NAME}:${TAG}..."
    docker push "${IMAGE_REPO}/${IMAGE_NAME}:${TAG}"
fi

echo "Push valmis."
