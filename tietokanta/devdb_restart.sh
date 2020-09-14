#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

$DIR/devdb_down.sh;
$DIR/devdb_up.sh;
