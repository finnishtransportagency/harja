# Käyttö

Tämä on Harjan tietokanta CircleCI:tä varten. Perusta imagena on `solita/harjadb:postgis-3.1`.
Tämä ei eroa perus imagesta mitenkään, mutta sisältää muutamia skriptejä, joita CircleCI konffissa käytetään.

# Skriptit

Tässä on yksi oma skripti ja loput on hard linkkejä (`ln`) `tapahtumat` kansiosta.

#### dbn-kaynnistys.sh

Ajaa käytännössä `solita/harjadb:postgis-3.1` sisältämiä skriptejä, jotka käynnistää Possun ja ajaa sinne migraatiot
sekä testidatan.

## Imagen luonti ja päivitys dockerhubiin
* Aja `$ build-and-tag-docker-image.sh`
  * Image tagataan default-arvolla, jota käytetään suoraan circle-ci:ssä. 
    Mutta, jos haluat käyttää eri tagin nimeä, niin voit antaa sen argumenttina scriptille. 
* Kun build on valmis, niin voit ajaa `$ push-docker-image.sh`
    * Tämä script puskee halutun imagen dockerhubiin.
    * Default-arvolla tagattu image pusketaan ensisijaisesti, mutta jos haluat käyttää eri tagia, niin voit antaa sen
      argumenttina scriptille.
    * Varmista, että olet kirjautunut sisään dockeriin kehityskoneella ja että sinulla
      on riittävät oikeudet Dockerhubissa `solita/harjadb`-repositorioon.