#!/usr/bin/env bash

# Hakee viimeisimmät migraatioiden versionumerot. Haetaan oletuksena develop ja nykyinen branch.
#
# esim.
# ./viimeisin_migraatio.sh
# Viimeisimmät migraatiot:
# develop
# 892
# VHAR-4778
# 890
#
# ./viimeisin_migraatio.sh develop other-branch
# develop
# 892
# other-branch
# 892

migraatiot () {  
    local branch="${1}"
    local regex="V1_([0-9]+)__.*"
    for f in $(git ls-tree --name-only -r origin/${branch})
    do
        if [[ $f =~ $regex ]]
        then
            versio="${BASH_REMATCH[1]}"
            echo "${versio}"
        fi

    done
}

viimeisin_migraatio () {
    local branch="${1}"
    git fetch origin $branch --quiet
    migraatiot $branch | sort -n | tail -n 1 
}


branch_a="${1:-develop}"
branch_b="${1:-$(git branch --show-current)}"

versio_a=$(viimeisin_migraatio $branch_a)
versio_b=$(viimeisin_migraatio $branch_b)

echo "Viimeisimmät migraatiot:"
echo $branch_a
echo $versio_a

echo $branch_b
echo $versio_b
