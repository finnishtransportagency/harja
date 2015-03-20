#!/bin/bash

if [ "$CI_BUILD" == "" ]; then
    $HOME/node_modules/karma/bin/karma start "karma.conf.js"
fi
