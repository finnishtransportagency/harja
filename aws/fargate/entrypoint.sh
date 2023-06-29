#!/usr/bin/env bash

set -x

# TODO: Lataa kontin tarvitsemat tiedostot S3 bucketista aws cli s3 työkalulla

# Luodaan java-optioille array, johon otetaan mukaan lisäoptioita mikäli ne on määritelty
cmd_opts=()

if [[ -n "$HARJA_JAVA_AGENT" ]]; then
  cmd_opts+=("$HARJA_JAVA_AGENT")
fi

if [[ -n "$HARJA_JVM_OPTS" ]]; then
  cmd_opts+=("$HARJA_JVM_OPTS")
fi

cmd_opts+=("-cp" "$HARJA_LIBS":harja.jar harja.palvelin.main)

# Aja java-komento
java "${cmd_opts[@]}"
