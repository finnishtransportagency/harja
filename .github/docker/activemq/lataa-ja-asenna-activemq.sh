#!/bin/ash
set -euo pipefail

curl "https://archive.apache.org/dist/activemq/${ACTIVEMQ_VERSION}/${ACTIVEMQ}-bin.tar.gz" \
    -o "${ACTIVEMQ}-bin.tar.gz"

# Validate checksum
if [ "$ACTIVEMQ_SHA512" != "$(sha512sum "${ACTIVEMQ}-bin.tar.gz")" ]; then
    echo "$(sha512sum ${ACTIVEMQ}-bin.tar.gz)"
    echo "sha512 values doesn't match! exiting."
    exit 1
fi

tar xzf "${ACTIVEMQ}-bin.tar.gz" -C /opt
ln -s "/opt/${ACTIVEMQ}" "$ACTIVEMQ_HOME"
