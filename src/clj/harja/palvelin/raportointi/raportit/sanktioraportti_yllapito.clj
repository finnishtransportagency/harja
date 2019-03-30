(ns harja.palvelin.raportointi.raportit.sanktioraportti-yllapito
  "Ylläpidon Sakko- ja bonusraportti.

  Sisältää ylläpitourakan sakko- ja bonustiedot.

  Kontekstikohtaiset sarakkeet ovat:
    -urakka: urakan nimi
    -ELY: urakoiden nimet ja yhteensä
    -Koko maa: ELYjen nimet ja yhteensä

  Riveinä muistutusten ja sakkojen määrä ylläpitoluokittain ja kaikki luokat yhteensä."
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- yllapitoluokan-raporttirivit
  [luokka luokan-rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko (if luokka
               (str "Ylläpitoluokka " (yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi luokka))
               "Ei ylläpitoluokkaa")}
   (yhteiset/luo-rivi-muistutusten-maara yhteiset/+muistutusrivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa yhteiset/+sakkorivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])

(defn suorita [db user parametrit]
  (let [raportin-rivit-fn (fn [{:keys [naytettavat-alueet sanktiot-kannassa yhteensa-sarake?]}]
                            (let [optiot {:yhteensa-sarake? yhteensa-sarake? :urakkatyyppi (:urakkatyyppi parametrit)}
                                  sanktiot-yllapitoluokittain (group-by :yllapitoluokka sanktiot-kannassa)
                                  yllapitoluokittaiset-rivit (mapcat (fn [[luokka luokan-rivit]]
                                                                       (yllapitoluokan-raporttirivit luokka luokan-rivit naytettavat-alueet optiot))
                                                                     sanktiot-yllapitoluokittain)
                                  yhteensa-rivit (yhteiset/raporttirivit-yhteensa sanktiot-kannassa naytettavat-alueet optiot)]
                              (into []
                                    (when (> (count naytettavat-alueet) 0)
                                      (concat
                                        yllapitoluokittaiset-rivit
                                        yhteensa-rivit)))))
        db-haku-fn hae-sanktiot-yllapidon-raportille
        raportin-nimi "Sakko- ja bonusraportti"
        info-teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."]

    (yhteiset/suorita-runko db user (merge parametrit {:raportin-rivit-fn raportin-rivit-fn
                                                       :db-haku-fn db-haku-fn
                                                       :raportin-nimi raportin-nimi
                                                       :info-teksti info-teksti}))))
