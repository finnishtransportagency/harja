## Cypress-image

Tämä on pohjaimage Cypress E2E testien ajamista varten.
Imageen on esiasennettu kaikki Cypressin tarvitsemat depsut, Nodejs ja itse Cypress,
Jos jokin E2E testi vaatii ylimääräisiä depsuja, ne asennetaan erikseen E2E-testit ajavassa jobissa.

Jos päivität imagea, julkaise ensin tagatty versio builda_test_docker_images workflowlla ja kokeile 
uutta versiota 'start-cypress'-actionissa hakemalla tagatty testiversio ja katso että PR:n testit menevät läpi.
Päivitä vasta sen jälkeen 'latest'-tag yllämainitulla workflowlla, niin muutos tulee käyttöön kaikkialla.

### Selainten päivitys Cypress-imageen
Suorita ohjeistuksen mukainen imagen build ja julkaisu workflowlla, jotta uusimmat versiot Chrome ja Edge selaimista
otetaan mukaan imageen.
Tätä on hyvä tehdä ajoittain, jotta testiympäristön selaimet pysyvät ajan tasalla.

Imageiden päivitys: [.github/docker/README.md](../README.md)
