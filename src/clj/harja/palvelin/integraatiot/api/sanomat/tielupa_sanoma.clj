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
   ::tielupa/katselmus-url (:katselmus-url perustiedot)
   ::tielupa/urakan-nimi (:alueurakka perustiedot)
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
   ::tielupa/tienpitoviranomainen-kasittelija (::kasittelija tienpitoviraonomainen)})

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

(defn liittymalupa [{liittymaohje :liittymaohje :as liittymalupa}]
  {::tielupa/liittymalupa-myonnetty-kayttotarkoitus (:myonnetty-kauttotarkoitus liittymalupa)
   ::tielupa/liittymalupa-haettu-kayttotarkoitus (:haettu-kayttotarkoitus liittymalupa)
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
   ::tielupa/liittymalupa-liittymaohje-liittymakaari (:liittymakaari liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-leveys-metreissa (:leveys-metreissa liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-rumpu (:rumpu liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa (:rummun-halkaisija-millimetreissa liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-rummun-etaisyys-metreissa (:rummun-etaisyys-metreissa liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-odotustila-metreissa (:odotustila-metreissa liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-nakemapisteen-etaisyys (:nakemapisteen-etaisyys liittymaohje)
   ::tielupa/liittymalupa-liittymaohje-liittymisnakema (:liittymisnakema liittymaohje)
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
                   (merge (mainoslupa (:mainoslupa tielupa))))]
    domain))