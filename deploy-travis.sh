#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: $0 <envnumber> [branch]"
    exit 1
fi
ENV_NUMBER="$1"

shift

set -e

CURRENT_BRANCH=`git symbolic-ref --short HEAD`
BRANCH_NAME=${1:-$CURRENT_BRANCH}

echo "Haetaan branchin $BRANCH_NAME Travis-buildi testikoneelle $ENV_NUMBER"

set -x

ssh -t root@harja-dev$ENV_NUMBER.harjatest.solita.fi \
    "set -e; \
 systemctl stop harja; \
 sudo -u postgres psql -c \"SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harja' AND pid <> pg_backend_pid();\"
 sudo -u postgres psql -c 'drop database harja;'; \
 sudo -u postgres psql -c 'create database harja;'; \
 mkdir -p s3-tmp;  \
 rm -f harja-travis-$BRANCH_NAME.pgdump.gz harja-travis-$BRANCH_NAME.jar; \
 wget https://harjatravis.s3.amazonaws.com/harja-travis-$BRANCH_NAME.pgdump.gz; \
 wget https://harjatravis.s3.amazonaws.com/harja-travis-$BRANCH_NAME.jar; \
 cp harja-travis-$BRANCH_NAME.jar /opt/harja/harja-nightly.jar; \
 zcat harja-travis-$BRANCH_NAME.pgdump.gz | sudo -u postgres psql harja; \
 systemctl start harja"
