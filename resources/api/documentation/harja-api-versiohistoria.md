<b>Nykyinen versio: 0.0.6</b>
<b>Julkaistu: 11.12.2015</b>

<b>Versiohistoria:</b>
- Versionumero: 0.0.7. Julkaistu: 27.1.2016
    - Siltatunnus lisätty siltatarkastuksiin
- Versionumero: 0.0.6. Julkaistu: 11.12.2015 . Muutokset:
    - Soratie-, talvihoito- ja tiestötarkastus rajapinnat on refaktoroitu vastaamaan uutta tietomallia
    - Vanha havainto rajapinta on muutettu laatupoikkeama rajapinnaksi
- Versionumero: 0.0.5. Julkaistu: 30.11.2015 . Muutokset:
    - Ilmoitusten haut siirretty urakoiden alle. Kaksi erillistä operaatiota ilmoitusten reaaliaikaiselle haulle ja massahaulle viimeisen id:n jälkeen.
    - Päivystäjätietojen haku toteutettu kolmena eri GET-palveluna: haku urakka id:llä, sijainnilla tai puhelinnumerolla
    - Toteumalle merkitty pakolliseksi alkamis- ja päättymisaika
    - Reittipisteelle merkitty pakolliseksi antaa aika
    - Totemien kirjaamisesta muutettu materiaalien ja tehtävien yksiköt enumin sijasta tekstiksi. Käytettävät arvot yksiköille & materiaaleille otetaan jatkossa urakan tietojen hausta.
    - Lisätty urakoiden haun vastaukseen kirjausyksiköt tehtäville & kaikki materiaalit
    - Lisätty uusi toteumatyyppi vahingonkorvaus reitti, piste & varustetoteumille
    - Tyokoneseurannasta poistettu sopimus id.
    - Varusterajapinta päivitetty vastaamaan tierekisterin rajapintaa
    - Urakoiden hakuun lisätty alueurakkanumero
    - Tarkennettu urakan haun vastausta. Kertoo nyt urakan sopimusten yksikkö- ja kokonaishintaiset tehtävät listassa, joita voidaan käyttää kirjaaman toteumia.
    - Kirjattu tarkennuksia API:n operaatioiden kuvauksiin.
    - Suunta lisätty työkone seurantaan. Suunta annetaan astelukuna.
    - Tietolajien haku varusteille päivitetty. Tietolajit haetaan yksi kerrallaan Tierekisteristä.
    - Poikkeamat poistetu rajapinnasta
    - Muutoksia varusterajapintaan
    - Haetaan vain yksi tietolaji kerrallaan
- Versionumero: 0.0.4. Julkaistu: 14.7.2015. Muutokset:
    - Toteumien materiaalien ja tehtävien määrää muutettu vastaamaan nykyistä tietomallia
- Versionumero: 0.0.3. Julkaistu: 8.7.2015. Muutokset:
    - Tiestö-, soratie- ja talvihoitotarkastusten payloadit yksinkertaistettu
- Versionumero: 0.0.2. Julkaistu: 10.6.2015. Muutokset:
    - Poistettu päivystäjätietojen haku
    - Lisätty tielupien haku
    - Lisätty tietyö ilmoituksen kirjaus
- Versionumero: 0.0.1. Julkaistu: 9.6.2015. Muutokset:
    - Ensimmäinen versio julkaistu