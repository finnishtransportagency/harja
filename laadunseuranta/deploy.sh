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

CURRENT_BRANCH=`git symbolic-ref --short HEAD`
HIPCHAT_TOKEN=`cat ../../.harja/hipchat-token`
# HipChat notifikaatio
CONFIG="from=laadunseuranta/deploy.sh&color=purple"
MESSAGE="$USER deployasi juuri uuden Harja-laadunseurantatyökalun version haarasta $CURRENT_BRANCH palvelimelle <a href=\"https://$SERVER/harja/laadunseuranta/index.html\">$SERVER/harja/laadunseuranta/index.html</a>"
curl -d $CONFIG --data-urlencode "message=${MESSAGE}" "https://api.hipchat.com/v2/room/914801/notification?auth_token=$HIPCHAT_TOKEN&format=json"
