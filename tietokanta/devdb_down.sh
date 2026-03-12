#!/bin/sh

echo "Tuhotaan harjadb Docker-kontti"
# Optiolla -f palautetaan exit-code 0 vaikka konttia ei ole olemassa
# Piilotetaan virheet
docker rm -f harjadb 2>/dev/null

echo "Tuhotaan harja-verkko"
# Optiolla -f palautetaan exit-code 0 vaikka verkkoa ei ole olemassa
# Stderr näytetään, koska siellä voi olla tärkeitä virheitä, esim. "network has active endpoints".
docker network rm -f harja-net
