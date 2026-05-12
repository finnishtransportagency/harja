# Harjan mini-framework

Tämä dokumentti kuvaa Bootstrap-poiston mini-frameworkin ensimmäiset hyväksytyt primitive-rajaukset. Tarkoitus ei ole dokumentoida kaikkea UI:ta, vaan näyttää turvallinen tapa lisätä uutta ja siirtää vanhaa vaiheittain pois Bootstrap-riippuvuuksista.

Dokumentti on samalla mini-frameworkin käytännön ohjeistus. Uutta rinnakkaista nimeämistapaa ei tuoda. Käytössä on yksi linja: suomenkielinen, litteä ja viivapohjainen nimeäminen.

## Tavoite

- shared-komponentit kapseloivat vendor-luokat pois näkymistä
- uudet primitive-luokat ovat Harjan omistamia ja nimettyjä
- siirtymä tehdään primitive kerrallaan, ei näkymä kerrallaan

## Mikä kuuluu Väylälle, mikä mini-frameworkille ja mikä ei kuulu kumpaankaan

Käytä tätä rajaa aina ennen kuin lisäät uuden shared-tyylin, wrapperin tai primitiven.

### Väylä = visuaalinen perusta

- Väylä omistaa värit, typografian, spacing-rytmin ja muun yleisen HTML- tai design-tason perustyylin.
- Väylä omistaa input-, select- ja textarea-perustyylin, checkbox- ja radio-perustyylin, taulukon perustyylin sekä linkkien, nappien ja pintojen peruslookin.
- Väylä ei omista Harjan shared-komponenttien rakennetta, API:a tai käyttäytymistä.

### Harjan mini-framework = Harjan shared-UI-primitivet ja yhteiset käyttötavat

- Mini-frameworkiin kuuluvat viesti, modali, kortti tai panel-korvike, välilehdet, dropdown tai valikkolista, lomakekenttä-wrapperi ja shared layout-helperit silloin kun niillä on yhteinen API.
- Mini-frameworkiin kuuluvat myös validointitilojen shared esitystapa ja vakaat `data-cy`-käytännöt shared-komponenteissa.
- Mini-framework käyttää Väylän visuaalista perustaa, mutta omistaa Harjan toistuvan rakenteen, API:n ja käyttäytymisen.

### Näkymäkohtainen kerros = domain- tai sivukohtaiset poikkeukset

- Jos ratkaisu tarvitaan vain yhdellä sivulla tai yhdessä domain-kontekstissa, sitä ei nosteta mini-frameworkiin.
- Näkymäkohtainen kerros saa sovittaa shared-primitivejä paikalliseen tarpeeseen, mutta ei saa luoda uutta yleistä primitiveä vahingossa.

### Siirtymäkerros = vain väliaikainen Bootstrap-yhteensopivuus

- Siirtymäkerrokseen kuuluvat vain alias-, adapteri- ja yhteensopivuussäännöt, joita tarvitaan vanhan Bootstrap-markupin pitämiseksi hengissä migraation ajan.
- Siirtymäkerros ei ole pysyvä primitive-kerros eikä oikea paikka uudelle shared-API:lle.

## Päätössäännöt kehittäjälle

- Jos asia on yleinen HTML- tai design-tason perustyyli ilman Harjan omaa käyttölogiikkaa, vie se Väylä-pohjaan.
- Jos asia on Harjassa toistuva shared-käyttötapa, jolla on oma rakenne, API tai käyttäytyminen, vie se mini-frameworkiin.
- Jos asia on vain yhden näkymän tai domainin poikkeus, jätä se näkymäkohtaiseen kerrokseen.
- Jos asia on olemassa vain Bootstrap-markupin siirtymävaihetta varten, vie se siirtymäkerrokseen.

### Lyhyt päätöspuu

1. Onko kyse vain yleisestä HTML- tai design-tason perustyylistä ilman Harjan omaa käyttölogiikkaa?
	- Jos on, ratkaisu kuuluu Väylälle.
2. Onko kyse toistuvasta Harjan shared-käyttötavasta, jolla on oma rakenne, API tai käyttäytyminen?
	- Jos on, ratkaisu kuuluu mini-frameworkiin.
3. Onko kyse vain yhden näkymän tai domainin poikkeuksesta?
	- Jos on, ratkaisu kuuluu näkymäkohtaiseen kerrokseen.
4. Onko ratkaisu olemassa vain Bootstrap-markupin yhteensopivuuden takia?
	- Jos on, ratkaisu kuuluu siirtymäkerrokseen ja sille pitää olla poistopolku.

## Nimeämisen peruslinja

- käytä yhtä suomenkielistä viivapohjaista nimeämistä koko mini-frameworkissa
- suosi litteitä nimiä, jotka kertovat tarkoituksen ilman erillistä elementti- tai modifier-syntaksia
- älä ota käyttöön BEM-tyyppisiä `__`- tai `--`-rakenteita uutena käytäntönä
- pidä sama sanasto yhtenäisenä tyyleissä, komponenteissa, namespaceissa ja testiselektoreissa

Hybridimallin rakenne jäsennetään näihin osiin:

- `harja_tyylit`
- `muuttujat`
- `perusta`
- `primitiivit`
- `siirtyma`
- `apuluokat`
- `koonti.less`

## Ensimmäinen primitive-ryhmä: flash-viesti

Omistava toteutus:

- namespace: `harja.ui.viesti`
- shared primitive-mäppäys: `harja.ui.primitiivit.viesti`
- tyylit: `dev-resources/less/harja_tyylit/primitiivit/viesti.less`

Hyväksytty käyttötapa:

- näytä palautteen overlay edelleen funktion `harja.ui.viesti/nayta!` kautta
- primitive renderöi Harjan omat luokat `harja-viesti-overlay`, `harja-viesti-tausta`, `harja-viesti` ja variantin `harja-viesti-<tyyppi>`
- testauksessa ja E2E-polussa suosi `data-cy`-attribuutteja `flash-viesti`, `flash-viesti-overlay` ja `flash-viesti-tausta`

Ei enää suositella tässä primitive-perheessä:

- Bootstrapin `alert`, `alert-success`, `alert-info`, `alert-warning`, `alert-danger`
- Bootstrapin `modal` ja `modal-backdrop` flash-viestin overlayn toteutuksessa

## Variantit

- `:success` -> `harja-viesti-onnistuminen`
- `:info` -> `harja-viesti-info`
- `:warning` -> `harja-viesti-varoitus`
- `:danger` -> `harja-viesti-vaara`

Tuntematon luokka putoaa `info`-varianttiin.

## Nimeämissäännöt

### CSS-luokat

- käytä suomenkielisiä, viivalla erotettuja nimiä, esimerkiksi `harja-viesti-tausta`
- pidä nimi litteänä: yksi nimi kertoo roolin ilman erillistä elementti- tai modifier-syntaksia
- käytä yhteistä etuliitettä, kun luokka kuuluu samaan primitive-perheeseen, esimerkiksi `harja-viesti-*`
- vältä nimiä kuten `harja-viesti-overlay__tausta` ja `harja-viesti--varoitus`, koska ne tuovat rinnakkaisen BEM-tyylin

### LESS-tiedostot ja hakemistot

- käytä suomenkielisiä nimiä myös tiedostoissa ja hakemistoissa
- noudata hybridimallin rakennetta: `harja_tyylit`, `muuttujat`, `perusta`, `primitiivit`, `siirtyma`, `apuluokat`
- nimeä primitive-tiedostot aiheen mukaan, esimerkiksi `viesti.less`, ei rakennesyntaksin mukaan kuten `viesti__overlay.less`
- kokoa tyylit `koonti.less`-tiedoston kautta

### Clojure namespacejen apurakenteet

- käytä olemassa olevaa Harjan namespace-linjaa ja pidä nimet suomenkielisinä siellä missä domain-termit ovat suomeksi
- suosi selkeitä apunimiä kuten `harja.ui.viesti` ja `harja.ui.primitiivit.viesti`
- kun primitivejä on useampi kuin yksi, ryhmittele niiden shared-logiikka yhteisen `harja.ui.primitiivit`-juuren alle
- älä rakenna CSS-nimeämistä namespaceen BEM-logiikalla tai uusilla rinnakkaisilla sanastoilla

### `data-cy`-attribuutit

- käytä samoja suomenkielisiä, viivapohjaisia nimiä kuin käyttöliittymän käsitteissä
- nimeä attribuutti sen mukaan mitä käyttäjäpolussa tunnistetaan, esimerkiksi `flash-viesti`, `flash-viesti-overlay`, `flash-viesti-tausta`
- pidä nimi vakaana ja vältä rakenteeseen sidottuja BEM-muotoja

## Päätössääntö

Nimi on hyvä, kun se on suomenkielinen, viivapohjainen, litteä ja kertoo suoraan roolin tai tyypin.

Nimi rikkoo linjaa, jos se tuo rinnalle BEM-tyyppisen `__`- tai `--`-syntaksin, sekoittaa kieliä ilman syytä tai hajottaa saman primitive-perheen useaan eri nimeämistapaan.

## Ennen ja jälkeen

Ennen:

- flash-viesti nojasi Bootstrapin `modal`, `modal-backdrop`, `alert` ja `alert-*` -luokkiin

Jälkeen:

- `harja.ui.viesti` renderöi vain Harjan omat primitive-luokat
- Bootstrap-riippuvuus ei enää vuoda flash-viestin markupiin
- siirtymä ei vaatinut näkymäkohtaisia markup-muutoksia, koska public API pysyi samana

## Rajaus

Tämä ensimmäinen viipale ei muuta:

- toast-viestejä
- modaaleja
- gridiä
- lomakeprimitiviä

Seuraava luonteva primitive samasta kerroksesta on `harja.ui.modal`, koska se käyttää yhä Bootstrapin modal-rakennetta vaikka toiminnallisuus on jo oma.

## Kehityssivu primitiiveille

Ensimmäinen UI-komponenttien manuaaliseen tarkasteluun tarkoitettu kehityssivu löytyy Hallinta > Työkalut > UI-komponenttien tarkastelu.

Teknisesti näkymä on nimetty samansuuntaisesti koko pinossa, jotta näkyvä nimi, näkymän avain, tiedostonimi ja tyylit käyttävät samaa käsitettä `ui-komponenttien-tarkastelu`.

Ensimmäinen versio näyttää vain viesti-primitiven perusvariantit:

- onnistuminen
- tieto
- varoitus

Sivu on tarkoitettu laajennettavaksi niin, että myöhemmin samaan rakenteeseen voidaan lisätä esimerkiksi modal-, kortti- ja muut component- tai primitive-ryhmät ilman erillistä uutta dokumentaatiosivua.
