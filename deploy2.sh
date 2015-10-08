#!/bin/sh

HARJA_ENV=harja-dev$1
BRANCH=$2

git push $HARJA_ENV $BRANCH

pushd test_envs/upcloud
ansible-playbook deploy2.yml -i inventory/harjadev --extra-vars "harja_migrate_only=false harja_branch=$BRANCH" --limit $HARJA_ENV
popd
