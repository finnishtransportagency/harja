#!/bin/bash

kill `lsof -n -i4TCP:3449 | awk 'NR>1{printf "%s", $2}'`
