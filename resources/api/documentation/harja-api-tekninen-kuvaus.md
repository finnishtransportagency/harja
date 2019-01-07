Harja -järjestelmän rajapinta on toteutettu REST-API:na, jossa tieto liikkuu JSON-formaatissa. 

Tietoliikenne Harjan palvelimille kulkee Väylän keskitetyn gatewayn kautta (Oracle Enterprice Gateway). Siirtoprotokollana käytetään HTTPS. Käyttäjän tunnistaminen tehdään käyttäen HTTP Basic autentikaatiota tunnus-salasana -parina. Väylä hallinnoi järjestelmä-/urakoitsijakohtaisia tunnuksia.

Kutsut voi lähettää gzipillä pakattuina merkitsemällä content encodingin gzipiksi.

Huomioitavaa:
<ul>
  <li>Kaikki ajat raportoidaan Suomen aikavyöhykkeessä GMT+2/3. Aikaleimat täytyy siis raportoida formaatissa: "2016-11-03T03:43:14+02:00"</li>
  <li>Kaikki toteumien tehtävien ja materiaalien määrät raportoidaan Harjan urakkahaun palauttamassa yksikössä. Harja ei tee muunnoksia raportoiduille yksiköille.</li>
</ul>