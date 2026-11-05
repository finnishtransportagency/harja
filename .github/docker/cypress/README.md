## Cypress-image

Tämä on pohjaimage Cypress E2E testien ajamista varten.
Imageen on esiasennettu kaikki Cypressin tarvitsemat depsut, Nodejs ja itse Cypress,
Jos jokin E2E testi vaatii ylimääräisiä depsuja, ne asennetaan erikseen E2E-testit ajavassa jobissa.

Cypressin binääripaketin purkamiseen asennetaan `unzip` virallisen Cypress-imagen tapaan.
Ilman sitä Cypress 10.2.0:n Node-pohjainen purku voi Node 26:lla päättyä kesken ilman virhekoodia,
jolloin `cypress verify` ilmoittaa binäärin puuttuvan.

Dockerfile on jaettu stageihin `base` (riippuvuudet ja Cypress), `chrome` (+ Chrome) ja `edge` (+ Edge).
Oletusbuildi tuottaa viimeisen stagen eli imagen, jossa on molemmat selaimet.
Pelkän Chromen sisältävän, pienemmän imagen voi buildata `--target chrome` -optiolla.

Build-workflow'n `zstd-compression`-optiolla imagen kerrokset pakataan zstd:llä gzipin sijaan.
Zstd-kerrokset puretaan runnerilla nopeammin, mikä voi lyhentää imagen pullausaikaa.
Paikallinen `build-image.sh` + `push-image.sh` tuottaa aina gzipattuja kerroksia.

Imagen viittaus ja kontin asetukset määritellään [docker-compose.yml](../docker-compose.yml)-tiedoston
`cypress-latest`-servicessä. `start-cypress`-action valitsee servicen `service-name`-inputilla,
hakee imagen komennolla `docker compose pull` ja ajaa testit komennolla `docker compose run --rm --no-deps`.  
Kertaluonteinen ajo välittää testien exit coden workflowlle ja poistaa kontin ajon jälkeen.  
Host-verkko säilyttää yhteyden runnerilla käynnistettyyn Harjaan.

Jos päivität imagea, julkaise ensin tagatty versio build_test_docker_images workflowlla ja kokeile
uutta versiota vaihtamalla imagen viittaus Compose-tiedostossa. Varmista, että PR:n testit menevät läpi.  
Päivitä vasta sen jälkeen 'latest'-tag yllämainitulla workflowlla, niin muutos tulee käyttöön kaikkialla.

### Selainten päivitys Cypress-imageen
Suorita ohjeistuksen mukainen imagen build ja julkaisu workflowlla, jotta uusimmat versiot Chrome ja Edge selaimista
otetaan mukaan imageen.
Tätä on hyvä tehdä ajoittain, jotta testiympäristön selaimet pysyvät ajan tasalla.

Imageiden päivitys: [.github/docker/README.md](../README.md)
