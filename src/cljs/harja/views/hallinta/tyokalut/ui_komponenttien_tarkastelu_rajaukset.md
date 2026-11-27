# UI-komponenttien tarkastelun rajaus

Tämä dokumentti on tarkastelusivun review-pinta. Arvioi muutoksia ensin tämän rajauksen kautta.

## Tavoite

Rakentaa Harjan uutta UI:ta shared-komponenttien avulla niin, että:

- näkymä omistaa domain-datan, tilan, haut, oikeudet ja callbackien päätökset
- shared-komponentti omistaa DOM-rakenteen, saavutettavuuden, vuorovaikutuksen ja vakaat `data-cy`-ankkurit
- uusi UI ei lisää suoraa Bootstrap-markupia eikä näkymäkohtaista yleis-CSS:ää
- komponentin public API näkyy tarkastelusivulla ennen tuotantokäyttöpaikkaa

## Aloita tästä

Tämä tiedosto on uuden UI:n käyttöönoton kanoninen ohje. Rekisterin `:suositus`
kertoo, voiko komponenttia valita uuteen näkymään: `:uuteen-ui` on sallittu
lähtökohta, `:ei-suositusta` vaatii perustellun poikkeuksen ja `:vanha` ei ole
uuden näkymän oletus.

| Käyttötapaus | Aloita rakenteesta |
| --- | --- |
| Sivu | `sailio` → `osio` → `kortti`, `pino`, `vaaka` tai `ruudukko` |
| Lomake | `lomake` → `kenttaryhma` → rekisteröity syöte → `toimintoryhma` |
| Suodatus ja listaus | `suodatinrivi` → suodatin/syötteet → `perusgrid` |
| Tietosisältö | `kortti` + `tietolista`; älä aloita `panel`- tai `tietoja`-komponentista |
| Palaute | `tilaviesti` pienen prosessin tilaan; flash viestille toteutuksen mukainen `viesti`-API |

Tee ensin rekisterihaku (`lein komponenttirekisteri :id <id>`) ja käytä
olemassa olevaa esimerkkisivua lähtökohtana. Jos komponentti tai yhdistelmä
puuttuu, lisää sen sopimus ennen käyttöpaikkaa; älä kääri puutetta
näkymäkohtaisella CSS:llä.

### v1:n takuu

- v1 takaa rekisteröidyn komponenttisopimuksen, API:n, DOM-ankkurit ja
  validointipolun näkyville uusille käyttöpaikoille.
- v1 ei takaa kaikkien tuotantokomponenttien Bootstrapin poistamista,
  kaikkien legacy-komponenttien porttausta tai Livin tavoiteltua
  saavutettavuussopimusta.
- Olemassa olevaa legacy-käyttöpaikkaa ei muuteta, ellei ticketissä ole erillistä
  porttaus- tai migraatioviipaletta.

## Rakenne

| Osa | Sijainti |
| --- | --- |
| Shared-komponentit (`pino`, `vaaka`, `sailio`, `osio`, `ruudukko`, `suodatinrivi`, `suodatin`, `kenttaryhma`, `toimintoryhma`, `tilaviesti`) | `src/cljc/harja/ui/perus.cljc` |
| Komponenttirekisteri (data) ja eheystarkistus | `src/cljc/harja/ui/perus/rekisteri.cljc` |
| Rekisterin EDN-tulostus komentoriviltä | `src/clj/harja/tyokalut/komponenttirekisteri.clj` (`lein komponenttirekisteri`) |
| Tarkastelusivun entry | `tyokalut/ui_komponenttien_tarkastelu_nakyma.cljs` |
| Tarkastelurunko (navigaatio, kortti, sopimus, `ankkuri`) | `tyokalut/ui_komponenttien_tarkastelu/kehys.cljs` |
| Esikatselut ryhmittäin | `tyokalut/ui_komponenttien_tarkastelu/{alasvedot,toiminnot,lomakkeet,gridit,palautteet,portatut,simulaattori}.cljs` |
| Esikatselujen rekisteröinti | `tyokalut/ui_komponenttien_tarkastelu/rekisterointi.cljs` |
| Esimerkkisivut (EDN, simulaattorin valinta, ks. alla) | `src/cljc/harja/ui/perus/esimerkkisivut/*.edn`, lataus `src/cljc/harja/ui/perus/esimerkkisivut.cljc` |
| Sopimus- ja rekisteritestit (`lein test`) | `test/cljc/harja/ui/perus_test.cljc`, `test/cljc/harja/ui/perus/rekisteri_test.cljc`, `test/clj/harja/ui/perus/esikatselu_test.clj`, `test/clj/harja/ui/perus/bootstrap_luokat_test.clj`, `test/clj/harja/ui/perus/valit_test.clj` |
| Tyylit | `dev-resources/less/komponentit.less`, `dev-resources/less/pages/ui_komponenttien_tarkastelu.less` |

Uuden komponentin lisääminen:

1. Lisää rekisterimerkintä `rekisteri.cljc`:ään (`:id`, `:ryhma`, `:nimi`, `:kuvaus`, `:funktio`, `:asetukset`, `:bootstrap-tila`).
2. Lisää esikatselu ryhmän tiedostoon `register!`-kutsulla rekisterin `:id`:llä. Jokainen id esiintyy `(register! :id `-muodossa täsmälleen kerran `src/cljs`-hakemistossa. Kaksi rekisteröintiä samalla id:llä on virhe.
3. Lisää sopimustesti, jos komponentti on shared-komponentti.
4. `rekisteri/virheet`-testi epäonnistuu, jos rekisterin tiedot ovat puutteelliset.

## Rekisterin tulostus

`lein komponenttirekisteri` tulostaa EDN-kartan `{:komponentit [...] :esimerkit [...]}` stdoutiin ilman appia, tietokantaa tai selainta. Suodattimet annetaan avain-arvo-pareina: `lein komponenttirekisteri :id nappi` tai `lein komponenttirekisteri :ryhma lomakkeet`. Suodatin kohdistuu komponentteihin; `:esimerkit` sisältää koko katalogin. Komponentit sisältävät rekisterimerkinnän kaikki avaimet (mm. `:funktio`, `:asetukset`, `:simulaattori-asetukset`, `:suositus`, `:korvaa`, `:variantit`, `:bootstrap-luokat`) ja lisäksi `:ankkuri`. Funktioarvot jätetään pois. Tuntematon suodatin, pariton argumenttimäärä sekä tuntematon `:id` tai `:ryhma` tulostavat virheen stderriin ja päättyvät koodilla 1.

## Agenttityönkulku

1. Aja `lein komponenttirekisteri` ja lue komponentit sekä `:esimerkit`.
2. Valitse lähteeksi lähin olemassa oleva esimerkkisivu ja säilytä sen `:data-cy`-ankkurien malli.
3. Käytä rekisterissä `:sallitut-vanhemmat`-kaarta kanonisena lapsisopimuksena. Käänteinen `sallitut-lapset`-näkymä johdetaan siitä eikä sitä ylläpidetä erikseen.
4. Kirjoita declaratiivinen EDN-puu. Älä lisää funktioita, atomeja, suoraa Bootstrap-markupia tai sivukohtaista CSS:ää.
5. Kun komponentilla on `:simulaattori-asetukset`, käytä sitä EDN-muodossa. `:asetukset` kuvaa tuotanto-API:a.
6. Aja `lein test :only harja.ui.perus.esimerkkisivut-test` ja tarvittaessa `lein test :only harja.ui.perus.simulaattori-test`.
7. Tarkista uusi puu Sivusimulaattorissa normaali-, tyhjä-, lataus-, virhe- ja kapea tila käyttäen yhteistä sivuntilavalitsinta.

Esimerkit ladataan käännösaikaisella makrolla, joten EDN-muutos ei laukaise automaattista reloadia. Poikkeus on perusteltu, koska esimerkkien on pysyttävä samana JVM-testidatana ja selainbundlessa. Kehityksessä aja Figwheel-build uudelleen tai kosketa `esimerkkisivut.cljc`-tiedostoa.

## Sopimus

- Käyttöpaikka antaa kentälle `:id`:n. Komponentti ei generoi id:tä renderöinnissä.
- Komponentti johtaa `label for`, `<id>-ohje`, `<id>-virhe` sekä `aria-describedby`, `aria-invalid` ja `aria-required`.
- `:data-cy` on oletuksena `:id`. `:luokka` viedään juurielementille.
- Pudotusvalikon valinta välitetään `:valitse-fn`:lle täsmälleen samassa muodossa kuin vaihtoehto annettiin.
- **Välisääntö:** komponentit eivät aseta ulkomarginaaleja. Lasten välin omistaa aina vanhempi `gap`-ominaisuudella ja nimetyllä tokenilla (`:pieni`, `:keski`, `:suuri`): `pino`, `vaaka`, `ruudukko`, `osio` ja `kortti` sekä niiden sisältö-wrapperit (`.ui-osio-sisalto`, `.ui-kortti-sisalto`). Jos lapset ovat kiinni toisissaan, korjaa vanhemman gap. Älä lisää lapselle marginaalia, spacer-CSS:ää, `* + *`-valitsinta tai `:margin`-asetusta API:in. Sivun ylä- ja alareunan tila on vanhemman paddingia. Valinnaisen sisällön ympärille ei tehdä tyhjää wrapperia. `harja.ui.perus.valit-test` valvoo sääntöä (juurielementeillä ei ulkomarginaalia, säiliöiden sisältö-wrapperit omistavat gapin). Legacy-komponenttien omia marginaaleja ei korjata ennen porttausvaihetta.
- Layout-komponentit käyttävät nimettyjä Väylä-tokeneita (`:pieni`, `:keski`, `:suuri`) eivätkä vapaata CSS:ää. `vaaka` on vaakasuuntainen `flex`, `sailio` rajaa sivusisällön leveyden ja sisäreunan (valinnaisesti `:keskita? true` keskittää sen vaakasuunnassa), `ruudukko` on responsiivinen 1–4-palstainen CSS-grid ja `osio` on semanttinen otsikollinen alue.
- `kortti` kokoaa otsakkeen, sisällön ja alatunnisteen ilman ulkomarginaalia. `tietolista` on sen semanttinen `<dl>`-pohjainen otsikko-arvo-sisältö; uuteen UI:hin käytetään niitä vanhojen `panel`- ja `tietoja`-komponenttien sijaan.
- `sailio` vastaa yleisen UI-frameworkin `Container`ia. Nimeä `sivu` ei käytetä, koska `:sivu` on simulaattorin komponenttipuun tekninen juurivanhempi.
- Bootstrap-riippuvuus kirjataan rekisterissä `:bootstrap-tila`-arvolla (`:ei-riippuvuutta`, `:osittain-riippuvainen`, `:riippuvainen`, `:maarittelematta`).
- Jokaisella rekisterimerkinnällä on `:suositus` (`:uuteen-ui`, `:vanha`, `:ei-suositusta`) ja `:korvaa`-lista (tekstit vanhoista vastineista, tyhjä vektori jos ei korvausta). Sopimusruutu näyttää molemmat.
- `:legacy-paatos` kuvaa elinkaaripäätöksen. Sallitut arvot ovat `:aktiivinen` ja `:legacy`. Ilman erillistä päätöstä `:uuteen-ui` tulkitaan aktiiviseksi, `:vanha` legacyksi ja `:ei-suositusta` jää `:paatamatta`-tilaan. Eksplisiittinen päätös ohittaa johdetun arvon.
- Uudessa merkinnässä (`:suositus :uuteen-ui`) kielletyt asetusnimet ovat `:class`, `:classes`, `:disabled?`, `:disabloi`, `:valitse-fn` ja `:valinta-fn`. Käytä `:luokka`, `:disabled` ja `:vaihda-fn`. `-fn`-päätteisen asetuksen tyypin on oltava `:funktio` ja `?`-päätteisen `:boolean`. Vanhat merkinnät (`:vanha`, `:ei-suositusta`) saavat käyttää vanhoja nimiä, mutta tyyppisäännöt koskevat vain uusia merkintöjä.
- `:suositus` kertoo, mitä uuteen UI:hin käytetään. `:uuteen-ui` on suositeltu komponentti, `:vanha` on olemassa oleva Bootstrap-riippuvainen rakenne (esim. `rivi`) ja `:ei-suositusta` on käytössä oleva komponentti, jolle ei ole vielä korvaajaa tai jonka korvaaja on toinen komponentti. Poistettu API voidaan säilyttää `:korvaa`-listassa migraatioiden lähdeviitteenä. Komponentti ei saa korvata itseään: `:korvaa` listaa vanhat vastineet, ei komponentin omaa funktiota.
- `:sallitut-vanhemmat` listaa rekisterin komponentti-id:t (tai `:sivu`), joiden lapsena komponentti saa olla. Ryhmä-id tai rooli ei ole kelvollinen vanhempi. Simulaattori hylkää kaikki muut yhdistelmät, joten kun layout-komponentti (`pino`, `suodatinrivi`, `toimintoryhma`, `panel`) saa uuden tyypillisen lapsen, lisää layout-komponentin id lapsen vanhempiin.
- `:simulaattori-slotit` nimeää EDN-lapsipuulle pakolliset määrärajoitukset, esim. `:kentta {:min 1 :max 1}`. Slottiavaimen pitää kuulua komponentin `:slotit`-joukkoon.
- `:simulaattori-asetukset` korvaa saman avaimen tuotantoasetuksen simulaattorin EDN-muodossa. Tämä on raja esimerkiksi `tabs :valilehdet`-datan ja `muokkausgrid :rivit`-vektorin käyttämiselle ilman tuotantoatomia.
- `:slotit` ja `:asetukset` ovat pakollisia avaimia, mutta ne saavat olla tyhjiä, kun komponentilla ei ole sloteja tai parametreja (esim. `murupolku`). `:tilat` ja `:sallitut-vanhemmat` eivät saa olla tyhjiä.
- `:nimeamispoikkeukset` (joukko asetusnimiä) sallii uudessa merkinnässä yksittäisen kielletyn asetusnimen, kun se on jo käytössä olevan API:n osa. Validointi hylkää poikkeuksen, jota merkintä ei tarvitse.
- `:yhteiset-variantit` yhdistää yhteisen palautesanaston (`palaute-variantit`: `:onnistui`, `:info`, `:varoitus`, `:virhe`) komponentin omaan arvoon, esim. viestin `:luokka`-arvoon. Palautekomponentin pitää antaa kartta, ja avainten pitää kuulua yhteiseen sanastoon. Kartassa saa olla vain ne variantit, joille komponentilla on vastine (info-laatikolla ei ole virhevarianttia).
- Palautekomponentit ovat ryhmässä `:palautteet`. Yhteinen varianttisanasto on `palaute-variantit` (`:onnistui`, `:info`, `:varoitus`, `:virhe`). Komponenttien olemassa olevia variantteja ei ole vielä nimetty uudelleen.

## Livi-pudotusvalikon sopimus

### Suositus ja vastuut

- `livi-pudotusvalikko` on edelleen aktiivinen tuotantokomponentti, mutta sitä
  ei suositella uudelle käyttöpaikalle ennen tavoitetilan saavutettavuustestien
  toteutumista. Rekisterin suositus on `:ei-suositusta` ja elinkaaripäätös
  `:aktiivinen`.
- Rekisteri omistaa public API:n, suosituksen, `:korvaa`-suhteen ja
  Bootstrap-tilan. Tässä tiedostossa ei toisteta rekisterin asetustaulukkoa.
- Käyttöpaikka omistaa arvon, vaihtoehdot, domain-rajoitukset,
  validoinnin, callbackit, näkyvät tekstit, disabloinnin ja tiedon siitä,
  mikä vaihtoehto on valittu.
- Komponentti omistaa labelin ja kentän yhteyden, popupin, fokuksen,
  näppäimistövuorovaikutuksen, ARIA-suhteet, valitun tilaindikaation ja
  vaihtoehtojen käytettävyysluokan.
- Käyttöpaikka antaa vakaan `:elementin-id`:n uudessa UI:ssa. Komponentti
  johtaa siitä triggerin, popupin ja labelin id-suhteet. Vapaa fallback ei ole
  uudelle käyttöpaikalle hyväksytty sopimus.
- Avoimen ja suljetun tilan callbackit (`:auki-fn!`, `:kiinni-fn!`) kuvaavat
  tilansiirtymää. Ne eivät omista fokus- tai DOM-logiikkaa.

### Käyttöluokat

| Luokka | Sopimus |
| --- | --- |
| Yksinkertainen tekstivalinta | Livi käsittelee yhden tekstivalinnan. |
| Ryhmitelty valinta | Livi ylläpitää ryhmäotsikot ja vaihtoehtojen järjestyksen. |
| Disabloitu vaihtoehto | Käyttöpaikka ilmoittaa estyneet vaihtoehdot; livi ohittaa ne näppäimistöllä ja näppäinohjauksen testissä. |
| Komponenttirivi | Käyttöpaikka antaa sallitun rivikomponentin; livi omistaa valinnan ja fokuksen. |
| Checkbox-monivalinta | Ei kuulu yksivalintaan. Käytä `checkbox-pudotusvalikko`a omalla sopimuksellaan. |
| Haku | Ei kuulu livin perussopimukseen. Haku tarvitsee erillisen public API:n ja testit. |
| Action menu | Ei ole arvovalinta. Käytä erillistä toimintovalikkokomponenttia. |

### Nykyinen toteutus

Nykyinen toteutus ei vielä täytä tavoitesopimusta eikä tätä nykytilaa saa kuvata
saavutettavuustavoitteen toteutuneena:

- trigger on `button`, mutta näkyvä `label` ei ole yhdistetty sen `id`:hen;
  vain valinnainen `aria-label` nimeää triggerin
- triggerillä ei ole `aria-expanded`-, `aria-controls`- tai popup-ominaisuuksia
- lista on `ul`, jonka kohteet ovat fokusoitavia `li`-elementtejä, mutta niillä ei
  ole listbox-/option-rooleja eikä valitulle kohteelle ARIA-tilaa
- Enter ja Space avaavat triggeriltä valikon ja siirtävät fokuksen valittuun tai
  ensimmäiseen vaihtoehtoon
- listassa nuolet liikuttavat fokusta, Enter valitsee ja Escape tai Tab sulkee
  listan sekä palauttaa fokuksen triggeriin
- triggerin Escape- ja Tab-käsittely sulkee valikon ilman yhtenäistä callback- tai
  fokuspalautussopimusta
- typeahead tarkastelee tällä hetkellä yhtä näppäimen merkkiä ja sulkee listan
  osuman löytyessä
- ulkopuolinen klikkaus sulkee valikon ja kutsuu `:kiinni-fn!`-callbackin

### Tavoitetila

Myöhempi toteutusviipale muuttaa DOM- ja vuorovaikutussopimuksen näin:

- triggerin saavutettava nimi tulee näkyvästä labelista; valinnainen
  `aria-label` saa vain täydentää nimeämistä perustellussa poikkeustapauksessa
- trigger on select-only combobox: `role=combobox`, `aria-expanded`,
  `aria-controls`, popup-suhde listboxiin ja vakaat trigger-, label- ja
  popup-id:t
- popup on `role=listbox`; vaihtoehto on `role=option` ja valittu vaihtoehto
  käyttää `aria-selected=true`-tilaa
- Enter ja Space avaa tai sulkee triggerin. Nuoli alas avaa listan ja siirtää
  fokuksen valittuun vaihtoehtoon tai ensimmäiseen; nuoli ylös käyttää
  valittua tai viimeistä vaihtoehtoa
- listan nuolet ohittavat disabloidut vaihtoehdot, Enter valitsee ja Space
  valitsee tai vaihtaa checkbox-rivin tilan siinä komponentissa, jossa
  monivalinta on tuettu
- Escape sulkee listan ja palauttaa fokuksen triggeriin. Tab sulkee listan
  viivyttämättä normaalin tab-järjestyksen etenemistä
- klikkauksella tai näppäimistöllä tehty valinta sulkee listan. Näppäimistövalinta
  palauttaa fokuksen triggeriin
- ulkopuolinen klikkaus sulkee listan eikä siirrä fokusta pois klikattavasta
  kohteesta
- typeaheadin tulee vähintään säilyttää nykyinen yhden merkin yhteensopivuus.
  Useamman merkin ja hakujakson aikakatkaisun rajaus tehdään toteutusviipaleen
  hyväksymiskriteereihin ennen koodimuutosta
- `:auki-fn!` ja `:kiinni-fn!` kutsutaan täsmälleen yhdelle avaus- tai
  sulkemistilansiirtymälle riippumatta siitä, millä syötteellä siirtymä tapahtui

### Varmennus

- Rekisteritesti lukitsee sen, ettei keskeneräistä liviä merkitä
  `:uuteen-ui`-suositukseksi ja että sen `:korvaa`-suhde pysyy tallessa.
- Hiccup- tai rekisteritesti ei todista DOM-fokusta tai saavutettavuuskäytöstä.
- Tavoitetilan Playwright-passi testaa triggerin, listan ja option tila- ja
  ARIA-suhteet sekä avauksen, nuolinäppäimet, Enterin, Spacen, Tabin, Escapen,
  typeaheadin, valinnan ja ulkopuolisen klikkauksen.
- Yksikään tämän nykytilaa kuvaava puute ei ole peruste jättää pois myöhemmän
  toteutusviipaleen acceptance criteria -kohdasta.

## Nimiavaruudet

- Shared-komponentit ovat `harja.ui.perus` (`src/cljc/harja/ui/perus.cljc`), rekisteri `harja.ui.perus.rekisteri` ja simulaattori `harja.ui.perus.simulaattori`. Nimi erottaa ne lifecycle-nimiavaruudesta `harja.ui.komponentti`, jota käyttää yli 160 tiedostoa.
- `harja.ui.komponentti` (komponentin elinkaari, `komp/luo`) ei muutu tässä.
- `primitiivit/viesti.cljc` (`harja.ui.primitiivit.viesti`) pysyy primitiivikerroksessa tabs-primitiivin rinnalla. Sen siirto ei ole tarpeen, koska sen käyttäjät ovat `harja.ui.viesti` ja tarkastelusivun portatut-esikatselut.

## Esimerkkisivut

Esimerkkisivut ovat valmiita, kelvollisia simulaattoripuita sekä käyttäjille (valinta simulaattorin "Esimerkkisivu"-kentästä) että agenteille (luettava ja kopioitava toteutusmalli). Käytä niitä lähtökohtana, kun rakennat uutta sivua tai tarkistat, mitä komponenttiyhdistelmiä simulaattori tukee.

- Yksi tiedosto per esimerkki hakemistossa `src/cljc/harja/ui/perus/esimerkkisivut/`. Tiedostonimi on snake_case (`listaussivu.edn`), esimerkit näytetään tiedostonimen mukaisessa järjestyksessä.
- Tiedoston muoto: `{:id :listaussivu :nimi "Näytettävä nimi" :kuvaus "Mitä esimerkki näyttää" :puu [{:komponentti ...}]}`. `:puu` on sama deklaratiivinen rakenne kuin simulaattorin EDN-lähde.
- Tiedostot luetaan käännösaikaan makrolla (`harja.ui.perus.esimerkkisivut/lataa-esimerkkisivut`). Tiedoston muokkaus ei laukaise Figwheelin uudelleenlatausta: kosketa `esimerkkisivut.cljc`-tiedostoa tai käännä uudelleen.
- `lein test :only harja.ui.perus.esimerkkisivut-test` validoi jokaisen esimerkin simulaattorin säännöillä sekä tarkistaa yksilölliset id:t, nimen ja kuvauksen. Uusi esimerkki saa käyttää vain komponentteja, joilla on simulaattorin `:renderoi`. Muuten esikatselu näyttää virheen, joten tarkista uusi esimerkki selaimessa.
- Uusi esimerkki lisätään tiedostona, ei koodina. Esimerkki ei saa sisältää funktioita, symboleja tai hiccup-markupia.
- Simulaattorin yhteiset sivutilat ovat `:normaali`, `:lataus`, `:tyhja`, `:virhe` ja `:ei-oikeutta`. Tilanvalinta ei muuta EDN-lähdettä: epänormaali tila korvaa vain renderöidyn esikatselun sisällön. Lähde, validointi ja tilavalitsin jäävät näkyviin. Lataus ja tyhjä käyttävät `status`-roolia, virhe ja ei-oikeutta `alert`-roolia.

## Tunnetut rajoitukset

- Simulaattorin renderöijä (`:renderoi`) on tehty rekisterin komponenteista näille: asettelut (`pino`, `vaaka`, `sailio`, `osio`, `ruudukko`, `suodatinrivi`, `toimintoryhma`, `lomake`, `panel`), `nappi`, `linkki`, `ikoni-ja-teksti`, `tilaviesti`, kentät (`tekstikentta`, `numerokentta`, `sahkopostikentta`, `puhelinkentta`, `valintaruutu`, `raksiboksi`, `radiokentta`, `checkbox-ryhma`, `radio-group`, `valinta`, `kenttaryhma`, `suodatin`), alasvedot (`livi-pudotusvalikko`, `checkbox-pudotusvalikko`, `hoitokausi`), gridit (`perusgrid`, `muokkausgrid`), sisältö (`tietoja`, `taulukkotietonakyma`, `kaksi-palstaa-otsikkoja-ja-arvoja`) ja palautteet (`ajax-loader`, `vihje`, `info-laatikko`, `tila-indikaattori`, `tooltip`, `tabs`, `dropdown-panel`).
- Renderöijä välittää asetuksista vain dokumentoidut, datana annettavat arvot (whitelist). Vapaata `:luokka`-, `:class`-, `:style`- tai hiccup-asetusta ei välitetä, vaan tyylit tulevat renderöijän omasta sanastosta: `nappi` käyttää `:variantti`-asetusta (`:ensisijainen`, `:toissijainen`, `:reunaton`, `:myonteinen`, `:kielteinen`, `:varoitus`), `panel` ottaa sisällön `:lapset`-solmuista. Funktiot, atomit ja ikonit eivät voi tulla syötteestä, joten renderöijä antaa ne itse. Taulukko- ja otsikko-arvo-komponentit saavat datan `:rivit`-asetuksella.
- Jokaisella instanssilla on oma tila (`r/with-let`) ja `data-cy` joko asetuksesta tai oletuksena `ui-simulaattori-<komponentti>`. Alkuarvoa ei voi antaa `:arvo`-asetuksella (suojattu).
- Ilman simulaattorin renderöijää ovat tarkoituksella: `murupolku`, `navbar`, `viesti`, `modal`, `sivupalkki`, `toast-viesti`, `leijuke`, `varmista-kayttajalta` (globaali tila tai overlay), `dropdown` (avaustila puuttuu) ja `rivi` (vanha Bootstrap-rakenne).
- `liitteet` saa simulaattorissa staattisen `liitetiedosto`-kortin. Oikea `harja.ui.liitteet`-komponentti tekee backend-kutsuja, joten simulaattori ei renderöi sitä.
- `kenttaryhma` ottaa simulaattorissa täsmälleen yhden rekisteröidyn lapsikentän. Tuetut kentät ovat `tekstikentta`, `numerokentta`, `paivamakentta`, `livi-pudotusvalikko` ja `tekstialue`. Tuotannossa `kenttaryhma` välittää kentälle `:id`, `:aria-describedby`, `:aria-invalid`, `:aria-required` ja `:disabled?`; EDN-simulaattori johtaa vakaan id:n pakollisesta `:data-cy`-avaimesta. Kenttä pitää oman arvonsa, joten arvo, tallennus, validointisäännöt ja callbackit eivät kuulu wrapperin sopimukseen.
- `:vayla-tyyli?` ei vaikuta kenttätyyppeihin `:email` ja `:text` (`kentat.cljs`), joten niiden input ja textarea jäävät ilman Väylä-luokkia (`form-control` vain `lomake?`-tilassa). Tämä on tuotantokomponentin rajoitus, jonka korjaus vaatii luvan. Sähköpostin ja tekstialueen simulaattorikortit eivät siksi näytä Väylä-tyyliä.
- Tarkastelusivun esikatselut (CLJS) validoidaan selaimessa. Rakennetestit kattavat vain cljc-puolen hiccup-rakenteen.
- Tarjoushinnat-näkymän `urakka-haku-id` on edelleen render-aikainen `gensym`.
- Livi-pudotusvalikon nykytila ja tavoitetila on dokumentoitu yllä. Nykyinen
  toteutus ei vielä toteuta kaikkia tavoitetilan ARIA- ja fokussuhteita. Nappi on
  merkitty `:osittain-riippuvaiseksi`, koska Bootstrap-luokat tulevat
  käyttöpaikan `:luokka`-optiosta.
- Kenttätyyppi `:valinta` renderöityy livi-pudotusvalikon kautta, joten sille ei ole omaa korttia. Sen Bootstrap-riippuvuus on sama kuin livi-pudotusvalikolla.
- Gridit (`perusgrid`, `muokkausgrid`) ovat `:riippuvainen`, koska ne käyttävät `.panel`-, `.panel-default`- ja `btn-xs`-luokkia.
- `perusgrid`in EDN-sopimuksessa vetolaatikko valitaan rekisteriavaimella, ryhmäotsikko annetaan `:rivityyppi :otsikko` -rivinä, sivutus positiivisella `:sivuta`-arvolla ja yhteenveto valmiiksi lasketulla `:rivityyppi :yhteenveto` -rivinä. Rivityyppien ankkurit ovat `perusgrid-rivi-tyhja`, `perusgrid-rivi-vetolaatikko`, `perusgrid-rivi-otsikko` ja `perusgrid-rivi-yhteenveto`. Sopimus ei salli funktioita, vapaata Hiccupia, omia sivutuskontrolleja eikä yhteenvetolaskentaa komponentissa.
- Checkbox-ryhmä on `:osittain-riippuvainen`: yksi palsta on puhdas, useampi palsta käyttää `col-sm-*`-luokkia.
- Modal on `:riippuvainen`, koska `modal-container` käyttää yhä `.modal`-, `.modal-dialog`- ja `.modal-content`-luokkia.

## Kartta

Kartta ei ole rekisterissä käytettävänä komponenttina. Syyt:

- `harja.ui.openlayers/openlayers` vaatii `mapspec`-argumentin, jonka kutsuja muodostaa.
- Komponentti käyttää jaettua tilaa (`the-kartta`, `komento-ch`, klik- ja hover-käsittelijät), joten vain yksi instanssi on kerrallaan.
- Globaali kartta renderöidään kerran `harja.views.main`-näkymässä. Näkymät eivät renderöi karttaa itse.

Näkymät ohjaavat karttaa `harja.tiedot.navigaatio`-funktioilla: `vaihda-kartan-koko!` kokoilla `:S`, `:M`, `:L` tai `:hidden`, `kartan-edellinen-koko` palauttamiseen ja `kartta-nakyvissa?` näkyvyyden lukemiseen. Tasot hallitaan `kartta-tasot/taso-paalle!` ja `taso-pois!` -funktioilla.

## Overlay ja domain-kytkeytyvät komponentit

- `:sivupalkki` (`harja.ui.sivupalkki/oikea`) on kiinteä overlay (`overlay-oikealla`: `position: fixed; right: 0; height: 100%`), ei normaalin virtauksen laatikko. Avaamisen ja sulkemisen päättää käyttöpaikka. Komponentti siirtää fokuksen sisältöön ja palauttaa sen. Esikatselu avaa paneelin vain napista, jotta fokus ei siirry sivun latautuessa.
- `:liitteet` (`harja.ui.liitteet`) on domain-kytkeytyvä: lisäys ja lista tekevät backend-kutsuja ja oikeustarkistuksen urakka-id:llä. Esikatselu renderöi vain `liitetiedosto`-kortin ilman backendiä. Kuvaliitteet avautuvat `modal`-komponentissa, joten se on `:osittain-riippuvainen`.
- `:murupolku` renderöidään globaalisti `harja.views.main`-näkymässä. Esikatselu on staattinen kortti, koska toinen instanssi duplikoisi globaalit `alasveto-*`-id:t ja näppäimistökäsittelijät.

## Lomakkeen apurit

Lomake rakennetaan `:lomake`-komponentin kautta. Apurit ovat skeeman osia, eivät erillisiä komponentteja, joten niitä ei rekisteröidä omina merkintöinä:

- `ryhma` ryhmittelee kentät otsikon alle.
- `rivi` asettaa kentät vierekkäin samalle riville.
- `lomake-spacer` lisää välin.
- `::lomake/col-luokka` asetetaan yksittäiselle kentälle `:luokka`-arvolla. Oletusarvoja ei käytetä uudessa UI:ssa, koska ne ovat Bootstrap-gridiluokkia (`col-xs-12 col-sm-6 col-md-5 col-lg-4`).
- `ilman-lomaketietoja` ja `voi-tallentaa?` ovat datan apureita, eivät UI-komponentteja.

Yhtä kenttää varten `col-luokka` voi kuitenkin olla oma luokka, koska `kentta` käyttää skeeman arvoa ensin.

## Rajauksen ulkopuolella

Älä laajenna tätä kokonaisuutta ilman erillistä päätöstä:

- olemassa olevien Bootstrap-käyttöpaikkojen yleinen porttaus
- dropdownien fokus-, Escape- ja ulkopuolisen klikkauksen uuden sopimuksen määrittely
- domain-tilan, Tuck-tapahtumien tai backend-kutsujen siirtäminen shared-komponenttiin
- kaikkien nappien, kenttien, gridien tai modaalien yhdistäminen tähän namespaceen
- näkymäkohtaisen CSS:n muuttaminen yleiseksi vain luokkien yhdenmukaistamisen vuoksi
- laaja nimeämis-, LESS- tai Bootstrap-overridejen siivous

## Review-portti

Review hyväksyy uuden muutoksen vain, jos kaikki täyttyvät:

- public API ja vastuunjako ovat näkyvissä tarkastelusivulla tai lähikoodin dokumentaatiossa
- käyttöpaikka käyttää vain dokumentoituja `data-cy`-ankkureita ja saavutettavuusrooleja
- domain-tila pysyy näkymässä
- normaali- ja olennaiset rajatilat on validoitu
- `lein compile-dev`, tarvittaessa `npm run build-css`, editoridiagnostiikka ja selainpassi ovat vihreät
- uusi käyttöpaikka ei lisää suoraa Bootstrap-markupia
- muutoksen laajuus on yksi komponenttisopimus tai yksi tuotantokäyttöpaikka

## Tuotantokomponenttien korjaukset

Tuotantokomponentteja (`src/cljs/harja/ui/`, `src/cljs/harja/views/`) ei muokata ennen kuin komponenttijärjestelmän ensimmäinen versio on yhdistetty `develop`-haaraan. Sen jälkeen jokainen korjaus on oma muutoksensa omalla validoinnilla. Alla olevat kohdat on tunnistettu, mutta niitä ei ole toteutettu.

| Korjaus | Kohde | Riski | Vaadittu validointi | Tila |
| --- | --- | --- | --- | --- |
| Alasvedon 4 px:n marginaali suodatinrivillä. `.label-ja-alasveto` nostaa alasvedon muiden kenttien yläpuolelle, ja tarjoushinnat-pilotissa linjaus epäonnistuu. | `styles.less` (`.label-ja-alasveto`), `yleiset.cljs` (`pudotusvalikko`) | Globaali muutos koskee noin 20 tuotantotiedostoa. Sivukohtaiset ylikirjoitukset (`yllapito_kustannukset_tyylit.less`, `tarkastukset.less`, `kulujenkirjaus.less`) voivat muuttua. | Ennen–jälkeen-kuvat 4–5 sivulta, esimerkiksi kulujenkirjaus, valinnat, kojelauta ja kululomake. Tarjoushinnat 1920 px ja 800 px leveydellä. | Odottaa päätöstä. Rajattu korjaus `.ui-suodatinrivi .label-ja-alasveto { margin: 0 }` ei riko tuotantoa, koska `.ui-suodatinrivi` on vain uudessa järjestelmässä. |
| Väylä-tyyli ei päde sähköposti- ja tekstikenttään. `:vayla-tyyli?` ei vaikuta tyyppeihin `:email` ja `:text`. | `kentat.cljs` | Tyyli muuttuu kaikissa näiden tyyppien tuotantokentissä. | Selainpassi sähköposti-, tekstikenttä- ja tekstialuekäyttöpaikoissa. Tyyppitesti, joka varmistaa, että jokainen kenttätyyppi saa Väylä-luokat. | Odottaa lupaa. |
| `tee-kentta` ei tue `aria-describedby`- eikä `aria-invalid`-attribuutteja. | `kentat.cljs` (`tee-kentta`) | Muutos koskee kenttien virheilmoituksia, joten käyttäytyminen voi muuttua. | Selainpassi virhetilassa ja saavutettavuustarkistus. | Odottaa lupaa. |
| Liitteiden sijainti kululomakkeessa. | Kululomakkeen näkymä | Layoutpäätös, ei tekninen riski. | Päätös ennen toteutusta. | Avoin. |

Vielä selvittämättä: lomakekomponenttien (esimerkiksi alasveto ja nappi) keskittäminen samalla vaaka-layoutilla. Keskittämistä on testattu vain `sailio`-komponentilla (`:keskita?`).

## Seuraava sallittu askel

Siirrä yksi uusi tuotannon UI-alue komponenttien käyttöön ja lisää tarvittavat komponentit rekisteriin yksi kerrallaan. Bootstrap-porttaus tehdään erillisenä viipaleena vasta, kun kyseisen komponentin sopimus on tarkastelusivulla vakaa.
