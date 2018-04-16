(ns harja.views.tieluvat.tieluvat
  (:require
    [tuck.core :refer [tuck]]
    [harja.loki :refer [log]]
    [harja.tiedot.tieluvat.tieluvat :as tiedot]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
    [harja.ui.grid :as grid]
    [harja.views.kartta :as kartta]
    [harja.ui.debug :as debug]
    [harja.ui.valinnat :as valinnat]
    [harja.ui.kentat :as kentat]

    [harja.domain.tielupa :as tielupa]
    [harja.ui.napit :as napit]
    [clojure.string :as str]
    [harja.pvm :as pvm])
  (:require-macros
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn hakijan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Hakijan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/hakija-nimi}
    {:otsikko "Osasto"
     :tyyppi :string
     :nimi ::tielupa/hakija-osasto}
    {:otsikko "Postinosoite"
     :tyyppi :string
     :nimi ::tielupa/hakija-postinosoite}
    {:otsikko "Postinumero"
     :tyyppi :string
     :nimi ::tielupa/hakija-postinumero}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/hakija-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/hakija-sahkopostiosoite}
    {:otsikko "Tyyppi"
     :tyyppi :string
     :nimi ::tielupa/hakija-tyyppi}
    {:otsikko "Maakoodi"
     :tyyppi :string
     :nimi ::tielupa/hakija-maakoodi}))

(defn urakoitsijan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Urakoitsijan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-nimi}
    {:otsikko "Yhteyshenkilö"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-yhteyshenkilo}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/urakoitsija-sahkopostiosoite}))

(defn liikenneohjaajan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liikenneohjaajan tiedot"}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-nimi}
    {:otsikko "Yhteyshenkilö"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-yhteyshenkilo}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/liikenneohjaajan-sahkopostiosoite}))

(defn tienpitoviranomaisen-tiedot [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Tienpitoviranomaisen tiedot"}
    {:otsikko "Puhelinnumero"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-puhelinnumero}
    {:otsikko "Sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-sahkopostiosoite}
    {:otsikko "Lupapäällikkö"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-lupapaallikko}
    {:otsikko "Käsittelijä"
     :tyyppi :string
     :nimi ::tielupa/tienpitoviranomainen-kasittelija}))

(defn johtoluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Johtolupa"}
    {:otsikko "Maakaapelia yhteensä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-maakaapelia-yhteensa}
    {:otsikko "Ilmakaapelia yhteensä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-ilmakaapelia-yhteensa}
    {:otsikko "Tienalituksia"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-tienalituksia}
    {:otsikko "Tienylityksiä"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-tienylityksia}
    {:otsikko "Silta-asennuksia"
     :tyyppi :string
     :nimi ::tielupa/johtolupa-silta-asennuksia}))

(defn liittymaluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liittymälupa"}
    {:otsikko "Myönnetty käyttötarkoitus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-myonnetty-kayttotarkoitus}
    {:otsikko "Haettu käyttötarkoitus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-haettu-kayttotarkoitus}
    {:otsikko "Liittymän siirto?"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittyman-siirto}
    {:otsikko "Tarkoituksen kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-tarkoituksen-kuvaus}
    {:otsikko "Tilapäinen liittymälupa?"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-tilapainen}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-sijainnin-kuvaus}
    {:otsikko "Arvioitu kokonaisliikenne"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-arvioitu-kokonaisliikenne}
    {:otsikko "Arvioitu kuorma-autoliikenne"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-arvioitu-kuorma-autoliikenne}
    {:otsikko "Nykyisen liittymän numero"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-nykyisen-liittyman-numero}
    {:otsikko "Nykyisen liittymän päiväys"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-nykyisen-liittyman-paivays}
    {:otsikko "Kiinteistö rekisterinumero"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-kiinteisto-rn}
    {:otsikko "Muut kulkuyhteydet"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-muut-kulkuyhteydet}
    {:otsikko "Valmistumisen takaraja"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-valmistumisen-takaraja}
    {:otsikko "Kylä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-kyla}
    {:otsikko "Liittymäkaari"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liittymakaari}
    {:otsikko "Leveys metreissä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-leveys-metreissa}
    {:otsikko "Rumpu?"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-rumpu}
    {:otsikko "Rummun halkaisija (mm)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa}
    {:otsikko "Rummun etäisyys (m)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-rummun-etaisyys-metreissa}
    {:otsikko "Odotustila (m)"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-odotustila-metreissa}
    {:otsikko "Näkemäpisteen etäisyys"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-nakemapisteen-etaisyys}
    {:otsikko "Liittymisnäkemä"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liittymisnakema}
    {:otsikko "Liikennemerkit"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-liikennemerkit}
    {:otsikko "Lisäohjeet"
     :tyyppi :string
     :nimi ::tielupa/liittymalupa-liittymaohje-lisaohjeet}))

(defn mainosluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Mainoslupa"}
    {:otsikko "Mainostettava asia"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-mainostettava-asia}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-sijainnin-kuvaus}
    {:otsikko "Korvaava päätös"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-korvaava-paatos}
    {:otsikko "Tiedoksi ELY-keskukselle"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-tiedoksi-elykeskukselle}
    {:otsikko "Asemakaava alueella"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-asemakaava-alueella}
    {:otsikko "Suoja-alueen leveys"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-suoja-alueen-leveys}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/mainoslupa-lisatiedot}))

(defn opasteluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Opastelupa"}
    {:otsikko "Kohteen nimi"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-kohteen-nimi}
    {:otsikko "Palvelukohteen opastaulu"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-palvelukohteen-opastaulu}
    {:otsikko "Palvelukohteen osoiteviitta"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-palvelukohteen-osoiteviitta}
    {:otsikko "Osoiteviitta"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-osoiteviitta}
    {:otsikko "Ennakkomerkki"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-ennakkomerkki}
    {:otsikko "Opasteen teksti"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-opasteen-teksti}
    {:otsikko "Osoiteviitan tunnus"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-osoiteviitan-tunnus}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-lisatiedot}
    {:otsikko "URL"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-kohteen-url-osoite}
    {:otsikko "Jatkolupa?"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-jatkolupa}
    {:otsikko "Alkuperäinen lupanumero"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-alkuperainen-lupanro}
    {:otsikko "Alkuperäisen luvan alkupäivämäärä"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-alkuperaisen-luvan-alkupvm}
    {:otsikko "Alkuperäisen luvan loppupäivämäärä"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-alkuperaisen-luvan-loppupvm}
    {:otsikko "Nykyinen opastus"
     :tyyppi :string
     :nimi ::tielupa/opastelupa-nykyinen-opastus}))

(defn suoja-aluerakentamisluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Suoja-aluerakentamislupa"}
    {:otsikko "Rakennettava asia"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-rakennettava-asia}
    {:otsikko "Lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-lisatiedot}
    {:otsikko "Esitetty etäisyys tien keskilinjaan"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan}
    {:otsikko "Vähimmäisetäisyys tien keskilinjasta"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta}
    {:otsikko "Leveys"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-suoja-alueen-leveys}
    {:otsikko "Sijoitus"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-sijoitus}
    {:otsikko "Kiinteistön rekisterinumero"
     :tyyppi :string
     :nimi ::tielupa/suoja-aluerakentamislupa-kiinteisto-rn}))

(defn myyntiluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Myyntilupa"}
    {:otsikko "Aihe"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-aihe}
    {:otsikko "Alueen nimi"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-alueen-nimi}
    {:otsikko "Aikaisempi myyntilupa"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-aikaisempi-myyntilupa}
    {:otsikko "Opastusmerkit"
     :tyyppi :string
     :nimi ::tielupa/myyntilupa-opastusmerkit}))

(defn liikennemerkkijarjestelyn-lomaketiedot [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Liikennemerkkijärjestely"}
    {:otsikko "Aihe"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-aihe}
    {:otsikko "Sijainnin kuvaus"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-sijainnin-kuvaus}
    {:otsikko "Tapahtuman tiedot"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-tapahtuman-tiedot}
    {:otsikko "Lisätiedot nopeusrajoituksesta"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta}
    {:otsikko "Muut liikennemerkit"
     :tyyppi :string
     :nimi ::tielupa/liikennemerkkijarjestely-muut-liikennemerkit}))

(defn tyoluvan-lomaketiedot [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Työlupa"}
    {:otsikko "Työn sisältö"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tyon-sisalto}
    {:otsikko "Työn saa aloittaa"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tyon-saa-aloittaa}
    {:otsikko "Viimeistelty oltava"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-viimeistely-oltava}
    {:otsikko "Ohjeet työn suorittamiseen"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-ohjeet-tyon-suorittamiseen}
    {:otsikko "Loppuosa puuttuu?"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-los-puuttuu}
    {:otsikko "Ilmoitus tieliikennekeskukseen?"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-ilmoitus-tieliikennekeskukseen}
    {:otsikko "Tilapäinen nopeusrajoitus?"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tilapainen-nopeusrajoitus}
    {:otsikko "Loppuosa, lisätiedot"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-los-lisatiedot}
    {:otsikko "Tieliikennekeskuksen sähköpostiosoite"
     :tyyppi :string
     :nimi ::tielupa/tyolupa-tieliikennekusksen-sahkopostiosoite}))

(defn vesihuoltoluvan-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Vesihuoltolupa"}
    {:otsikko "Tienylityksiä"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-tienylityksia}
    {:otsikko "Tienalituksia"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-tienalituksia}
    {:otsikko "Silta-asennuksia"
     :tyyppi :string
     :nimi ::tielupa/vesihuoltolupa-silta-asennuksia}))

(defn valmistumisilmoituksen-lomakekentat [valittu-tielupa]
  (lomake/ryhma
    {:otsikko "Valmistumisilmoitus"}
    {:otsikko "Valmistumisilmoitus vaaditaan?"
     :tyyppi :string
     :nimi ::tielupa/valmistumisilmoitus-vaaditaan}
    {:otsikko "Valmistumisilmoitus palautettu?"
     :tyyppi :string
     :nimi ::tielupa/valmistumisilmoitus-palautettu}
    {:otsikko "Valmistumisilmoitus"
     :tyyppi :string
     :nimi ::tielupa/valmistumisilmoitus}))

(defn tielupalomake [e! {:keys [valittu-tielupa] :as app}]
  (let [uusi? false]
    [:div
    [debug/debug app]
    [napit/takaisin "Takaisin lupataulukkoon" #(e! (tiedot/->ValitseTielupa nil))]
     [lomake/lomake
      {:luokka "ryhma-reuna"
       :voi-muokata? false}
      [{:otsikko "Ulkoinen tunniste"
        :tyyppi :string
        :nimi ::tielupa/ulkoinen-tunniste}
       {:otsikko "Diaarinumero"
        :tyyppi :string
        :nimi ::tielupa/paatoksen-diaarinumero}
       {:otsikko "Saapumispäivämäärä"
        :tyyppi :string
        :nimi ::tielupa/saapumispvm}
       {:otsikko "Myöntämispäivämäärä"
        :tyyppi :string
        :nimi ::tielupa/myontamispvm}
       {:otsikko "Voimassaolon alkupäivämäärä"
        :tyyppi :string
        :nimi ::tielupa/voimassaolon-alkupvm}
       {:otsikko "Voimassaolon loppupäivämäärä"
        :tyyppi :string
        :nimi ::tielupa/voimassaolon-loppupvm}
       {:otsikko "Otsikko"
        :tyyppi :string
        :nimi ::tielupa/otsikko}
       {:otsikko "Katselmus URL"
        :tyyppi :string
        :nimi ::tielupa/katselmus-url}
       {:otsikko "Urakka"
        :tyyppi :string
        :nimi ::tielupa/urakan-nimi}
       {:otsikko "Kunta"
        :tyyppi :string
        :nimi ::tielupa/kunta}
       {:otsikko "Lähiosoite"
        :tyyppi :string
        :nimi ::tielupa/kohde-lahiosoite}
       {:otsikko "Postinumero"
        :tyyppi :string
        :nimi ::tielupa/kohde-postinumero}
       {:otsikko "Postitoimipaikka"
        :tyyppi :string
        :nimi ::tielupa/kohde-postitoimipaikka}
       {:otsikko "Tien nimi"
        :tyyppi :string
        :nimi ::tielupa/tien-nimi}
       (when (tiedot/nayta-hakijan-tiedot? valittu-tielupa)
         (hakijan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-urakoitsijan-tiedot? valittu-tielupa)
         (urakoitsijan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-liikenneohjaajan-tiedot? valittu-tielupa)
         (liikenneohjaajan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-tienpitoviranomaisen-tiedot? valittu-tielupa)
         (tienpitoviranomaisen-tiedot valittu-tielupa))
       (when (tiedot/nayta-valmistumisilmoituksen-tiedot? valittu-tielupa)
         (valmistumisilmoituksen-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-johtoluvan-tiedot? valittu-tielupa)
         (johtoluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-liittymaluvan-tiedot? valittu-tielupa)
         (liittymaluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-mainosluvan-tiedot? valittu-tielupa)
         (mainosluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-opasteluvan-tiedot? valittu-tielupa)
         (opasteluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-suoja-aluerakentamisluvan-tiedot? valittu-tielupa)
         (suoja-aluerakentamisluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-myyntiluvan-tiedot? valittu-tielupa)
         (myyntiluvan-lomakekentat valittu-tielupa))
       (when (tiedot/nayta-liikennemerkkijarjestelyn-tiedot? valittu-tielupa)
         (liikennemerkkijarjestelyn-lomaketiedot valittu-tielupa))
       (when (tiedot/nayta-tyoluvan-tiedot? valittu-tielupa)
         (tyoluvan-lomaketiedot valittu-tielupa))
       (when (tiedot/nayta-vesihuoltoluvan-tiedot? valittu-tielupa)
         (vesihuoltoluvan-lomakekentat valittu-tielupa))
       #_{:otsikko "Mainokset"
        :nimi ::tielupa/mainokset}
       #_{:otsikko "Liikennemerkkijärjestelyt"
        :nimi ::tielupa/liikennemerkkijarjestelyt}
       #_{:otsikko "Johtoasennukset"
        :nimi ::tielupa/johtoasennukset}
       #_{:otsikko "Kaapeliasennukset"
        :nimi ::tielupa/kaapeliasennukset}
       #_{:otsikko "Opasteet"
        :nimi ::tielupa/opasteet}]
      valittu-tielupa]]))

(defn suodattimet [e! app]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [valinnat/valintaryhmat-3
      [:div
       [kentat/tee-otsikollinen-kentta {:otsikko "Tierekisteriosoiteväli"
                                        :kentta-params {:tyyppi :tierekisteriosoite
                                                        :sijainti (atomi :sijainti)}
                                        :arvo-atom (atomi :tr)}]]
      [:div
       [kentat/tee-otsikollinen-kentta {:otsikko "Luvan numero"
                                        :kentta-params {:tyyppi :string}
                                        :arvo-atom (atomi :luvan-numero)}]
       [kentat/tee-otsikollinen-kentta {:otsikko "Lupatyyppi"
                                        :kentta-params {:tyyppi :valinta
                                                        :valinnat (into [nil] (sort tielupa/lupatyyppi-vaihtoehdot))
                                                        :valinta-nayta #(or (tielupa/tyyppi-fmt %) "- Ei käytössä -")}
                                        :arvo-atom (atomi :lupatyyppi)}]
       [kentat/tee-otsikollinen-kentta {:otsikko "Hakija"
                                        :kentta-params {:tyyppi :haku
                                                        :nayta ::tielupa/hakija-nimi
                                                        :hae-kun-yli-n-merkkia 2
                                                        :lahde tiedot/hakijahaku}
                                        :arvo-atom (atomi :hakija)}]]

      [:div
       [valinnat/aikavali (atomi :myonnetty) {:otsikko "Myönnetty välillä"}]
       [valinnat/aikavali (atomi :voimassaolo) {:otsikko "Voimassaolon aikaväli"}]]]]))

(defn tielupataulukko [e! {:keys [haetut-tieluvat tielupien-haku-kaynnissa?] :as app}]
  [:div
   [kartta/kartan-paikka]
   [debug/debug app]
   [suodattimet e! app]
   [grid/grid
    {:otsikko "Tienpidon luvat"
     :tunniste ::tielupa/id
     :sivuta grid/vakiosivutus
     :rivi-klikattu #(e! (tiedot/->ValitseTielupa %))
     :tyhja (if tielupien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Myönnetty"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/myontamispvm}
     {:otsikko "Voimassaolon alku"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-alkupvm}
     {:otsikko "Voimassaolon loppu"
      :leveys 1
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :nimi ::tielupa/voimassaolon-loppupvm}
     {:otsikko "Lupatyyppi"
      :leveys 1
      :tyyppi :string
      :nimi ::tielupa/tyyppi
      :fmt tielupa/tyyppi-fmt}
     {:otsikko "Hakija"
      :leveys 1
      :tyyppi :string
      :nimi ::tielupa/hakija-nimi}
     {:otsikko "Luvan numero"
      :leveys 1
      :tyyppi :positiivinen-numero
      :nimi ::tielupa/ulkoinen-tunniste}
     {:otsikko "Tie"
      :leveys 1
      :tyyppi :string
      :nimi :tie
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map ::tielupa/tie sijainnit))))}
     {:otsikko "Alku"
      :leveys 1
      :tyyppi :string
      :nimi :alkuosa
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map (comp
                                     (partial str/join "/")
                                     (juxt ::tielupa/aosa ::tielupa/aet))
                                   sijainnit))))}
     {:otsikko "Loppu"
      :leveys 1
      :tyyppi :string
      :nimi :loppuosa
      :hae (fn [rivi]
             (let [sijainnit (::tielupa/sijainnit rivi)]
               (str/join ", " (map (comp
                                     (partial str/join "/")
                                     (juxt ::tielupa/losa ::tielupa/let))
                                   sijainnit))))}]
    haetut-tieluvat]])

(defn tieluvat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeTieluvat)))
                      #(do (e! (tiedot/->Nakymassa? false))))
    (fn [e! app]
      (if-not (:valittu-tielupa app)
        [tielupataulukko e! app]
        [tielupalomake e! app]))))

(defc tieluvat []
  [tuck tiedot/tila tieluvat*])