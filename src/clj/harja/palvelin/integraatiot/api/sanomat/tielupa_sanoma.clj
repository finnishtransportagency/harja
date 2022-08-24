(ns harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma
  (:require [harja.domain.tielupa :as tielupa]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]))

(defn nil-turvallinen-bigdec [arvo]
  (when (not (nil? arvo))
    (bigdec arvo)))

(defn perustiedot [{perustiedot :perustiedot}]
  {::tielupa/ulkoinen-tunniste (get-in perustiedot [:tunniste :id])
   ::tielupa/tyyppi (keyword (:tyyppi perustiedot))
   ::tielupa/paatoksen-diaarinumero (:paatoksen-diaarinumero perustiedot)
   ::tielupa/saapumispvm (json-tyokalut/aika-string->java-sql-date (:saapumispvm perustiedot))
   ::tielupa/myontamispvm (json-tyokalut/aika-string->java-sql-date (:myontamispvm perustiedot))
   ::tielupa/voimassaolon-alkupvm (json-tyokalut/aika-string->java-sql-date (:voimassaolon-alkupvm perustiedot))
   ::tielupa/voimassaolon-loppupvm (json-tyokalut/aika-string->java-sql-date (:voimassaolon-loppupvm perustiedot))
   ::tielupa/otsikko (:otsikko perustiedot)
   ::tielupa/liite-url (:liite-url perustiedot)
   ::tielupa/katselmus-url (:katselmus-url perustiedot)
   ::tielupa/urakoiden-nimet (if-let [urakoiden-nimet (:alueurakka perustiedot)]
                               urakoiden-nimet
                               [])
   ::tielupa/kunta (:kunta perustiedot)
   ::tielupa/kohde-lahiosoite (:kohteen-lahiosoite perustiedot)
   ::tielupa/kohde-postitoimipaikka (:kohteen-postitoimipaikka perustiedot)
   ::tielupa/kohde-postinumero (:kohteen-postinumero perustiedot)
   ::tielupa/tien-nimi (:tien-nimi perustiedot)})

(defn sijainti [sijainti]
  {::tielupa/tie (:numero sijainti)
   ::tielupa/aosa (:aosa sijainti)
   ::tielupa/aet (:aet sijainti)
   ::tielupa/losa (:losa sijainti)
   ::tielupa/let (:let sijainti)
   ::tielupa/ajorata (:ajorata sijainti)
   ::tielupa/kaista (:kaista sijainti)
   ::tielupa/puoli (:puoli sijainti)})

(defn sijainnit [sijainnit]
  {::tielupa/sijainnit (mapv #(sijainti (:sijainti %)) sijainnit)})

(defn hakijan-tiedot [hakija]
  {::tielupa/hakija-nimi (:nimi hakija)
   ::tielupa/hakija-osasto (:osasto hakija)
   ::tielupa/hakija-postinosoite (:postiosoite hakija)
   ::tielupa/hakija-postinumero (:postinumero hakija)
   ::tielupa/hakija-puhelinnumero (:puhelinnumero hakija)
   ::tielupa/hakija-sahkopostiosoite (:sahkopostiosoite hakija)
   ::tielupa/hakija-tyyppi (:tyyppi hakija)
   ::tielupa/hakija-maakoodi (:maakoodi hakija)})

(defn urakoitsijan-tiedot [urakoitsija]
  {::tielupa/urakoitsija-nimi (:nimi urakoitsija)
   ::tielupa/urakoitsija-yhteyshenkilo (:yhteyshenkilo urakoitsija)
   ::tielupa/urakoitsija-puhelinnumero (:puhelinnumero urakoitsija)
   ::tielupa/urakoitsija-sahkopostiosoite (:sahkopostiosoite urakoitsija)})

(defn liikenneohjaajan-tiedot [liikenneohjaaja]
  {::tielupa/liikenneohjaajan-nimi (:nimi liikenneohjaaja)
   ::tielupa/liikenneohjaajan-yhteyshenkilo (:yhteyshenkilo liikenneohjaaja)
   ::tielupa/liikenneohjaajan-puhelinnumero (:puhelinnumero liikenneohjaaja)
   ::tielupa/liikenneohjaajan-sahkopostiosoite (:sahkopostiosoite liikenneohjaaja)})

(defn tienpitoviranomaisen-tiedot [tienpitoviraonomainen]
  {::tielupa/tienpitoviranomainen-yhteyshenkilo (:yhteyshenkilo tienpitoviraonomainen)
   ::tielupa/tienpitoviranomainen-puhelinnumero (:puhelinnumero tienpitoviraonomainen)
   ::tielupa/tienpitoviranomainen-sahkopostiosoite (:sahkopostiosoite tienpitoviraonomainen)
   ::tielupa/tienpitoviranomainen-lupapaallikko (:lupapaallikko tienpitoviraonomainen)
   ::tielupa/tienpitoviranomainen-kasittelija (:kasittelija tienpitoviraonomainen)})

(defn valmistumisilmoitus [valmistumisilmoitus]
  {::tielupa/valmistumisilmoitus (:valmistumisilmoitus valmistumisilmoitus)
   ::tielupa/valmistumisilmoitus-palautettu (:palautettu valmistumisilmoitus)
   ::tielupa/valmistumisilmoitus-vaaditaan (:vaaditaan valmistumisilmoitus)})

(defn kaapeliasennukset [kaapeliasennukset]
  (mapv (fn [{kaapeliasennus :kaapeliasennus}]
          (merge {::tielupa/laite (:laite kaapeliasennus)
                  ::tielupa/asennustyyppi (:asennustyyppi kaapeliasennus)
                  ::tielupa/kommentit (:kommentit kaapeliasennus)
                  ::tielupa/maakaapelia-metreissa (nil-turvallinen-bigdec (:maakaapelia-metreissa kaapeliasennus))
                  ::tielupa/ilmakaapelia-metreissa (nil-turvallinen-bigdec (:ilmakaapelia-metreissa kaapeliasennus))
                  ::tielupa/nopeusrajoitus (:nopeusrajoitus kaapeliasennus)
                  ::tielupa/liikennemaara (nil-turvallinen-bigdec (:liikennemaara kaapeliasennus))}
                 (sijainti (:sijainti kaapeliasennus))))
        kaapeliasennukset))

(defn johto-ja-kaapelilupa [johto-ja-kaapelilupa]
  {::tielupa/johtolupa-maakaapelia-yhteensa (nil-turvallinen-bigdec (:maakaapelia-yhteensa johto-ja-kaapelilupa))
   ::tielupa/johtolupa-ilmakaapelia-yhteensa (nil-turvallinen-bigdec (:ilmakaapelia-yhteensa johto-ja-kaapelilupa))
   ::tielupa/johtolupa-tienalituksia (:tienalituksia johto-ja-kaapelilupa)
   ::tielupa/johtolupa-tienylityksia (:tienylityksia johto-ja-kaapelilupa)
   ::tielupa/johtolupa-silta-asennuksia (:silta-asennuksia johto-ja-kaapelilupa)
   ::tielupa/kaapeliasennukset (kaapeliasennukset (:kaapeliasennukset johto-ja-kaapelilupa))})

(defn mapin-tosi-arvolliset-avaimet [m]
  (for [k (keys m)
        :let [v (k m)]]
    (when (true? v) k)))

(defn muunna-sanoman-kayttotarkoitus [m]
  (let [sanoman-kt (first (mapin-tosi-arvolliset-avaimet m))
        muunnokset {:lomakiinteistolle-kulkuun "lomakiinteistolle-kulku"
                    :maa-ja-metsatalousajoon "maa-ja-metsatalousajo",
                    :asuinkiinteistolle-kulkuun "asuinkiinteistolle-kulku",
                    :liike-tai-teollisuuskiinteistolle-kulkuun "liike-tai-teollisuuskiinteistolle-kulku",
                    :energiapuukuljetuksiin "energiapuukuljetukset",
                    :jalankulku-tai-pyoraliikenteeseen "jalankulku-tai-pyoraliikenne",
                    :moottorikelkkailuun "moottorikelkkailu",
                    :muu "muu"}]
    (get muunnokset sanoman-kt)))

(defn liittymalupa [{liittymaohje :liittymaohje :as liittymalupa}]
  {::tielupa/liittymalupa-myonnetty-kayttotarkoitus (:myonnetty-kauttotarkoitus liittymalupa)
   ::tielupa/liittymalupa-haettu-kayttotarkoitus (muunna-sanoman-kayttotarkoitus (:haettu-kayttotarkoitus liittymalupa))
   ::tielupa/liittymalupa-liittyman-siirto (:liittyman-siirto liittymalupa)
   ::tielupa/liittymalupa-tarkoituksen-kuvaus (:tarkoituksen-kuvaus liittymalupa)
   ::tielupa/liittymalupa-tilapainen (:tilapainen liittymalupa)
   ::tielupa/liittymalupa-sijainnin-kuvaus (:sijainnin-kuvaus liittymalupa)
   ::tielupa/liittymalupa-arvioitu-kokonaisliikenne (:arvioitu-kokonaisliikenne liittymalupa)
   ::tielupa/liittymalupa-arvioitu-kuorma-autoliikenne (:arvioitu-kuorma-autoliikenne liittymalupa)
   ::tielupa/liittymalupa-nykyisen-liittyman-numero (:nykyisen-liittyman-numero liittymalupa)
   ::tielupa/liittymalupa-nykyisen-liittyman-paivays (json-tyokalut/aika-string->java-sql-date (:nykyisen-liittyman-paivays liittymalupa))
   ::tielupa/liittymalupa-kiinteisto-rn (:kiinteisto-rn liittymalupa)
   ::tielupa/liittymalupa-muut-kulkuyhteydet (:muut-kulkuyhteydet liittymalupa)
   ::tielupa/liittymalupa-valmistumisen-takaraja (json-tyokalut/aika-string->java-sql-date (:valmistumisen-takaraja liittymalupa))
   ::tielupa/liittymalupa-kyla (:kyla liittymalupa)
   ::tielupa/liittymalupa-liittymaohje-liittymakaari (nil-turvallinen-bigdec (:liittymakaari liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-leveys-metreissa (nil-turvallinen-bigdec (:leveys-metreissa liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-rumpu (:rumpu liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa (nil-turvallinen-bigdec (:rummun-halkaisija-millimetreissa liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-rummun-etaisyys-metreissa (nil-turvallinen-bigdec (:rummun-etaisyys-metreissa liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-odotustila-metreissa (nil-turvallinen-bigdec (:odotustila-metreissa liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-nakemapisteen-etaisyys (nil-turvallinen-bigdec (:nakemapisteen-etaisyys liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-liittymisnakema (nil-turvallinen-bigdec (:liittymisnakema liittymaohje))
   ::tielupa/liittymalupa-liittymaohje-liikennemerkit (:liikennemerkit liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-lisaohjeet (:lisaohjeet liittymaohje)})

(defn mainokset [mainokset]
  (mapv (fn [{mainos :mainos}]
          (sijainti (:sijainti mainos)))
        mainokset))

(defn mainoslupa [mainoslupa]
  {::tielupa/mainoslupa-mainostettava-asia (:mainostettava-asia mainoslupa)
   ::tielupa/mainoslupa-sijainnin-kuvaus (:sijainnin-kuvaus mainoslupa)
   ::tielupa/mainoslupa-korvaava-paatos (:korvaava-paatos mainoslupa)
   ::tielupa/mainoslupa-tiedoksi-elykeskukselle (:tiedoksi-elykeskukselle mainoslupa)
   ::tielupa/mainoslupa-asemakaava-alueella (:asemakaava-alueella mainoslupa)
   ::tielupa/mainoslupa-suoja-alueen-leveys (nil-turvallinen-bigdec (:suoja-alueen-leveys mainoslupa))
   ::tielupa/mainokset (mainokset (:mainokset mainoslupa))})

(defn mainosilmoitus [mainosilmoitus]
  (assoc (mainoslupa mainosilmoitus) ::tielupa/mainoslupa-mainostettava-asia (:mainostettava-asia mainosilmoitus)))

(defn opasteet [opasteet]
  (mapv (fn [{opaste :opaste}]
          (merge {::tielupa/kuvaus (:kuvaus opaste)
                  ::tielupa/tulostenumero (:tulostenumero opaste)}
                 (sijainti (:sijainti opaste))))
        opasteet))

(defn opastelupa [opastelupa]
  {::tielupa/opastelupa-kohteen-nimi (:kohteen-nimi opastelupa)
   ::tielupa/opastelupa-palvelukohteen-opastaulu (:palvelukohteen-opastaulu opastelupa)
   ::tielupa/opastelupa-palvelukohteen-osoiteviitta (:palvelukohteen-osoiteviitta opastelupa)
   ::tielupa/opastelupa-osoiteviitta (:osoiteviitta opastelupa)
   ::tielupa/opastelupa-ennakkomerkki (:ennakkomerkki opastelupa)
   ::tielupa/opastelupa-opasteen-teksti (:opasteen-teksti opastelupa)
   ::tielupa/opastelupa-osoiteviitan-tunnus (:osoiteviitan-tunnus opastelupa)
   ::tielupa/opastelupa-lisatiedot (:lisatiedot opastelupa)
   ::tielupa/opastelupa-kohteen-url-osoite (:kohteen-url-osoite opastelupa)
   ::tielupa/opastelupa-jatkolupa (:jatkolupa opastelupa)
   ::tielupa/opastelupa-alkuperainen-lupanro (:alkuperainen-lupanro opastelupa)
   ::tielupa/opastelupa-alkuperaisen-luvan-alkupvm (json-tyokalut/aika-string->java-sql-date (:alkuperaisen-luvan-alkupvm opastelupa))
   ::tielupa/opastelupa-alkuperaisen-luvan-loppupvm (json-tyokalut/aika-string->java-sql-date (:alkuperaisen-luvan-loppupvm opastelupa))
   ::tielupa/opastelupa-nykyinen-opastus (:nykyinen-opastus opastelupa)
   ::tielupa/opasteet (opasteet (:opasteet opastelupa))})

(defn muunna-sanoman-sijoitus [m]
  (if (= true (:nakema-alue m))
    "nakemisalue"
    (when (= true (:suoja-alue m))
      "suoja-alue")))

(defn suoja-aluerakentamislupa [suoja-aluerakentamislupa]
  {::tielupa/suoja-aluerakentamislupa-rakennettava-asia (:rakennettava-asia suoja-aluerakentamislupa)
   ::tielupa/suoja-aluerakentamislupa-lisatiedot (:lisatiedot suoja-aluerakentamislupa)
   ::tielupa/suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan (nil-turvallinen-bigdec (:esitetty-etaisyys-tien-keskilinjaan suoja-aluerakentamislupa))
   ::tielupa/suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta (nil-turvallinen-bigdec (:vahimmaisetaisyys-tien-keskilinjasta suoja-aluerakentamislupa))
   ::tielupa/suoja-aluerakentamislupa-suoja-alueen-leveys (nil-turvallinen-bigdec (:suoja-alueen-leveys suoja-aluerakentamislupa))
   ::tielupa/suoja-aluerakentamislupa-sijoitus (-> suoja-aluerakentamislupa
                                                   :sijoitus
                                                   muunna-sanoman-sijoitus)
   ::tielupa/suoja-aluerakentamislupa-kiinteisto-rn (:kiinteisto-rn suoja-aluerakentamislupa)})

(defn tilapainen-myyntilupa [tilapainen-myyntilupa]
  {::tielupa/myyntilupa-aihe (:aihe tilapainen-myyntilupa)
   ::tielupa/myyntilupa-alueen-nimi (:alueen-nimi tilapainen-myyntilupa)
   ::tielupa/myyntilupa-aikaisempi-myyntilupa (:aikaisempi-myyntilupa tilapainen-myyntilupa)
   ::tielupa/myyntilupa-opastusmerkit (:opastusmerkit tilapainen-myyntilupa)})

(defn liikennemerkkijarjestelyt [jarjestelyt]
  (mapv (fn [{jarjestely :jarjestely}]
          (merge {::tielupa/liikennemerkki (:liikennemerkki jarjestely)
                  ::tielupa/alkuperainen-nopeusrajoitus (:alkuperainen-nopeusrajoitus jarjestely)
                  ::tielupa/alennettu-nopeusrajoitus (:alennettu-nopeusrajoitus jarjestely)
                  ::tielupa/nopeusrajoituksen-pituus (:nopeusrajoituksen-pituus jarjestely)}
                 (sijainti (:sijainti jarjestely))))
        jarjestelyt))

(defn tilapaiset-liikennemerkkijarjestelyt [tilapaiset-liikennemerkkijarjestelyt]
  {::tielupa/liikennemerkkijarjestely-aihe (:aihe tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestely-sijainnin-kuvaus (:sijainnin-kuvaus tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestely-tapahtuman-tiedot (:tapahtuman-tiedot tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestely-nopeusrajoituksen-syy (:nopeusrajoituksen-syy tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta (:lisatiedot-nopeusrajoituksesta tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestely-muut-liikennemerkit (:muut-liikennemerkit tilapaiset-liikennemerkkijarjestelyt)
   ::tielupa/liikennemerkkijarjestelyt (liikennemerkkijarjestelyt (:jarjestelyt tilapaiset-liikennemerkkijarjestelyt))})

(defn tyolupa [tyolupa]
  {::tielupa/tyolupa-tyon-sisalto (:tyon-sisalto tyolupa)
   ::tielupa/tyolupa-tyon-saa-aloittaa (json-tyokalut/aika-string->java-sql-date (:tyon-saa-aloittaa tyolupa))
   ::tielupa/tyolupa-viimeistely-oltava (json-tyokalut/aika-string->java-sql-date (:viimeistely-oltava tyolupa))
   ::tielupa/tyolupa-ohjeet-tyon-suorittamiseen (:ohjeet-tyon-suorittamiseen tyolupa)
   ::tielupa/tyolupa-los-puuttuu (:los-puuttuu tyolupa)
   ::tielupa/tyolupa-ilmoitus-tieliikennekeskukseen (:ilmoitus-tieliikennekeskukseen tyolupa)
   ::tielupa/tyolupa-tilapainen-nopeusrajoitus (:tilapainen-nopeusrajoitus tyolupa)
   ::tielupa/tyolupa-los-lisatiedot (:los-lisatiedot tyolupa)
   ::tielupa/tyolupa-tieliikennekusksen-sahkopostiosoite (:tieliikennekusksen-sahkopostiosoite tyolupa)})

(defn johtoasennukset [johtoasennukset]
  (mapv (fn [{johtoasennus :johtoasennus}]
          (merge {::tielupa/laite (:laite johtoasennus)
                  ::tielupa/asennustyyppi (:asennustyyppi johtoasennus)
                  ::tielupa/kommentit (:kommentit johtoasennus)}
                 (sijainti (:sijainti johtoasennus))))
        johtoasennukset))

(defn vesihuoltolupa [vesihuoltolupa]
  {::tielupa/vesihuoltolupa-tienylityksia (:tienylityksia vesihuoltolupa)
   ::tielupa/vesihuoltolupa-tienalituksia (:tienalituksia vesihuoltolupa)
   ::tielupa/vesihuoltolupa-silta-asennuksia (:silta-asennuksia vesihuoltolupa)
   ::tielupa/johtoasennukset (johtoasennukset (:johtoasennukset vesihuoltolupa))})

(defn api->domain [tielupa]
  (let [domain (-> (perustiedot tielupa)
                   (merge (sijainnit (:sijainnit tielupa)))
                   (merge (hakijan-tiedot (:hakija tielupa)))
                   (merge (urakoitsijan-tiedot (:urakoitsija tielupa)))
                   (merge (liikenneohjaajan-tiedot (:liikenteenohjauksesta-vastaava tielupa)))
                   (merge (tienpitoviranomaisen-tiedot (:tienpitoviranomainen tielupa)))
                   (merge (valmistumisilmoitus (:valmistumisilmoitus tielupa)))
                   (merge (johto-ja-kaapelilupa (:johto-ja-kaapelilupa tielupa)))
                   (merge (liittymalupa (:liittymalupa tielupa)))
                   (merge (mainoslupa (:mainoslupa tielupa)))
                   (merge (mainosilmoitus (:mainosilmoitus tielupa)))
                   (merge (opastelupa (:opastelupa tielupa)))
                   (merge (suoja-aluerakentamislupa (:suoja-aluerakentamislupa tielupa)))
                   (merge (tilapainen-myyntilupa (:tilapainen-myyntilupa tielupa)))
                   (merge (tilapaiset-liikennemerkkijarjestelyt (:tilapaiset-liikennemerkkijarjestelyt tielupa)))
                   (merge (tyolupa (:tyolupa tielupa)))
                   (merge (vesihuoltolupa (:vesihuoltolupa tielupa))))]
    domain))
