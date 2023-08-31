#!/usr/bin/env bash

# Exit on any error or an unset variable (use optionally -x to print each command)
set -Eeu

echo "Pushataan image."
docker push ghcr.io/finnishtransportagency/harja_activemq:latest
echo "Build valmis."
