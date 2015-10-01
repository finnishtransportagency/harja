#!/bin/sh

function msg {
    echo "**************************************************************"
    echo "$1"
}

function error_exit {
    echo "_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_"
    echo "$1"
    exit 1
}

if [ -z "$1" ]; then
    echo "Usage: $0 <test-env-number> [migrate_only]"
    exit 1
fi

HARJA_ENV="harja-dev$1"

START_TS=`date +%s`

msg "Deploying to $HARJA_ENV, please have a cup of hot coffee!"

if [ "$2" == "migrate_only" ]; then
    echo "Performing migrate only"
fi

lein clean || error_exit "clean failed"

msg "Building"

pushd vagrant
sh migrate_test.sh || error_exit "test migrate failed"
popd

lein tuotanto || error_exit "build failed"

msg "Packing migration scripts"

tar czf tietokanta.tgz tietokanta

msg "Running deploy playbook"

pushd test_envs/upcloud
if [ "$2" == "migrate_only" ]; then
    ansible-playbook deploy.yml -i inventory/harjadev --extra-vars "harja_migrate_only=true" --limit $HARJA_ENV || error_exit "Deploy failed"
else
    ansible-playbook deploy.yml -i inventory/harjadev --extra-vars "harja_migrate_only=false" --limit $HARJA_ENV || error_exit "Deploy failed"
fi
popd

rm tietokanta.tgz

END_TS=`date +%s`

msg "Done!"

echo "It all took `echo "$END_TS-$START_TS"|bc` seconds."
