#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: $0 <envnumber> <branch>"
    exit 1
fi
envnumber="$1"
shift
if [ -z "$1" ]; then
    echo "Usage: $0 <env> <branch>"
    exit 1
fi
set -e
branchname="$1"
echo "Haetaan branchin $branchname Travis-buildi testikoneelle $envnumber"
set -x

ssh -t root@harja-dev$envnumber.harjatest.solita.fi \
    "set -e; id -a; \
 systemctl stop harja; \
 sudo -u postgres psql -c 'drop database harja;'; \
 sudo -u postgres psql -c 'create database harja;'; \
 mkdir -p s3-tmp;  \
 rm -f harja-travis-$branchname.pgdump.gz harja-travis-$branchname.jar; \
 wget https://harjatravis.s3.amazonaws.com/harja-travis-$branchname.pgdump.gz; \
 wget https://harjatravis.s3.amazonaws.com/harja-travis-$branchname.jar; \
 cp harja-travis-$branchname.jar /opt/harja/harja-nightly.jar; \
 zcat harja-travis-$branchname.pgdump.gz | sudo -u postgres psql harja; \
 systemctl start harja"
