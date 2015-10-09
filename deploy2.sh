#!/bin/sh

function error_exit {
    echo "_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_"
    echo "$1"
    exit 1
}

if [ -z "$1" ]; then
    echo "Usage: $0 <env> [branch]"
    exit 1
fi

CURRENT_BRANCH=`git symbolic-ref --short HEAD`
HARJA_ENV=harja-dev$1
BRANCH=$2

if [ -z "$BRANCH" ]; then
    BRANCH=$CURRENT_BRANCH
fi

echo "Deployaan branchin $BRANCH ympäristöön $HARJA_ENV"

git push $HARJA_ENV $BRANCH || error_exit "Push epäonnistui"

pushd test_envs/upcloud
ansible-playbook deploy2.yml -i inventory/harjadev --extra-vars "harja_migrate_only=false harja_branch=$BRANCH" --limit $HARJA_ENV || error_exit "Deploy epäonnistui"
popd
