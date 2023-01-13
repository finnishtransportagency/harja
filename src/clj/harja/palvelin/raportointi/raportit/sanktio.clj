(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.konversio :as konv]))

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
   ; Content kopioitu figmasta, tyhjän välin voisi formatoida html mutta näin ehkä helpompi?
   (yhteiset/luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, päätiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, muut tiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                                     {:sakkoryhma :B
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, päätiet"
                                      :sakkoryhma :B
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
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
   (yhteiset/luo-rivi-sakkojen-summa "        • Liikenneymp. hoito" rivit alueet
                                     {:sanktiotyyppi "Liikenneympäristön hoito"
                                      :sakkoryhma :A
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Sorateiden hoito" rivit alueet
                                     {:sanktiotyyppi "Sorateiden hoito ja ylläpito"
                                      :sakkoryhma :A
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                                     {:sakkoryhma :B
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Liikenneymp. hoito" rivit alueet
                                     {:sanktiotyyppi "Liikenneympäristön hoito"
                                      :sakkoryhma :B
                                      :talvihoito? false
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Sorateiden hoito" rivit alueet
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


(defn- jasenna-raportin-nimi [db parametrit]
  (let [urakan-tiedot (if (not (nil? (:urakka-id parametrit)))
                        (first (urakat-kyselyt/hae-urakka db (:urakka-id parametrit)))
                        nil)
        hallintayksikon-tiedot (if (not (nil? (:hallintayksikko-id parametrit)))
                                 (first (organisaatiot-kyselyt/hae-organisaatio db (:hallintayksikko-id parametrit)))
                                 nil)
        raportin-nimi (cond
                        urakan-tiedot (:nimi urakan-tiedot)
                        hallintayksikon-tiedot (:nimi hallintayksikon-tiedot)
                        :else "Koko maa")]
    raportin-nimi))
(defn suorita [db user parametrit]
  (let [raportin-rivit-fn (fn [{:keys [naytettavat-alueet sanktiot-kannassa yhteensa-sarake?]}]
                           (when (> (count naytettavat-alueet) 0)
                             (raporttirivit sanktiot-kannassa naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?})))
        db-haku-fn hae-sanktiot
        raportin-nimi (jasenna-raportin-nimi db parametrit)]

    (yhteiset/suorita-runko db user (merge parametrit {:raportin-rivit-fn raportin-rivit-fn
                                                       :db-haku-fn db-haku-fn
                                                       :raportin-nimi raportin-nimi}))))
