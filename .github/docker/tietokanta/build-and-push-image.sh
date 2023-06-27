#!/usr/bin/env bash

# Exit on any error or an unset variable (use optionally -x to print each command)
set -Eeu

echo "Buildataan ja tagataan image..."
    docker build -t ghcr.io/finnishtransportagency/harja_harjadb:latest .
echo "Build valmis."


echo "Pushataan image."
docker push ghcr.io/finnishtransportagency/harja_harjadb:latest
echo "Build valmis."
