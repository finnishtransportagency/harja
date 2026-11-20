(ns harja.ui.komponentit.rekisteri
  "Komponenttien tarkastelusivun rekisteri: komponenttien kuvaus datana.

  Rekisteri on tarkastelusivun kortin, navigaation ja sopimusruudun yhteinen lähde. Uusi
  komponentti lisätään tänne ensin, ja sen esikatselu liitetään samalla `:id`:llä
  tarkastelusivun esikatselut-kartassa.

  Rekisterissä ei ole renderöintiä, jotta sen eheys voidaan testata JVM:llä."
  (:require [clojure.string :as str]))

(def ankkuri-etuliite "ui-komponenttien-tarkastelu-")

(def ryhmat
  "Navigaation ryhmät esitysjärjestyksessä."
  [{:id :alasvedot :nimi "Alasvedot"}
   {:id :toiminnot :nimi "Toiminnot"}
   {:id :lomakkeet :nimi "Lomakkeet"}
   {:id :gridit :nimi "Gridit"}
   {:id :palautteet :nimi "Palautteet"}
   {:id :portatut :nimi "Portatut Bootstrap-komponentit"}])

(def luonteet
  "Mikä komponentti on: yksittäinen shared-komponentti vai niistä koottu kokonaisuus."
  #{:shared-komponentti :shared-rakenne})

(def bootstrap-tilat
  "Komponentin riippuvuus Bootstrapista. Teksti näytetään tarkastelusivulla."
  {:ei-riippuvuutta {:teksti "Ei Bootstrap-riippuvuutta"}
   :osittain-riippuvainen {:teksti "Osittain Bootstrap-riippuvainen"}
   :riippuvainen {:teksti "Bootstrap-riippuvainen"}
   :maarittelematta {:teksti "Bootstrap-tila määrittelemättä"}})

(def roolit
  "Komponentin rooli rakenteessa. Jokaisella komponentilla on täsmälleen yksi rooli."
  #{:syote :toiminto :lomake :asettelu :taulukko :rakenne :navigaatio :palaute :overlay})

(def asetustyypit
  "Komponentin asetuksen tyyppi. Näytetään sopimusruudussa."
  #{:merkkijono :numero :boolean :funktio :keyword :map :hiccup :vektori :atomi :muu})

(def komponentit
  [{:id :livi-pudotusvalikko
    :nimi "Livi-pudotusvalikko"
    :ryhma :alasvedot
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo :vaihtoehdot}
    :tilat #{:normaali :tyhja :virhe :estetty}
    :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :riippuvainen
    :kuvaus "Nykyinen tuotannossa käytetty dropdown-sopimus. Uuteen UI:hin suositellaan valintaryhmää, ellei haku tai kombo-valinta ole tarpeen."
    :funktio "harja.ui.yleiset/pudotusvalikko"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Valikon otsikko."}
                :optiot {:tyyppi :map :pakollinen? true :oletus nil :kuvaus "Valikon asetukset, esim. :auki-fn!, :kiinni-fn!, :elementin-id, :wrap-luokka, :vayla-tyyli?."}
                :valinnat {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Vaihtoehdot vektorina tai mappina."}}}
   {:id :nappi
    :nimi "Nappi"
    :ryhma :toiminnot
    :luonne :shared-komponentti
    :rooli :toiminto
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:toiminto}
    :tilat #{:normaali :estetty :lataus}
    :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :osittain-riippuvainen
    :funktio "harja.ui.napit/nappi"
    :asetukset {:teksti {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Napin teksti."}
                :toiminto {:tyyppi :funktio :pakollinen? true :oletus nil :kuvaus "Klikkauksen käsittelijä."}
                :disabled {:tyyppi :boolean :pakollinen? false :oletus false :kuvaus "Jos true, nappi on disabloitu."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Napin CSS-luokat, välilyönnillä erotettuna."}
                :ikoni {:tyyppi :hiccup :pakollinen? false :oletus nil :kuvaus "Nappiin piirrettävä ikonikomponentti."}
                :data-cy {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "E2E-testien tunniste."}
                :tallennus-kaynnissa? {:tyyppi :boolean :pakollinen? false :oletus false :kuvaus "Jos true, piirtää latausindikaattorin."}}
    :kuvaus "Yhteinen toimintopainike. Renderöi oman button-elementin, mutta käyttöpaikan :luokka voi tuoda Bootstrapin btn-luokat."}
   {:id :suodatin
    :nimi "Suodatin"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:kentta}
    :tilat #{:normaali :tyhja :virhe}
    :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Suodattimen label ja kenttä. Käytössä tarjoushintojen näkymässä."
    :funktio "harja.ui.komponentit/suodatin"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Suodattimen otsikko."}
                :id {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Suodattimen id."}
                :data-cy {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "E2E-testien tunniste."}
                :kentta {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Kenttä hiccupina tai funktiona."}}}
   {:id :valintaryhma
    :nimi "Valintaryhmä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :uuteen-ui
    :korvaa ["yleiset/pudotusvalikko" "yleiset/livi-pudotusvalikko" "kentat/tee-kentta :valinta"]
    :nimeamispoikkeukset #{:disabled?}
    :slotit #{:arvo :vaihtoehdot}
    :tilat #{:normaali :tyhja :virhe :estetty}
    :sallitut-vanhemmat #{:lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Saavutettava natiivin select-kentän ympärille rakennettu valintaryhmä."
    :funktio "harja.ui.komponentit/valintaryhma"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Ryhmän otsikko."}
                :vaihtoehdot {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Valittavat vaihtoehdot."}
                :arvo {:tyyppi :muu :pakollinen? true :oletus nil :kuvaus "Valittu arvo."}
                :vaihda-fn {:tyyppi :funktio :pakollinen? true :oletus nil :kuvaus "Kutsutaan valinnan muuttuessa."}
                :tyhja-valinta {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Tyhjän valinnan teksti."}
                :ohje {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Ohjeteksti."}
                :virhe {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Virheteksti."}
                :pakollinen? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Merkitsee ryhmän pakolliseksi."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, ryhmä on disabloitu."}}}
   {:id :tekstikentta
    :nimi "Tekstikenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Yksirivinen tekstikenttä. Arvo ja muutoksen käsittely omistaa käyttöpaikka. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :string."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon arvo tallennetaan."}
                :placeholder {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Paikkamerkkiteksti."}
                :elementin-id {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Input-elementin id."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on disabloitu."}
                :lomake? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on lomakkeen osa."}}}
   {:id :numerokentta
    :nimi "Numerokenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Numerokenttä muotoilee arvon ja hyväksyy vain sallitut luvut. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :numero."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon arvo tallennetaan."}
                :desimaalien-maara {:tyyppi :numero :pakollinen? false :oletus nil :kuvaus "Suurin sallittu desimaalien määrä."}
                :kokonaisosan-maara {:tyyppi :numero :pakollinen? false :oletus nil :kuvaus "Suurin sallittu kokonaisosan pituus."}
                :kokonaisluku? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, sallitaan vain kokonaisluvut."}
                :vaadi-ei-negatiivinen? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, negatiiviset arvot hylätään."}
                :elementin-id {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Input-elementin id."}
                :lomake? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on lomakkeen osa."}}}
   {:id :sahkopostikentta
    :nimi "Sähköpostikenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Sähköpostiosoitteen syöttökenttä selaimen omalla tyypillä. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :email."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon arvo tallennetaan."}
                :placeholder {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Paikkamerkkiteksti."}
                :on-focus {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan kentän fokusoituessa."}
                :on-blur {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan kentältä poistuttaessa."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on disabloitu."}
                :lomake? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on lomakkeen osa."}}}
   {:id :puhelinkentta
    :nimi "Puhelinkenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :kuvaus "Puhelinnumeron syöttökenttä. Lomake-tilassa kenttä saa Bootstrapin form-control-luokan, ellei vayla-tyyli? ole päällä."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :puhelin."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon arvo tallennetaan."}
                :placeholder {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Paikkamerkkiteksti."}
                :pituus {:tyyppi :numero :pakollinen? false :oletus nil :kuvaus "Kentän merkkipituus."}
                :vayla-tyyli? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, käyttää Väylä-tyylin kenttää."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on disabloitu."}
                :lomake? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on lomakkeen osa."}}}
   {:id :valintaruutu
    :nimi "Valintaruutu"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Yksittäinen valintaruutu, jonka tila on boolean-atomi tai atomi, jonka arvo on true, false tai ::indeterminate."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :checkbox."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon valinta tallennetaan."}
                :teksti {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Ruudun vieressä näkyvä teksti."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, ruutu on disabloitu."}
                :iso-clickalue? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, koko alue on klikattava."}
                :valitse! {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan valinnan muuttuessa."}}}
   {:id :radiokentta
    :nimi "Radiokenttä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo :valinnat}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Vaihtoehdoista yksi valitaan radiopainikkeella. Arvo on atomi."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :radio."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon valinta tallennetaan."}
                :valinnat {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Valittavat vaihtoehdot."}
                :valinta-nayta {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Muodostaa vaihtoehdon näytettävän tekstin."}
                :valinta-arvo {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Muodostaa vaihtoehdon arvon."}
                :disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, kenttä on disabloitu."}}}
   {:id :checkbox-ryhma
    :nimi "Checkbox-ryhmä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :syote
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:arvo :vaihtoehdot}
    :tilat #{:normaali :tyhja :estetty}
    :sallitut-vanhemmat #{:lomake :suodatin}
    :bootstrap-tila :osittain-riippuvainen
    :bootstrap-luokat ["col-sm-"]
    :kuvaus "Monivalinta, jossa arvo on joukko. Yksi palsta ei käytä Bootstrap-luokkia, useampi palsta käyttää col-sm-*-luokkia."
    :funktio "harja.ui.kentat/tee-kentta"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Kentän tyyppi, tässä :checkbox-group."}
                :arvo {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, johon valitut arvot tallennetaan."}
                :vaihtoehdot {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Valittavat vaihtoehdot."}
                :vaihtoehto-nayta {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Muodostaa vaihtoehdon näytettävän tekstin."}
                :palstoja {:tyyppi :numero :pakollinen? false :oletus nil :kuvaus "Vaihtoehtojen palstamäärä."}
                :disabloi {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, ryhmä on disabloitu."}}}
   {:id :lomake
    :nimi "Lomake"
    :ryhma :lomakkeet
    :luonne :shared-rakenne
    :rooli :lomake
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:kentat :toiminnot :tila}
    :tilat #{:normaali :lataus :virhe :onnistui}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Pieni lomakekokonaisuus, jossa näkymä omistaa arvon ja tallennuksen ehdot."
    :funktio "harja.ui.komponentit"
    :asetukset {:lapset {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Lomakkeen sisältö: pino, kenttaryhma, valintaryhma, toimintoryhma tai tilaviesti."}}}
   {:id :rivi
    :nimi "Rivi"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :rooli :asettelu
    :suositus :vanha
    :korvaa []
    :slotit #{:sarake}
    :tilat #{:normaali}
    :sallitut-vanhemmat #{:lomake :sivu}
    :bootstrap-tila :riippuvainen
    :bootstrap-luokat ["row"]
    :kuvaus "Asettelurivi, jossa sarakkeet jakavat tilan tasan koon mukaan. Sarakkeiden koon ja rivin luokan välittää käyttöpaikka."
    :funktio "harja.ui.yleiset/rivi"
    :asetukset {:koko {:tyyppi :muu :pakollinen? true :oletus nil :kuvaus "Numero tai mäppi {:lg :md :xs}."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Rivin CSS-luokka."}
                :sarakkeet {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Rivin sarakkeet."}}}
   {:id :panel
    :nimi "Panel"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :rakenne
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:otsikko :sisalto}
    :tilat #{:normaali :tyhja}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Yhteensopiva panel-wrapper ilman Bootstrapin panel-markupia."
    :funktio "harja.ui.bootstrap/panel"
    :asetukset {:otsikko {:tyyppi :hiccup :pakollinen? false :oletus nil :kuvaus "Paneelin otsikko. Jos annettu, sisältö on toinen argumentti."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Paneelin CSS-luokka."}
                :sisalto {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Paneelin sisältö."}}}
   {:id :perusgrid
    :nimi "Perusgrid"
    :ryhma :gridit
    :luonne :shared-komponentti
    :rooli :taulukko
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:sarakkeet :rivit}
    :tilat #{:normaali :tyhja}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Taulukkonäkymä rivien ja sarakkeiden esittämiseen. Paneelin ja napin luokat ovat vielä Bootstrapin panel- ja btn-xs-luokkia."
    :funktio "harja.ui.grid/grid"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Taulukon otsikko."}
                :tyhja {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Teksti, joka näytetään kun rivejä ei ole."}
                :tunniste {:tyyppi :keyword :pakollinen? false :oletus nil :kuvaus "Taulukon tunniste."}
                :sarakkeet {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Sarakkeiden määrittelyt."}
                :rivit {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Rivit."}}}
   {:id :muokkausgrid
    :nimi "Muokkausgrid"
    :ryhma :gridit
    :luonne :shared-komponentti
    :rooli :taulukko
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:sarakkeet :rivit}
    :tilat #{:normaali :tyhja :muokkaus}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Muokattava taulukko. Rivien muutokset kulkevat käyttöpaikan muutos-callbackin kautta. Paneelin rakenne on vielä Bootstrapin panel-luokkia."
    :funktio "harja.ui.grid/muokkaus-grid"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Taulukon otsikko."}
                :tyhja {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Teksti, joka näytetään kun rivejä ei ole."}
                :voi-muokata? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, rivejä voi muokata."}
                :voi-lisata? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, rivejä voi lisätä."}
                :voi-kumota? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, muutokset voi kumota."}
                :muutos {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan muutosten yhteydessä."}
                :sarakkeet {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Sarakkeiden määrittelyt."}
                :rivit-atomi {:tyyppi :atomi :pakollinen? true :oletus nil :kuvaus "Atomi, jossa rivit ovat."}}}
   {:id :tabs
    :nimi "Tabs"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :navigaatio
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:otsikot :sisallot}
    :tilat #{:normaali :ei-kayttooikeutta}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Välilehtien public API säilyy, mutta rakenne ei enää nojaa Bootstrapin nav-luokkiin."
    :funktio "harja.ui.bootstrap/tabs"
    :asetukset {:active {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Aktiivisen välilehden avain."}
                :style {:tyyppi :map :pakollinen? false :oletus nil :kuvaus "Tyylit."}
                :classes {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "CSS-luokat."}
                :on-change {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan välilehden vaihtuessa."}
                :valilehdet {:tyyppi :vektori :pakollinen? true :oletus nil :kuvaus "Otsikot, avaimet ja sisällöt vuorotellen."}}}
   {:id :navbar
    :nimi "Navbar"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :navigaatio
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:header :vasen :oikea}
    :tilat #{:normaali :suljettu :avoin}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Navigaation yhteensopiva navbar-wrapper Harjan omilla luokilla."
    :funktio "harja.ui.bootstrap/navbar"
    :asetukset {:options {:tyyppi :map :pakollinen? false :oletus nil :kuvaus "Navigaation asetukset."}
                :header {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Navigaation otsikko."}
                :itemit {:tyyppi :hiccup :pakollinen? false :oletus nil :kuvaus "Navigaation kohdat."}
                :right {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Kohdat, jotka erotetaan oikeaan reunaan."}}}
   {:id :viesti
    :nimi "Viesti"
    :ryhma :palautteet
    :luonne :shared-komponentti
    :rooli :palaute
    :suositus :uuteen-ui
    :korvaa ["yleiset/toast-viesti" "viesti/nayta-toast!"]
    :slotit #{:sisalto}
    :tilat #{:normaali}
    :variantit #{:onnistuminen :info :varoitus :vaara}
    :yhteiset-variantit {:onnistui :success :info :info :varoitus :warning :virhe :danger}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Flash-viesti ilman Bootstrapin modal- ja alert-luokkia. Variantit tulevat Harjan omista luokista."
    :funktio "harja.ui.viesti/nayta!"
    :asetukset {:viesti {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Näytettävä viesti."}
                :luokka {:tyyppi :keyword :pakollinen? false :oletus nil :kuvaus "Viestin tyyppi: :success, :info, :warning tai :danger."}
                :kesto {:tyyppi :numero :pakollinen? false :oletus nil :kuvaus "Kuinka kauan viesti näytetään."}}}
   {:id :modal
    :nimi "Modal"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :overlay
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:otsikko :sisalto}
    :tilat #{:avoin :suljettu}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :bootstrap-luokat ["modal" "modal-dialog" "modal-content"]
    :kuvaus "Modaali näytetään sivun päätason modal-kontissa. Avaamisen ehdot ja sulkemisen seuraukset omistaa näkymä."
    :funktio "harja.ui.modal/nayta!"
    :asetukset {:otsikko {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Modaalin otsikko."}
                :sulje {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan modaalin sulkeutuessa."}
                :sulje-fn {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan sulkemispainikkeen tai sulkemisen yhteydessä."}
                :sulje-ruksista-fn {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan, kun modaali suljetaan ruksista."}
                :modaalin-fokus-elementti {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Elementti, joka saa fokuksen modaalin avautuessa."}
                :leveys {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Modaalin leveys."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Modaalin CSS-luokka."}
                :footer {:tyyppi :hiccup :pakollinen? false :oletus nil :kuvaus "Modaalin alaosa."}
                :sisalto {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Modaalin sisältö."}}}
   {:id :dropdown-panel
    :nimi "Dropdown-panel"
    :ryhma :portatut
    :luonne :shared-komponentti
    :rooli :rakenne
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:otsikko :sisalto}
    :tilat #{:auki :kiinni}
    :sallitut-vanhemmat #{:sivu}
    :bootstrap-tila :riippuvainen
    :kuvaus "Otsikolla varustettu paneeli, jonka sisältö näkyy vain auki ollessa. Ei käytössä vielä missään näkymässä."
    :funktio "harja.ui.bootstrap/dropdown-panel"
    :asetukset {:open {:tyyppi :atomi :pakollinen? false :oletus nil :kuvaus "Atomi, jossa avoin-tila on."}
                :style {:tyyppi :map :pakollinen? false :oletus nil :kuvaus "Tyylit."}
                :otsikko {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Paneelin otsikko."}
                :sisalto {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Paneelin sisältö, näytetään kun paneeli on auki."}}}
   {:id :ajax-loader
    :nimi "Latausindikaattori"
    :ryhma :palautteet
    :luonne :shared-komponentti
    :rooli :palaute
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:viesti}
    :tilat #{:lataus}
    :sallitut-vanhemmat #{:sivu :lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Latauskuva ja optionaalinen viesti. Näkyy, kun sisältö on hakuvaiheessa."
    :funktio "harja.ui.yleiset/ajax-loader"
    :asetukset {:viesti {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Latausviesti."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "CSS-luokka."}
                :sama-rivi? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Jos true, indikaattori on samalla rivillä."}}}
   {:id :vihje
    :nimi "Pikkuvihje"
    :ryhma :palautteet
    :luonne :shared-komponentti
    :rooli :palaute
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:teksti}
    :tilat #{:normaali}
    :sallitut-vanhemmat #{:sivu :lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Kevyt ohjeteksti kuvakkeella. Käytetään kenttien ja otsikoiden yhteydessä."
    :funktio "harja.ui.yleiset/vihje"
    :asetukset {:teksti {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Vihjeen teksti."}
                :luokka {:tyyppi :keyword :pakollinen? false :oletus nil :kuvaus "Vihjeen tyyppi."}
                :ikonin-koko {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Ikonin koko."}}}
   {:id :info-laatikko
    :nimi "Info-laatikko"
    :ryhma :palautteet
    :luonne :shared-komponentti
    :rooli :palaute
    :suositus :ei-suositusta
    :korvaa []
    :slotit #{:ensisijainen :toissijainen}
    :tilat #{:normaali}
    :variantit #{:varoitus :onnistunut :neutraali :vahva-ilmoitus :huolto}
    :yhteiset-variantit {:onnistui :onnistunut :info :neutraali :varoitus :varoitus}
    :sallitut-vanhemmat #{:sivu :lomake}
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Tyypitelty ilmoitus ensisijaisella ja toissijaisella viestillä. Sulkeminen on valinnainen."
    :funktio "harja.ui.yleiset/info-laatikko"
    :asetukset {:tyyppi {:tyyppi :keyword :pakollinen? true :oletus nil :kuvaus "Laatikon tyyppi."}
                :ensisijainen-viesti {:tyyppi :hiccup :pakollinen? true :oletus nil :kuvaus "Pääviesti."}
                :toissijainen-viesti {:tyyppi :hiccup :pakollinen? false :oletus nil :kuvaus "Toissijainen viesti."}
                :leveys {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Laatikon leveys."}
                :luokka {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "CSS-luokka."}
                :sulje-fn {:tyyppi :funktio :pakollinen? false :oletus nil :kuvaus "Kutsutaan laatikon sulkemisen yhteydessä."}
                :sulje-nappi-id {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Sulkemisnapin id."}
                :reunan-voimakkuus {:tyyppi :muu :pakollinen? false :oletus nil :kuvaus "Reunan voimakkuus."}}}])

(defn ankkuri
  "Komponentin vakaa juuriankkuri (data-cy). Johdetaan id:stä, ei tallenneta erikseen."
  [{:keys [id]}]
  (str ankkuri-etuliite (name id)))

(defn api-teksti
  "Muodostaa komponentin :asetukset-kartasta ihmisluettavan API-kuvauksen.
  Pakolliset asetukset merkitään sulkeissa."
  [{:keys [funktio asetukset]}]
  (str funktio ": {"
       (str/join ", "
                 (for [[avain {:keys [tyyppi pakollinen?]}] (sort-by key asetukset)]
                   (str avain " " tyyppi (when pakollinen? " (pakollinen)"))))
       "}"))

(defn hae
  "Komponentti id:llä tai nil."
  ([id] (hae komponentit id))
  ([komponentit id]
   (first (filter #(= id (:id %)) komponentit))))

(defn ryhmittele
  "Palauttaa vain ne ryhmät, joissa on komponentteja: [{:id :nimi :komponentit [...]}]."
  ([] (ryhmittele ryhmat komponentit))
  ([ryhmat komponentit]
   (into []
     (keep (fn [{:keys [id] :as ryhma}]
             (when-let [ryhman-komponentit (seq (filter #(= id (:ryhma %)) komponentit))]
               (assoc ryhma :komponentit (vec ryhman-komponentit)))))
     ryhmat)))

(def suositukset
  "Mitä uudessa UI:ssa suositellaan: uuden komponentin käyttöä, vanhaa vastinetta tai ei suositusta."
  #{:uuteen-ui :vanha :ei-suositusta})

(def uusien-merkintojen-kielletyt-avaimet
  "Vanhoja asetusavaimia, joita uusi merkintä ei käytä. Vanhat merkinnät saavat käyttää niitä."
  #{:class :classes :disabled? :disabloi :valitse-fn :valinta-fn})

(def palaute-variantit
  "Palautekomponenttien yhteinen varianttisanasto. Palautekomponentti, jolla on `:variantit`,
  kertoo `:yhteiset-variantit`-kartassa, minkä oman arvonsa se käyttää kustakin yhteisestä variantista.
  Kartta voi olla osittainen, kun komponentilla ei ole vastinetta kaikille variantille."
  #{:onnistui :info :varoitus :virhe})

(defn- uusien-merkintojen-nimeamisvirheet
  "Uudessa merkinnässä asetusnimien on noudatettava yhtenäistä nimeämistä.
  `:nimeamispoikkeukset` listaa jo käytössä olevat asetukset, joiden nimeä ei voi muuttaa."
  [{:keys [id suositus asetukset nimeamispoikkeukset]}]
  (when (= :uuteen-ui suositus)
    (concat
      (for [avain (keys asetukset)
            :when (and (uusien-merkintojen-kielletyt-avaimet avain)
                       (not (contains? nimeamispoikkeukset avain)))]
        (str id ": kielletty asetusnimi " avain))
      (for [[avain {:keys [tyyppi]}] asetukset
            :let [nimi (name avain)]
            virhe [(when (and (str/ends-with? nimi "-fn") (not= :funktio tyyppi))
                     (str id ": " avain " on -fn-asetus, tyypin pitää olla :funktio"))
                   (when (and (str/ends-with? nimi "?") (not= :boolean tyyppi))
                     (str id ": " avain " on ?-asetus, tyypin pitää olla :boolean"))]
            :when virhe]
        virhe))))

(defn- nimeamispoikkeusten-virheet
  "Poikkeus on sallittu vain asetukselle, joka on komponentilla ja jonka nimi on kielletty."
  [{:keys [id asetukset nimeamispoikkeukset]}]
  (when (some? nimeamispoikkeukset)
    (if (set? nimeamispoikkeukset)
      (for [avain nimeamispoikkeukset
            :when (not (and (contains? asetukset avain)
                            (uusien-merkintojen-kielletyt-avaimet avain)))]
        (str id ": turha nimeämispoikkeus " avain))
      [(str id ": nimeamispoikkeukset on oltava joukko")])))

(defn- yhteisten-varianttien-virheet
  "Palautekomponentin `:variantit` vaativat `:yhteiset-variantit`-kartan, jonka avaimet kuuluvat
  yhteiseen sanastoon."
  [{:keys [id rooli variantit yhteiset-variantit]}]
  (let [vieraat (remove palaute-variantit (keys yhteiset-variantit))]
    (concat
      (when (and (= :palaute rooli) (seq variantit) (not (seq yhteiset-variantit)))
        [(str id ": palautekomponentin variantit vaativat yhteiset-variantit-kartan")])
      (when (and (some? yhteiset-variantit) (not (map? yhteiset-variantit)))
        [(str id ": yhteiset-variantit on oltava kartta")])
      (when (and (map? yhteiset-variantit) (seq vieraat))
        [(str id ": tuntemattomat yhteiset variantit " (vec vieraat))]))))

(defn virheet
  "Palauttaa rekisterin eheysongelmat tekstinä. Tyhjä lista tarkoittaa, että rekisteri on kunnossa."
  ([] (virheet ryhmat komponentit))
  ([ryhmat komponentit]
   (let [ryhma-idt (set (map :id ryhmat))
         vanhempi-idt (into #{:sivu} (map :id) komponentit)
         tuplat (fn [avain]
                  (->> komponentit (map avain) frequencies
                    (keep (fn [[arvo maara]] (when (> maara 1) arvo)))))
         joukko? (fn [arvo] (and (set? arvo) (seq arvo)))]
     (vec
       (concat
         (for [arvo (tuplat :id)] (str "Tuplaid: " arvo))
         (for [{:keys [id ryhma luonne bootstrap-tila bootstrap-luokat nimi kuvaus funktio asetukset rooli slotit tilat
                       sallitut-vanhemmat variantit suositus korvaa] :as komponentti} komponentit
               virhe [(when-not (contains? suositukset suositus)
                        (str id ": tuntematon tai puuttuva suositus " suositus))
                      (when-not (and (vector? korvaa) (every? string? korvaa))
                        (str id ": korvaa on oltava tekstivektori"))
                      (when-not (contains? ryhma-idt ryhma) (str id ": tuntematon ryhmä " ryhma))
                      (when-not (contains? luonteet luonne) (str id ": tuntematon luonne " luonne))
                      (when-not (contains? bootstrap-tilat bootstrap-tila)
                        (str id ": tuntematon bootstrap-tila " bootstrap-tila))
                      (when-not (or (nil? bootstrap-luokat) (and (vector? bootstrap-luokat) (every? string? bootstrap-luokat)))
                        (str id ": bootstrap-luokat on oltava tekstivektori"))
                      (when (and (= :ei-riippuvuutta bootstrap-tila) (seq bootstrap-luokat))
                        (str id ": ei-riippuvuutta-komponentilla ei voi olla bootstrap-luokat"))
                      (when-not (every? seq [nimi kuvaus funktio]) (str id ": nimi, kuvaus tai funktio puuttuu"))
                      (when-not (and (map? asetukset) (seq asetukset)) (str id ": asetukset puuttuvat"))
                      (when-let [virheelliset (seq (remove (comp asetustyypit :tyyppi val) asetukset))]
                        (str id ": tuntemattomat asetustyypit " (mapv (comp :tyyppi val) virheelliset)))
                      (when-not (contains? roolit rooli) (str id ": tuntematon tai puuttuva rooli " rooli))
                      (when-not (joukko? slotit) (str id ": slotit puuttuvat tai ovat tyhjät"))
                      (when-not (joukko? tilat) (str id ": tilat puuttuvat tai ovat tyhjät"))
                      (when-not (joukko? sallitut-vanhemmat)
                        (str id ": sallitut-vanhemmat puuttuvat tai ovat tyhjät"))
                      (when-let [tuntemattomat (seq (remove vanhempi-idt sallitut-vanhemmat))]
                        (str id ": tuntemattomat vanhemmat " (vec tuntemattomat)))
                      (when (and (contains? komponentti :variantit) (not (joukko? variantit)))
                        (str id ": variantit ovat tyhjät tai eivät ole joukko"))
                      (when (and (joukko? variantit) (some #(contains? tilat %) variantit))
                        (str id ": variantit ja tilat menevät päällekkäin"))]
               :when virhe]
           virhe)
         (for [komponentti komponentit
               virhe (concat (uusien-merkintojen-nimeamisvirheet komponentti)
                             (nimeamispoikkeusten-virheet komponentti)
                             (yhteisten-varianttien-virheet komponentti))]
           virhe))))))
