#!/bin/ash
set -euo pipefail
SHA512_VAL="35cae4258e38e47f9f81e785f547afc457fc331d2177bfc2391277ce24123be1196f10c670b61e30b43b7ab0db0628f3ff33f08660f235b7796d59ba922d444f  apache-activemq-5.15.9-bin.tar.gz"

curl "https://archive.apache.org/dist/activemq/${ACTIVEMQ_VERSION}/${ACTIVEMQ}-bin.tar.gz" -o "${ACTIVEMQ}-bin.tar.gz"

# Validate checksum
if [ "$SHA512_VAL" != "$( sha512sum "${ACTIVEMQ}-bin.tar.gz" )" ]
then
  echo "$( sha512sum ${ACTIVEMQ}-bin.tar.gz )"
  echo "sha512 values doesn't match! exiting."
  exit 1
fi

tar xzf "${ACTIVEMQ}-bin.tar.gz" -C  /opt
ln -s "/opt/${ACTIVEMQ}" "$ACTIVEMQ_HOME"
