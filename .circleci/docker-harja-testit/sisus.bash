#!/usr/bin/env bash

set -e
set -x
set -u
export TZ=EET
cd /tmp
cd harja
# git pull origin develop
function cmd_phantom
{
    lein with-profile +test doo phantom test once
}

function cmd_test
{
    lein clean
    lein test
}
function cmd_test+phantom
{
    lein clean
    lein test
    lein with-profile +test doo phantom test once
}

function cmd_back
{
    lein tuotanto-notest
    java -jar target/harja-0.0.1-SNAPSHOT-standalone.jar
}

function cmd_uberjar
{
    lein do clean, compile
    echo "--> aot compile done"
    lein less once
    echo "--> less compile done"
    lein with-profile prod-cljs compile-prod
    echo "--> cljs main compile done"
    lein with-profile laadunseuranta-prod compile-laadunseuranta-prod
    echo "--> cljs laadunseuranta compile done"
    lein uberjar
    echo "--> uberjar done"
}

function cmd_help {
    echo komennot: phantom, test, test+phantom, back, help
}

function cmd_integraatio
{
    lein clean
    lein test :integraatio
}

function cmd_hidas
{
    lein clean
    lein test :hidas
}

SUBCMD="$1"
shift
BRANCH="$1"
shift
ARGS="$@"

git fetch origin
pwd
rm -vf asetukset.edn
git checkout -b "t_$BRANCH" "origin/$BRANCH"
git checkout asetukset.edn

sed -i -e 's/:jvm-opts \^:replace \["-Xms256m" "-Xmx2g"\]/:jvm-opts \^:replace \["-Xms256m" "-Xmx900m"\]/g' project.clj

eval "cmd_$SUBCMD"
