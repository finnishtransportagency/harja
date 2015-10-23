#!/bin/sh

echo "Skripti ajaa vagrant reload (--provision). Sinun ei välttämättä tarvitse tehdä tätä. Käytä ehkä mieluummin aloita.sh"

cd vagrant
if [ -z $1 ]; then
  vagrant reload
else
  vagrant reload --provision
fi

cd ..

sh aloita.sh
