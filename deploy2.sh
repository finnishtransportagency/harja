#!/bin/sh

function msg {
    echo "**************************************************************"
    echo "$1"
    echo "**************************************************************"
}

function error_exit {
    echo "_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_"
    echo "$1"
    exit 1
}

if [ -z "$1" ]; then
    echo "Usage: $0 <env> [unit-tests?(=true)] [branch]"
    exit 1
fi

set -e

START_TS=`date +%s`

CURRENT_BRANCH=`git symbolic-ref --short HEAD`
HARJA_ENV=harja-dev$1
BRANCH=$3
UNIT=$2

if [ -z "$BRANCH" ]; then
    BRANCH=$CURRENT_BRANCH
fi

echo ""
echo "Deployaan branchin $BRANCH ympäristöön $HARJA_ENV"

git push $HARJA_ENV $BRANCH || error_exit "Push epäonnistui, tarkista että remote on olemassa: git remote add $HARJA_ENV ssh://root@$HARJA_ENV/opt/harja-repo"

pushd test_envs/upcloud
ansible-playbook deploy2.yml -i inventory/harjadev --extra-vars "harja_migrate_only=false harja_branch=$BRANCH" --limit $HARJA_ENV || error_exit "Deploy epäonnistui"
popd



msg "Deploy valmis palvelimelle $HARJA_ENV. Laitoin Harja Projekti HipChat-kanavalle tiedon asiasta."

HIPCHAT_TOKEN=`cat ../.harja/hipchat-token`
# HipChat notifikaatio
CONFIG="from=deploy2.sh&color=purple"
MESSAGE="$USER deployasi juuri uuden Harja-version haarasta $BRANCH palvelimelle <a href=\"https://$HARJA_ENV.harjatest.solita.fi\">$HARJA_ENV.harjatest.solita.fi</a>"
curl -d $CONFIG --data-urlencode "message=${MESSAGE}" "https://api.hipchat.com/v2/room/914801/notification?auth_token=$HIPCHAT_TOKEN&format=json"

END_TS=`date +%s`
msg "Suorite kesti `echo "$END_TS-$START_TS"|bc` sekuntia."
