#!/usr/bin/env bash

sh download_prod_dump.sh $1

sh mount_prod_dump.sh $1