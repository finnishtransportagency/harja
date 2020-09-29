#!/bin/bash
set -euo pipefail

if [[ "$OSTYPE" == "darwin"* ]]
then
    # Hostina toimii Mac OSX
    OMA_OS_TYPE='OSX'
else
    OMA_OS_TYPE=''
fi

os_sed_inplace() {
    if [[ ${OMA_OS_TYPE} == 'OSX' ]]
    then
        sed -i '' $@
    else
        sed -i $@
    fi
}
