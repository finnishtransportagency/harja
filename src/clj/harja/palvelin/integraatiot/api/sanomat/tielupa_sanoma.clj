(ns harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma
  (:require [harja.domain.tielupa :as tielupa]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]))

(defn perustiedot [{perustiedot :perustiedot}]
  {::tielupa/hakija-postinumero (:kohteen-postinumero perustiedot)
   ::tielupa/kohde-postitoimipaikka (:kohteen-postitoimipaikka perustiedot)
   ::tielupa/kohde-postinumero (:kohteen-postinumero perustiedot)
   ::tielupa/kunta (:kunta perustiedot)
   ::tielupa/voimassaolon-alkupvm (json-tyokalut/aika-string->java-sql-date (:voimassaolon-alkupvm perustiedot))
   ::tielupa/voimassaolon-loppupvm (json-tyokalut/aika-string->java-sql-date (:voimassaolon-loppupvm perustiedot))
   ::tielupa/kohde-lahiosoite (:kohteen-lahiosoite perustiedot)
   ::tielupa/paatoksen-diaarinumero (:paatoksen-diaarinumero perustiedot)
   ::tielupa/saapumispvm (json-tyokalut/aika-string->java-sql-date (:saapumispvm perustiedot))
   ::tielupa/otsikko (:otsikko perustiedot)
   ::tielupa/katselmus-url (:katselmus-url perustiedot)
   ::tielupa/ulkoinen-tunniste (get-in perustiedot [:tunniste :id])
   ::tielupa/tien-nimi (:tien-nimi perustiedot)
   ::tielupa/myontamispvm (json-tyokalut/aika-string->java-sql-date (:myontamispvm perustiedot))
   ::tielupa/urakan-nimi (:alueurakka perustiedot)
   ::tielupa/tyyppi (keyword (:tyyppi perustiedot))})

(defn sijainnit [sijainnit]
  {::tielupa/sijainnit (mapv (fn [{sijainti :sijainti}]
                               {::tielupa/tie (:numero sijainti)
                                ::tielupa/aosa (:aosa sijainti)
                                ::tielupa/aet (:aet sijainti)
                                ::tielupa/losa (:losa sijainti)
                                ::tielupa/let (:let sijainti)
                                ::tielupa/ajorata (:ajorata sijainti)
                                ::tielupa/kaista (:kaista sijainti)
                                ::tielupa/puoli (:puoli sijainti)})
                             sijainnit)})

(defn hakijan-tiedot [hakija]
  {::tielupa/hakija-nimi (:nimi hakija)
   ::tielupa/hakija-postinosoite (:postiosoite hakija)
   ::tielupa/hakija-postinumero (:postinumero hakija)
   ::tielupa/hakija-puhelinnumero (:puhelinnumero hakija)
   ::tielupa/hakija-sahkopostiosoite (:sahkopostiosoite hakija)
   ::tielupa/hakija-tyyppi (:tyyppi hakija)})

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
   ::tielupa/tienpitoviranomainen-sahkopostiosoite (:sahkopostiosoite tienpitoviraonomainen)})

(defn api->domain [tielupa]
  (let [tallennettava (-> (perustiedot tielupa)
                          (merge (sijainnit (:sijainnit tielupa)))
                          (merge (hakijan-tiedot (:hakija tielupa)))
                          (merge (urakoitsijan-tiedot (:urakoitsija tielupa)))
                          (merge (liikenneohjaajan-tiedot (:liikenteenohjauksesta-vastaava tielupa)))
                          (merge (tienpitoviranomaisen-tiedot (:tienpitoviranomainen tielupa))))]
    tallennettava))