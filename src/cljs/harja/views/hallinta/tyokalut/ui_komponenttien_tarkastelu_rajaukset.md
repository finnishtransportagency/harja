# UI-komponenttien tarkastelun rajaus

Tämä dokumentti on tarkastelusivun review-pinta. Arvioi muutoksia ensin tämän rajauksen kautta.

## Tavoite

Rakentaa Harjan uutta UI:ta shared-komponenttien avulla niin, että:

- näkymä omistaa domain-datan, tilan, haut, oikeudet ja callbackien päätökset
- shared-komponentti omistaa DOM-rakenteen, saavutettavuuden, vuorovaikutuksen ja vakaat `data-cy`-ankkurit
- uusi UI ei lisää suoraa Bootstrap-markupia eikä näkymäkohtaista yleis-CSS:ää
- komponentin public API näkyy tarkastelusivulla ennen tuotantokäyttöpaikkaa

## Rakenne

| Osa | Sijainti |
| --- | --- |
| Shared-komponentit (`pino`, `suodatinrivi`, `suodatin`, `kenttaryhma`, `valintaryhma`, `toimintoryhma`, `tilaviesti`) | `src/cljc/harja/ui/perus.cljc` |
| Komponenttirekisteri (data) ja eheystarkistus | `src/cljc/harja/ui/perus/rekisteri.cljc` |
| Rekisterin EDN-tulostus komentoriviltä | `src/clj/harja/tyokalut/komponenttirekisteri.clj` (`lein komponenttirekisteri`) |
| Tarkastelusivun entry | `tyokalut/ui_komponenttien_tarkastelu_nakyma.cljs` |
| Tarkastelurunko (navigaatio, kortti, sopimus, `ankkuri`) | `tyokalut/ui_komponenttien_tarkastelu/kehys.cljs` |
| Esikatselut ryhmittäin | `tyokalut/ui_komponenttien_tarkastelu/{alasvedot,toiminnot,lomakkeet,gridit,palautteet,portatut,simulaattori}.cljs` |
| Esikatselujen rekisteröinti | `tyokalut/ui_komponenttien_tarkastelu/rekisterointi.cljs` |
| Esimerkkisivut (EDN, simulaattorin valinta, ks. alla) | `src/cljc/harja/ui/perus/esimerkkisivut/*.edn`, lataus `src/cljc/harja/ui/perus/esimerkkisivut.cljc` |
| Sopimus- ja rekisteritestit (`lein test`) | `test/cljc/harja/ui/perus_test.cljc`, `test/cljc/harja/ui/perus/rekisteri_test.cljc`, `test/clj/harja/ui/perus/esikatselu_test.clj`, `test/clj/harja/ui/perus/bootstrap_luokat_test.clj` |
| Tyylit | `dev-resources/less/komponentit.less`, `dev-resources/less/pages/ui_komponenttien_tarkastelu.less` |

Uuden komponentin lisääminen:

1. Lisää rekisterimerkintä `rekisteri.cljc`:ään (`:id`, `:ryhma`, `:nimi`, `:kuvaus`, `:funktio`, `:asetukset`, `:bootstrap-tila`).
2. Lisää esikatselu ryhmän tiedostoon `register!`-kutsulla rekisterin `:id`:llä. Jokainen id esiintyy `(register! :id `-muodossa täsmälleen kerran `src/cljs`-hakemistossa. Kaksi rekisteröintiä samalla id:llä on virhe.
3. Lisää sopimustesti, jos komponentti on shared-komponentti.
4. `rekisteri/virheet`-testi epäonnistuu, jos rekisterin tiedot ovat puutteelliset.

## Rekisterin tulostus

`lein komponenttirekisteri` tulostaa rekisterin EDN:nä stdoutiin ilman appia, tietokantaa tai selainta. Suodattimet annetaan avain-arvo-pareina: `lein komponenttirekisteri :id nappi` tai `lein komponenttirekisteri :ryhma lomakkeet`. Tulos sisältää rekisterimerkinnän kaikki avaimet (mm. `:funktio`, `:asetukset`, `:suositus`, `:korvaa`, `:variantit`, `:bootstrap-luokat`) ja lisäksi `:ankkuri`. Funktioarvot jätetään pois. Tuntematon suodatin, pariton argumenttimäärä sekä tuntematon `:id` tai `:ryhma` tulostavat virheen stderriin ja päättyvät koodilla 1.

## Sopimus

- Käyttöpaikka antaa kentälle `:id`:n. Komponentti ei generoi id:tä renderöinnissä.
- Komponentti johtaa `label for`, `<id>-ohje`, `<id>-virhe` sekä `aria-describedby`, `aria-invalid` ja `aria-required`.
- `:data-cy` on oletuksena `:id`. `:luokka` viedään juurielementille.
- Valintaryhmän arvo voi olla mikä tahansa arvo (keyword, luku, merkkijono); komponentti vie arvon sellaisenaan `:vaihda-fn`:lle.
- Komponentit eivät aseta ulkomarginaaleja. Välit kuuluvat käyttöpaikalle tai `pino`/`toimintoryhma`-rakenteelle.
- Bootstrap-riippuvuus kirjataan rekisterissä `:bootstrap-tila`-arvolla (`:ei-riippuvuutta`, `:osittain-riippuvainen`, `:riippuvainen`, `:maarittelematta`).
- Jokaisella rekisterimerkinnällä on `:suositus` (`:uuteen-ui`, `:vanha`, `:ei-suositusta`) ja `:korvaa`-lista (tekstit vanhoista vastineista, tyhjä vektori jos ei korvausta). Sopimusruutu näyttää molemmat.
- Uudessa merkinnässä (`:suositus :uuteen-ui`) kielletyt asetusnimet ovat `:class`, `:classes`, `:disabled?`, `:disabloi`, `:valitse-fn` ja `:valinta-fn`. Käytä `:luokka`, `:disabled` ja `:vaihda-fn`. `-fn`-päätteisen asetuksen tyypin on oltava `:funktio` ja `?`-päätteisen `:boolean`. Vanhat merkinnät (`:vanha`, `:ei-suositusta`) saavat käyttää vanhoja nimiä, mutta tyyppisäännöt koskevat vain uusia merkintöjä.
- `:suositus` kertoo, mitä uuteen UI:hin käytetään. `:uuteen-ui` on suositeltu komponentti, `:vanha` on olemassa oleva Bootstrap-riippuvainen rakenne (esim. `rivi`) ja `:ei-suositusta` on käytössä oleva komponentti, jolle ei ole vielä korvaajaa tai jonka korvaaja on toinen komponentti (esim. `livi-pudotusvalikko` → `valintaryhma`). Komponentti ei saa korvata itseään: `:korvaa` listaa vanhat vastineet, ei komponentin omaa funktiota.
- `:sallitut-vanhemmat` listaa rekisterin komponentti-id:t (tai `:sivu`), joiden lapsena komponentti saa olla. Ryhmä-id tai rooli ei ole kelvollinen vanhempi. Simulaattori hylkää kaikki muut yhdistelmät, joten kun layout-komponentti (`pino`, `suodatinrivi`, `toimintoryhma`, `panel`) saa uuden tyypillisen lapsen, lisää layout-komponentin id lapsen vanhempiin.
- `:slotit` ja `:asetukset` ovat pakollisia avaimia, mutta ne saavat olla tyhjiä, kun komponentilla ei ole sloteja tai parametreja (esim. `murupolku`). `:tilat` ja `:sallitut-vanhemmat` eivät saa olla tyhjiä.
- `:nimeamispoikkeukset` (joukko asetusnimiä) sallii uudessa merkinnässä yksittäisen kielletyn asetusnimen, kun se on jo käytössä olevan API:n osa (esim. valintaryhmän `:disabled?`). Validointi hylkää poikkeuksen, jota merkintä ei tarvitse.
- `:yhteiset-variantit` yhdistää yhteisen palautesanaston (`palaute-variantit`: `:onnistui`, `:info`, `:varoitus`, `:virhe`) komponentin omaan arvoon, esim. viestin `:luokka`-arvoon. Palautekomponentin pitää antaa kartta, ja avainten pitää kuulua yhteiseen sanastoon. Kartassa saa olla vain ne variantit, joille komponentilla on vastine (info-laatikolla ei ole virhevarianttia).
- Palautekomponentit ovat ryhmässä `:palautteet`. Yhteinen varianttisanasto on `palaute-variantit` (`:onnistui`, `:info`, `:varoitus`, `:virhe`). Komponenttien olemassa olevia variantteja ei ole vielä nimetty uudelleen.

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

## Tunnetut rajoitukset

- Simulaattorin renderöijä (`:renderoi`) on tehty rekisterin komponenteista näille: asettelut (`pino`, `suodatinrivi`, `toimintoryhma`, `lomake`, `panel`), `nappi`, `linkki`, `ikoni-ja-teksti`, `tilaviesti`, kentät (`tekstikentta`, `numerokentta`, `sahkopostikentta`, `puhelinkentta`, `valintaruutu`, `raksiboksi`, `radiokentta`, `checkbox-ryhma`, `radio-group`, `valinta`, `valintaryhma`, `kenttaryhma`, `suodatin`), alasvedot (`livi-pudotusvalikko`, `checkbox-pudotusvalikko`, `hoitokausi`), gridit (`perusgrid`, `muokkausgrid`), sisältö (`tietoja`, `taulukkotietonakyma`, `kaksi-palstaa-otsikkoja-ja-arvoja`) ja palautteet (`ajax-loader`, `vihje`, `info-laatikko`, `tila-indikaattori`, `tooltip`, `tabs`, `dropdown-panel`).
- Renderöijä välittää asetuksista vain dokumentoidut, datana annettavat arvot (whitelist). Vapaata `:luokka`-, `:class`-, `:style`- tai hiccup-asetusta ei välitetä, vaan tyylit tulevat renderöijän omasta sanastosta: `nappi` käyttää `:variantti`-asetusta (`:ensisijainen`, `:toissijainen`, `:reunaton`, `:myonteinen`, `:kielteinen`, `:varoitus`), `panel` ottaa sisällön `:lapset`-solmuista. Funktiot, atomit ja ikonit eivät voi tulla syötteestä, joten renderöijä antaa ne itse. Taulukko- ja otsikko-arvo-komponentit saavat datan `:rivit`-asetuksella.
- Jokaisella instanssilla on oma tila (`r/with-let`) ja `data-cy` joko asetuksesta tai oletuksena `ui-simulaattori-<komponentti>`. Alkuarvoa ei voi antaa `:arvo`-asetuksella (suojattu).
- Ilman simulaattorin renderöijää ovat tarkoituksella: `murupolku`, `navbar`, `viesti`, `modal`, `sivupalkki`, `toast-viesti`, `leijuke`, `varmista-kayttajalta` (globaali tila tai overlay), `liitteet` (backend-kutsut), `dropdown` (avaustila puuttuu) ja `rivi` (vanha Bootstrap-rakenne).
- `kentat/tee-kentta` tukee vain `:elementin-id`:tä, ei `aria-describedby`- tai `aria-invalid`-attribuutteja. Kenttäryhmän ARIA-yhteys toimii natiiveille kentille (valintaryhmä), mutta `tee-kentta`-kentille se vaatii erillisen muutoksen `kentat.cljs`:ään.
- Tarkastelusivun esikatselut (CLJS) validoidaan selaimessa. Rakennetestit kattavat vain cljc-puolen hiccup-rakenteen.
- Tarjoushinnat-näkymän `urakka-haku-id` on edelleen render-aikainen `gensym`.
- Livi-pudotusvalikko on rekisteröity, mutta sen sopimusta ei ole vielä määritelty uudelleen. Nappi on merkitty `:osittain-riippuvaiseksi`, koska Bootstrap-luokat tulevat käyttöpaikan `:luokka`-optiosta.
- Kenttätyyppi `:valinta` renderöityy livi-pudotusvalikon kautta, joten sille ei ole omaa korttia. Sen Bootstrap-riippuvuus on sama kuin livi-pudotusvalikolla.
- Gridit (`perusgrid`, `muokkausgrid`) ovat `:riippuvainen`, koska ne käyttävät `.panel`-, `.panel-default`- ja `btn-xs`-luokkia.
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

## Seuraava sallittu askel

Siirrä yksi uusi tuotannon UI-alue komponenttien käyttöön ja lisää tarvittavat komponentit rekisteriin yksi kerrallaan. Bootstrap-porttaus tehdään erillisenä viipaleena vasta, kun kyseisen komponentin sopimus on tarkastelusivulla vakaa.
