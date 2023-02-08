(ns harja.palvelin.raportointi.raportit.sanktioraportti-yllapito
  "Ylläpidon Sakko- ja bonusraportti.

  Sisältää ylläpitourakan sakko- ja bonustiedot.

  Kontekstikohtaiset sarakkeet ovat:
    -urakka: urakan nimi
    -ELY: urakoiden nimet ja yhteensä
    -Koko maa: ELYjen nimet ja yhteensä

  Riveinä muistutusten ja sakkojen määrä ylläpitoluokittain ja kaikki luokat yhteensä."
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.sanktiot :as sanktiot-kyselyt]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- jasenna-raportin-nimi [db parametrit]
  (let [urakan-tiedot (if (not (nil? (:urakka-id parametrit)))
                        (first (urakat-kyselyt/hae-urakka db (:urakka-id parametrit)))
                        nil)
        hallintayksikon-tiedot (if (not (nil? (:hallintayksikko-id parametrit)))
                                 (first (organisaatiot-kyselyt/hae-organisaatio db (:hallintayksikko-id parametrit)))
                                 nil)
        raportin-tyyppi (if (nil? (:kasittelija parametrit))
                          :html
                          (:kasittelija parametrit))
        raportin-nimi (cond
                        (and (= :html raportin-tyyppi) urakan-tiedot) (:nimi urakan-tiedot)
                        (and (= :html raportin-tyyppi) hallintayksikon-tiedot) (:nimi hallintayksikon-tiedot)
                        (and (= :html raportin-tyyppi) (nil? hallintayksikon-tiedot) (nil? urakan-tiedot)) "Koko maa"
                        :else "Sanktiot, bonukset ja arvonvähennykset")]
    raportin-nimi))
(defn suorita [db user {:keys [urakka-id hallintayksikko-id urakkatyyppi alkupvm loppupvm] :as parametrit}]
  (let [sanktiot (hae-sanktiot-yllapidon-raportille db
                   {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                    :alku alkupvm
                    :loppu loppupvm})
        raportin-nimi (jasenna-raportin-nimi db parametrit)
        info-teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."]

    (yhteiset/suorita-runko db user (merge parametrit {:sanktiot sanktiot
                                                       :raportin-nimi raportin-nimi
                                                       :info-teksti info-teksti}))))
