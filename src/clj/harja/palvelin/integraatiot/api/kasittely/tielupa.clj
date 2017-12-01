(ns harja.palvelin.integraatiot.api.kasittely.tielupa
  (:require [harja.domain.tielupa :as tielupa]
            [harja.tyokalut.xml :as tyokalut]))

(def testidata
  {:otsikko
   {:lahettaja
    {:jarjestelma "Urakoitsijan järjestelmä",
     :organisaatio {:nimi "Urakoitsija", :ytunnus "1234567-8"}},
    :viestintunniste {:id 123},
    :lahetysaika "2016-01-30T12:00:00+02:00"},
   :tielupa
   {:perustiedot
    {:kohteen-postinumero "90900",
     :kohteen-postitoimipaikka "Kiiminki",
     :voimassaolon-alkupvm "2020-09-22T12:00:00+02:00",
     :kohteen-lahiosoite "Tie 123",
     :kunta "Kiiminki",
     :paatoksen-diaarinumero "123456789",
     :saapumispvm "2017-09-22T12:00:00+02:00",
     :otsikko "Lupa tehdä töitä",
     :ely "Pohjois-Pohjanmaa",
     :katselmus-url "https://tilu.fi/1234",
     :tunniste {:id 1234},
     :tien-nimi "Kuusamontie",
     :myontamispvm "2018-09-22T12:00:00+02:00",
     :alueurakka "Oulun alueurakka",
     :tyyppi "työlupa",
     :voimassaolon-loppupvm "2020-09-22T12:00:00+02:00"},
    :sijainnit
    [{:sijainti
      {:numero 20,
       :aet 2631,
       :aosa 6,
       :ajorata 0,
       :kaista 1,
       :puoli 0,
       :sijoitus "oikea"}}],
    :hakija
    {:nimi "Henna Hakija",
     :postiosoite "Liitintie 1",
     :postinumero "90900",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "henna.hakija@example.com",
     :tyyppi "kotitalous"},
    :urakoitsija
    {:nimi "Puulaaki Oy",
     :yhteyshenkilo "Yrjänä Yhteyshenkilo",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "yrjana.yhteyshenkilo@example.com"},
    :liikenteenohjauksesta-vastaava
    {:nimi "Liikenneohjaus Oy",
     :yhteyshenkilo "Lilli Liikenteenohjaaja",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "lilli.liikenteenohjaaja@example.com"},
    :tienpitoviranomainen
    {:yhteyshenkilo "Teijo Tienpitäjä",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "teijo.tienpitaja@example.com"},
    :valmistumisilmoitus
    {:vaaditaan true,
     :palautettu true,
     :valmistumisilmoitus "Työt valmistuneet 22.9.2017"},
    :tyolupa
    {:ilmoitus-tieliikennekeskukseen true,
     :los-puuttuu false,
     :tyon-sisalto "Töitä",
     :ohjeet-tyon-suorittamiseen "",
     :viimeistely-oltava "2017-10-22T12:00:00+02:00",
     :los-lisatiedot "",
     :tieliikennekusksen-sahkopostiosoite "",
     :tilapainen-nopeusrajoitus true,
     :tyon-saa-aloittaa "2017-09-22T12:00:00+02:00"}}})

(defn hae-ely [ely]
  ;; todo: hae kannasta vastaava
  )

(defn hae-tieluvan-urakka [tielupa]
  ;; todo: hae kannasta
  )

(defn perustiedot [{:keys [perustiedot tunniste] :as tielupa}]
  (println "--->>> TYYPPI" (type (:myontamispvm perustiedot)))
  {::tielupa/ely (hae-ely (:ely perustiedot))
   ::tielupa/urakka (hae-tieluvan-urakka tielupa)
   ::tielupa/hakija-postinumero (:kohteen-postinumero perustiedot)
   ::tielupa/kohde-postitoimipaikka (:kohteen-postitoimipaikka perustiedot)
   ::tielupa/kohde-postinumero (:kohteen-postinumero perustiedot)
   ::tielupa/kunta (:kunta perustiedot)
   ::tielupa/voimassaolon-alkupvm (tyokalut/json-date-time->xml-xs-date (:voimassaolon-alkupvm perustiedot))
   ::tielupa/voimassaolon-loppupvm (tyokalut/json-date-time->xml-xs-date (:voimassaolon-loppupvm perustiedot))
   ::tielupa/kohde-lahiosoite (:kohteen-lahiosoite perustiedot)
   ::tielupa/paatoksen-diaarinumero (:paatoksen-diaarinumero perustiedot)
   ::tielupa/saapumispvm (tyokalut/json-date-time->xml-xs-date (:saapumispvm perustiedot))
   ::tielupa/otsikko (:otsikko perustiedot)
   ::tielupa/katselmus-url (:katselmus-url perustiedot)
   ::tielupa/ulkoinen-tunniste (:id tunniste)
   ::tielupa/tien-nimi (:tien-nimi perustiedot)
   ::tielupa/myontamispvm (tyokalut/json-date-time->xml-xs-date (:myontamispvm perustiedot))
   ::tielupa/urakan-nimi (:alueurakka perustiedot)
   ::tielupa/tyyppi (keyword (:tyyppi perustiedot))})

(defn hae-geometria-tieosoitteelle [{:keys [numero
                                            aosa
                                            aet
                                            losa
                                            let
                                            ajorata]}]
  ;; todo: hae kannasta
  )

(defn sijainnit [sijainnit]
  {::tielupa/sijainnit (mapv (fn [sijainti]
                               {::tielupa/tie (:numero sijainti)
                                ::tielupa/aosa (:aosa sijainti)
                                ::tielupa/aet (:aet sijainti)
                                ::tielupa/losa (:losa sijainti)
                                ::tielupa/let (:let sijainti)
                                ::tielupa/ajorata (:ajorata sijainti)
                                ::tielupa/kaista (:kaista sijainti)
                                ::tielupa/puoli (:puoli sijainti)
                                ::tielupa/geometria (hae-geometria-tieosoitteelle sijainti)})
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

(defn tallennettava-tielupa [tielupa]
  (let [tallennettava (-> (perustiedot tielupa)
                          (merge (sijainnit (:sijainnit tielupa)))
                          (merge (hakijan-tiedot (:hakija tielupa)))
                          (merge (urakoitsijan-tiedot (:urakoitsija tielupa)))
                          (merge (liikenneohjaajan-tiedot (:liikenteenohjauksesta-vastaava tielupa)))
                          (merge (tienpitoviranomaisen-tiedot (:tienpitoviranomainen tielupa))))]
    (println "--->>> tielupa" tielupa)
    (println "--->>> tallennettava" tallennettava)
    tallennettava))