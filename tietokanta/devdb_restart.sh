#!/usr/bin/env bash

IMAGE=${1:-solita/harjadb:centos-9.5}
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

$DIR/devdb_down.sh;
$DIR/devdb_up.sh $IMAGE;
