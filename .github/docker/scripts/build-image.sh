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
  echo "  -t, --tag TAG                   Valinnainen ylimääräinen tag. 'Latest' buildataan aina."
  echo "  -c, --clean-build [true|false]  Buildaa ilman cachea (default: true)."
  echo "  -p, --progress [auto|plain]     Määrittele build progress lokituksen muoto (default: plain)."
  echo "  -h, --help                      Näytä tämä ohjeteksti."
  exit 1
}

TAG=""
CLEAN_BUILD="true"
PROGRESS="plain"

# Parsi komentorivioptiot
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -t|--tag)
      TAG="$2"
      echo "Buildataan tag: ${TAG}"
      shift 2
      ;;
    -c|--clean-build)
      CLEAN_BUILD="$2"
      shift 2
      ;;
    -p|--progress)
      PROGRESS="$2"
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

echo "Clean build?: ${CLEAN_BUILD}"

cmd_opts=()

if [[ "$CLEAN_BUILD" = "true" ]]; then
  cmd_opts+=("--no-cache")
fi

cmd_opts+=("--progress=${PROGRESS}")

if [[ -n "$TAG" ]]; then
  cmd_opts+=("-t" "ghcr.io/finnishtransportagency/${IMAGE_NAME}:${TAG}")
fi


echo "Buildataan ja tagataan image..."
    docker build \
    -t "ghcr.io/finnishtransportagency/${IMAGE_NAME}:latest" \
    "${cmd_opts[@]}" .
echo "Build valmis."
