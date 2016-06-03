#!/bin/bash

if [ "$1" == "" ]; then
    echo "Anna palvelimen numero!"
    exit 1
fi

SERVER=harja-dev$1

lein tuotanto
pushd playbooks
ansible-playbook deploy.yml -i inventory/harjadev --limit $SERVER
popd
