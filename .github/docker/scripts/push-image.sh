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
  echo "  -t, --tag TAG   Valinnainen pushattava tag. Tämän lisäksi 'latest' tag pushataan aina."
  echo "  -h, --help      Näytä tämä ohjeteksti."
  exit 1
}

# Valinnainen ylimääräinen tag. "Latest" tag pusketaan aina.
TAG=""

# Parsi komentorivioptiot
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -t|--tag)
      TAG="$2"
      shift 2
      ;;
    -h|--help)
      help
      ;;
    *)
      echo "Tuntematon optio: $1"
      help
      ;;
  esac
done


echo "Pushataan ${IMAGE_NAME}:latest..."

docker push "${IMAGE_REPO}/${IMAGE_NAME}:latest"

if [[ -n "$TAG" ]]; then
  echo "Pushataan ${IMAGE_NAME}:${TAG}..."
  docker push "${IMAGE_REPO}/${IMAGE_NAME}:${TAG}"
fi

echo "Push valmis."
