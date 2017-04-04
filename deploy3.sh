#!/bin/sh

CURRENT_BRANCH=`git branch|grep "*"|cut -c3-`
BRANCH=${1:-$CURRENT_BRANCH}

echo "Deployataan uusi AWS instanssi branchista $BRANCH"

aws ec2 run-instances --image-id ami-2d79a942 --key-name harja_upcloud_rsa --instance-type t2.medium > .deploy3

INSTANCE=`jq -r ".Instances[0].InstanceId" .deploy3`

echo "Instanssi luotu: $INSTANCE. Odotetaan, että sillä on julkinen verkko."


HOST=""

while [ -z "$HOST" ]; do
    HOST=`aws ec2 describe-instances --instance-ids $INSTANCE | jq -r ".Reservations[0].Instances[0].PublicDnsName"`;
    sleep 1;
done;

echo "Saatiin julkinen osoite: $HOST"

echo "Odotetaan, että portti 22 (SSH) vastaa"
while ! nc -z $HOST 22; do
    sleep 0.5;
done;

# Haetaan koneen fingerprint, ettei ssh kysy sen perään
echo "Haetaan koneen fingerprint: ssh-keyscan"
ssh-keyscan -t rsa $HOST > .deploy3_known_hosts

ssh -i ~/.ssh/harja_upcloud_rsa -o StrictHostKeyChecking=no -o UserKnownHostsFile=.deploy3_known_hosts -t centos@$HOST \
    "set -e; \
 echo \"Käynnistetään PostgreSQL 9.5\"; \
 sudo service postgresql-9.5 start; \
 sudo -u postgres psql -c 'drop database harja;'; \
 sudo -u postgres psql -c 'create database harja;'; \
 sudo mkdir -p s3-tmp;  \
 echo \"Haetaan Travis buildin jar ja pgdump\"; \
 sudo rm -f harja-travis-$BRANCH.pgdump.gz harja-travis-$BRANCH.jar; \
 sudo wget https://harjatravis.s3.amazonaws.com/harja-travis-$BRANCH.pgdump.gz; \
 sudo wget https://harjatravis.s3.amazonaws.com/harja-travis-$BRANCH.jar; \
 echo \"Ajetaan tietokanta dumpista\"; \
 sudo zcat harja-travis-$BRANCH.pgdump.gz | sudo -u postgres psql harja; \
 echo \"Käynnistetään nginx\"; \
 sudo service nginx start"

echo "Käynnistetään Harja branch $BRANCH"
ssh -i ~/.ssh/harja_upcloud_rsa -o StrictHostKeyChecking=no -o UserKnownHostsFile=.deploy3_known_hosts centos@$HOST "./harja.sh $BRANCH"


open https://$HOST/
