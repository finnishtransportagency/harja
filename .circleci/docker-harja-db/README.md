# Käyttö

Tämä on Harjan tietokanta CircleCI:tä varten. Perusta imagena on `solita/harjadb:centos-12`.
Tämä ei eroa perus imagesta mitenkään, mutta sisältää muutamia skriptejä, joita CircleCI konffissa käytetään.

# Skriptit

Tässä on yksi oma skripti ja loput on hard linkkejä (`ln`) `tapahtumat` kansiosta.

#### dbn-kaynnistys.sh

Ajaa käytännössä `solita/harjadb:centos-12` sisältämiä skriptejä, jotka käynnistää Possun ja ajaa sinne migraatiot
sekä testidatan.