(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- raporttirivit-talvihoito [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Talvihoito"}
   (yhteiset/luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
                                         {:talvihoito? true
                                          :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko A" rivit alueet
                                     {:sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Päätiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, päätiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Muut tiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, muut tiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                                     {:sakkoryhma :B
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Päätiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, päätiet"
                                      :sakkoryhma :B
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Muut tiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, muut tiet"
                                      :sakkoryhma :B
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Talvihoito, sakot yht." rivit alueet
                                     {:talvihoito? true
                                      :sakkoryhma #{:A :B}
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-indeksien-summa "Talvihoito, indeksit yht." rivit alueet
                                      {:talvihoito? true
                                       :sakkoryhma #{:A :B}
                                       :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-muut-tuotteet [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Muut tuotteet"}
   (yhteiset/luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
                                         {:talvihoito? false
                                          :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko A" rivit alueet
                                     {:sakkoryhma :A
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Liikenneymp. hoito" rivit alueet
                                     {:sanktiotyyppi "Liikenneympäristön hoito"
                                      :sakkoryhma :A
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Sorateiden hoito" rivit alueet
                                     {:sanktiotyyppi "Sorateiden hoito ja ylläpito"
                                      :sakkoryhma :A
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                                     {:sakkoryhma :B
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Liikenneymp. hoito" rivit alueet
                                     {:sanktiotyyppi "Liikenneympäristön hoito"
                                      :sakkoryhma :B
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "- Sorateiden hoito" rivit alueet
                                     {:sanktiotyyppi "Sorateiden hoito ja ylläpito"
                                      :sakkoryhma :B
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Muut tuotteet, sakot yht." rivit alueet
                                     {:talvihoito? false
                                      :sakkoryhma #{:A :B}
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-indeksien-summa "Muut tuotteet, indeksit yht." rivit alueet
                                      {:talvihoito? false
                                       :sakkoryhma #{:A :B}
                                       :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-ryhma-c [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Ryhmä C"}
   (yhteiset/luo-rivi-sakkojen-summa "Ryhmä C, sakot yht." rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-indeksien-summa "Ryhmä C, indeksit yht." rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})])


(defn- raporttirivit [rivit alueet optiot]
  (into [] (concat
             (raporttirivit-talvihoito rivit alueet optiot)
             (raporttirivit-muut-tuotteet rivit alueet optiot)
             (raporttirivit-ryhma-c rivit alueet optiot)
             (yhteiset/raporttirivit-yhteensa rivit alueet optiot))))

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
                                (hae-sanktiot db
                                              {:urakka urakka-id
                                               :hallintayksikko hallintayksikko-id
                                               :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                               :alku alkupvm
                                               :loppu loppupvm}))

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
        raportin-rivit (when (> (count naytettavat-alueet) 0)
                         (raporttirivit sanktiot-kannassa naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?}))
        raportin-nimi "Sanktioiden yhteenveto"
        otsikko (yleinen/raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko
                 :oikealle-tasattavat-kentat (into #{} (range 1 (yleinen/sarakkeiden-maara raportin-otsikot)))
                 :sheet-nimi raportin-nimi}
      raportin-otsikot
      raportin-rivit]]))
