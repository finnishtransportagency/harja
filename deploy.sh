#!/bin/sh
if [ -z "$1" ]; then
    echo "Usage: $0 <test-env-number>"
    exit 1
fi

HARJA_ENV="harja-dev$1"

echo "Deploying to $HARJA_ENV"
echo "Please be patient!"

lein clean
lein tuotanto
tar czf tietokanta.tgz tietokanta
cd test_envs/upcloud
ansible-playbook deploy.yml -i inventory/harjadev --limit $HARJA_ENV

echo "Done!"
