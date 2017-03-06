#!/bin/sh

IMAGE=${1:-solita/harjadb}

./devdb_down.sh;
./devdb_up.sh $IMAGE;