#!/bin/sh

cd vagrant
if [ -z $1 ]; then
  echo "Ajetaan vagrant reload. Voit ajaa skriptin antamalla jotain parametriksi, jolloin ajetaan vagrant reload --provision."
  echo "Voit käyttää myös aloita.sh, joka ajaa vain migraatiot & lein do clean, repl"
  vagrant reload
else
  echo "Ajetaan vagrant reload --provision."
    echo "Voit käyttää myös aloita.sh, joka ajaa vain migraatiot & lein do clean, repl"
  vagrant reload --provision
fi

cd ..

echo "Ajetaan aloita.sh"
sh aloita.sh
