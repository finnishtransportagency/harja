(ns harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma
  (:require [harja.domain.tielupa :as tielupa]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]))

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
                  ::tielupa/maakaapelia-metreissa (bigdec (:maakaapelia-metreissa kaapeliasennus))
                  ::tielupa/ilmakaapelia-metreissa (bigdec (:ilmakaapelia-metreissa kaapeliasennus))
                  ::tielupa/nopeusrajoitus (:nopeusrajoitus kaapeliasennus)
                  ::tielupa/liikennemaara (bigdec (:liikennemaara kaapeliasennus))}
                 (sijainti (:sijainti kaapeliasennus))))
        kaapeliasennukset))

(defn johto-ja-kaapelilupa [johto-ja-kaapelilupa]
  {::tielupa/johtolupa-maakaapelia-yhteensa (bigdec (:maakaapelia-yhteensa johto-ja-kaapelilupa))
   ::tielupa/johtolupa-ilmakaapelia-yhteensa (bigdec (:ilmakaapelia-yhteensa johto-ja-kaapelilupa))
   ::tielupa/johtolupa-tienalituksia (:tienalituksia johto-ja-kaapelilupa)
   ::tielupa/johtolupa-tienylityksia (:tienylityksia johto-ja-kaapelilupa)
   ::tielupa/johtolupa-silta-asennuksia (:silta-asennuksia johto-ja-kaapelilupa)
   ::tielupa/kaapeliasennukset (kaapeliasennukset (:kaapeliasennukset johto-ja-kaapelilupa))})

(defn api->domain [tielupa]
  (let [domain (-> (perustiedot tielupa)
                   (merge (sijainnit (:sijainnit tielupa)))
                   (merge (hakijan-tiedot (:hakija tielupa)))
                   (merge (urakoitsijan-tiedot (:urakoitsija tielupa)))
                   (merge (liikenneohjaajan-tiedot (:liikenteenohjauksesta-vastaava tielupa)))
                   (merge (tienpitoviranomaisen-tiedot (:tienpitoviranomainen tielupa)))
                   (merge (valmistumisilmoitus (:valmistumisilmoitus tielupa)))
                   (merge (johto-ja-kaapelilupa (:johto-ja-kaapelilupa tielupa))))]
    domain))