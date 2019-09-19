# CIRCLE-CI:n testaaminen paikallisesti #

Tässä pistetään paikallisesti pyörimään Harja yhdessä kontissa ja kanta toisessa kontissa <code>docker-compose</code>:n
avulla, jotta ympäristö olisi hyvin saman lainen kuin se on Circlessä.

<code>.circleci/docker-harja-testit/sisus.bash</code> suoritetaan kaikissa Circlen töissä. Tämä tiedosto kumminkin
referoi kantaan <code>localhost</code> kautta, joten kaikki ne viittaukset tulee ensin vaihtaa <code>possu</code>:ksi,
niinkuin tuo kanta on <code>docker-compose.yml</code> tiedostossa määritetty. Eli kannattaa vain kopioida
<code>.circleci/harja-testit-paikallisesti/sisus.bash</code> tuonne <code>.circleci/docker-harja-testit/</code> kansioon.

Jotta tuo Harjaa pyörittävä kontti jäisi itse asiassa pyörimään sammumisen sijasta, niin kannattaa kommentoida 
<code>ENTRYPOINT</code> ja <code>CMD</code> rivit pois.

Lopuksi buildataan <code>circleci-testi:latest</code> kontti ja ajetaan tuo docker-compose. Alla on step-by-step ohjeet.

## Tee näin ##

Tässä oletetaan, että olet Harja projektin rootissa, Docker on asennettu ja käytät jotain Linux shelliä

1. <code>cp .circleci/harja-testit-paikallisesti/sisus.bash .circleci/docker-harja-testit/</code>
2. - MAC:llä <code>sed -i '' -e 's/ENTRYPOINT/#ENTRYPOINT/g' .circleci/docker-harja-testit/Dockerfile</code>
   - Muu linux distro <code>sed -i -e 's/ENTRYPOINT/#ENTRYPOINT/g' .circleci/docker-harja-testit/Dockerfile</code>
3. - MAC:llä <code>sed -i '' -e 's/CMD/#CMD/g' .circleci/docker-harja-testit/Dockerfile</code>
   - Muu linux distro <code>sed -i -e 's/CMD/#CMD/g' .circleci/docker-harja-testit/Dockerfile</code>
4. <code>docker image build -t circleci-testi:latest .circleci/docker-harja-testit</code>
5. <code>cd .circleci/harja-testit-paikallisesti</code>
6. <code>docker-compose up -d</code>
7. <code>docker-compose run harja-kontti bash</code>
8. Nyt sinun pitäisi olla tuon harja-kontin sisällä. Eli seuraavat komennot siellä <code>cd /tmp</code>
9. <code>bash sisus.bash <komento-jota-haluat-testata> <Github haara, johon sitä haluat testata></code>
   Elikkä esim <code>bash sisus.bash test develop</code>