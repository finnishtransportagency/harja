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
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- yllapitoluokan-raporttirivit
  [luokka luokan-rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko (if luokka
               (str "Ylläpitoluokka " (yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi luokka))
               "Ei ylläpitoluokkaa")}
   (yhteiset/luo-rivi-muistutusten-maara yhteiset/+muistutusrivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa yhteiset/+sakkorivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])


(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-alueet (yleinen/naytettavat-alueet db konteksti {:urakka urakka-id
                                                                     :hallintayksikko hallintayksikko-id
                                                                     :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                                                     :alku alkupvm
                                                                     :loppu loppupvm})
        sanktiot-kannassa (into []
                                (comp
                                  (map #(konv/string->keyword % :sakkoryhma))
                                  (map #(konv/array->set % :sanktiotyyppi_laji keyword)))
                                (hae-sanktiot-yllapidon-raportille db
                                                                   {:urakka urakka-id
                                                                    :hallintayksikko hallintayksikko-id
                                                                    :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                                                    :alku alkupvm
                                                                    :loppu loppupvm}))
        sanktiot-yllapitoluokittain (group-by :yllapitoluokka sanktiot-kannassa)
        urakat-joista-loytyi-sanktioita (into #{} (map #(select-keys % [:urakka-id :nimi]) sanktiot-kannassa))
        ;; jos on jostain syystä sanktioita urakassa joka ei käynnissä, spesiaalikäsittely, I'm sorry
        naytettavat-alueet (if (= konteksti :hallintayksikko)
                             (vec (sort-by :nimi (set/union (into #{} naytettavat-alueet)
                                                            urakat-joista-loytyi-sanktioita)))
                             naytettavat-alueet)
        yhteensa-sarake? (> (count naytettavat-alueet) 1)
        raportin-otsikot (into [] (concat
                                    [{:otsikko "" :leveys 12}]
                                    (mapv
                                      (fn [alue]
                                        {:otsikko (if (= konteksti :koko-maa)
                                                    (str (:elynumero alue) " " (:nimi alue))
                                                    (:nimi alue))
                                         :leveys 15
                                         :fmt :raha})
                                      naytettavat-alueet)
                                    (when yhteensa-sarake?
                                      [{:otsikko "Yh\u00ADteen\u00ADsä" :leveys 15 :fmt :raha}])))
        raportin-nimi "Sakko- ja bonusraportti"
        optiot {:yhteensa-sarake? yhteensa-sarake? :urakkatyyppi urakkatyyppi}
        otsikko (yleinen/raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        yllapitoluokittaiset-rivit (mapcat (fn [[luokka luokan-rivit]]
                                             (yllapitoluokan-raporttirivit luokka luokan-rivit naytettavat-alueet optiot))
                                           sanktiot-yllapitoluokittain)
        yhteensa-rivit (yhteiset/raporttirivit-yhteensa sanktiot-kannassa naytettavat-alueet optiot)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko
                 :oikealle-tasattavat-kentat (into #{} (range 1 (yleinen/sarakkeiden-maara raportin-otsikot)))
                 :sheet-nimi raportin-nimi}
      raportin-otsikot
      (into []
            (when (> (count naytettavat-alueet) 0)
              (concat
                yllapitoluokittaiset-rivit
                yhteensa-rivit)))]
     [:teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."]]))
