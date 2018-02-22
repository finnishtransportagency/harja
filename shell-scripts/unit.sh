#!/bin/bash

function fail {
    if [ -z "$1" ];
    then
        echo " ---- "
        echo "Voit avata raportin kommennolla: sh avaaunit.sh";
    else
        echo " ---- "
        echo "Raportti avattu selainikkunaan."
        sh avaaunit.sh
    fi

    exit 1
}

function eiajoa {
    echo "Koodissa taitaa olla jotain häikkää! test2junit/xml/ kansio on tyhjä, eli testejä ei saatu ajettua ollenkaan."
    echo "Kokeile ajaa lein test nähdäksesi tarkemmat tulokset?"
    exit 1
}

rm -r test2junit/xml/ 2>/dev/null

AVAARAPORTTI=$1

if [ -z "$AVAARAPORTTI" ];
then
  echo "Ajetaan unit-testit."
  echo "Voit avata raportin automaattisesti antamalla ensimmäisenä parametrina jotain."
else
  echo "Ajetaan unit-testit. Jos virheitä löytyy, aukaistaan raportti selaimeen automaattisesti."
fi

echo " ---- "
echo "Aloitetaan ajo.."
tulos="$(lein test2junit 2> /dev/null)"
status=$?

echo "Testit ajettu. Analysoidaan tulokset."
echo "Step: 1/2.."

terminaloutput="$(for i in test2junit/xml/*.xml;
do xpath $i "/testsuite[not(@errors = '0') or not(@failures = '0')]" 2>&1 | grep -v "No nodes found";
done;)"


echo "Step: 2/2.."
notificationoutput=`find test2junit -name "*.xml" | xargs xmllint --xpath "string(/testsuite[not(@errors = '0') or not(@failures = '0')]/@name)"`

echo "Done!"
echo " ---- "

# Jos testien output kansio on tyhjä, testien ajossa oli jotain häikkää.
test "$(ls -A "test2junit/xml/" 2>/dev/null)" || eiajoa

NOTIFIER="$(which terminal-notifier)"

if [ -n "$NOTIFIER" ];
then
    if [ -n "$notificationoutput" ];
    then
        terminal-notifier -title "Harjan yksikkötesteissä virheitä!" -message "$notificationoutput" -open "file:///`pwd`/test2junit/html/index.html"
    else
        terminal-notifier -title "Harjan yksikkötestit ajettu onnistuneesti" -message "Kaikki testit ajettu onnistuneesti." -open "file:///`pwd`/test2junit/html/index.html"
    fi
fi

# lein2junit raportoi lopussa mm. montako testiä ajettiin, ja lopussa raportoidaan antilla HTML raportti.
# Me halutaan ainoastaan ne pari riviä, joilla lukee montako testiä ajettiin..
if [ -z "$terminaloutput" ];
then
  echo "$tulos" | tail -15 | head -n 2; # Montako testiä / Montako virhettä
else
  echo "$terminaloutput"
  echo ""
  echo " ---- "
  echo "$tulos" | tail -16 | head -n 2 # Montako testiä / Montako virhettä
  fail $AVAARAPORTTI
fi


# Fallback, jos testit exittas virheviestillä, niin ei me voida sitä hyväksyä mitenkään.
if [ $status -ne 0 ];
then
    echo "Kaikki muut tarkastukset menivät muka läpi, mutta lein test2junit valmistui muulla koodilla kuin 0."
    echo "Joku ongelma siellä siis pitäisi olla. Aja lein test?"
    exit 1
fi