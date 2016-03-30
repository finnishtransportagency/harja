#!/bin/bash


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

echo "Testit ajettu. Analysoidaan tulokset."
echo "Step: 1/2.."

terminaloutput="$(for i in test2junit/xml/*.xml;
do xpath $i "/testsuite[not(@errors = '0') or not(@failures = '0')]" 2>&1 | grep -v "No nodes found";
done;)"

echo "Step: 2/2.."

notificationoutput=`find test2junit -name "*.xml" | xargs xmllint --xpath "string(/testsuite[not(@errors = '0') or not(@failures = '0')]/@name)"`
if [ -n "$notificationoutput" ];

echo "Done!"
echo " ---- "

# lein2junit raportoi lopussa mm. montako testiä ajettiin, ja lopussa raportoidaan antilla HTML raportti.
# Me halutaan ainoastaan ne pari riviä, joilla lukee montako testiä ajettiin..
if [ -z "$terminaloutput" ];
then
  echo "$tulos" | tail -16 | head -n 2;
else
  echo "$terminaloutput"
  echo ""
  echo " ---- "
  echo "$tulos" | tail -16 | head -n 2
fi

echo " ---- "

then
  terminal-notifier -title "Harjan yksikkötesteissä virheitä!" -message "$notificationoutput" -open "file:///`pwd`/test2junit/html/index.html"

  if [ -z "$AVAARAPORTTI" ];
  then
    echo "Voit avata raportin kommennolla: sh avaaunit.sh";
  else
    echo "Raportti avattu selainikkunaan."
    sh avaaunit.sh
  fi

  exit 1
else
  terminal-notifier -title "Harjan yksikkötestit ajettu onnistuneesti" -message "Kaikki testit ajettu onnistuneesti." -open "file:///`pwd`/test2junit/html/index.html"
fi