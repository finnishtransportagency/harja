(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.set :as set]
            [clojure.string :as str]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito"))

(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id sakkoryhma talvihoito? sanktiotyyppi] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakko? rivi)
        (or (nil? sakkoryhma) (if (set? sakkoryhma)
                                (sakkoryhma (:sakkoryhma rivi))
                                (= sakkoryhma (:sakkoryhma rivi))))
        (or (nil? urakka-id) (= urakka-id (:urakka_id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? sanktiotyyppi) (str/includes? (str/lower-case (:sanktiotyyppi_nimi rivi)) (str/lower-case sanktiotyyppi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- suodata-muistutukset [rivit {:keys [urakka-id hallintayksikko-id talvihoito?] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (not (sanktiot-domain/sakko? rivi))
        (or (nil? urakka-id) (= urakka-id (:urakka_id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- sakkojen-summa
  ([rivit] (sakkojen-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:summa %) 0)
                 laskettavat)))))

(defn- indeksien-summa
  ([rivit] (indeksien-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:indeksikorotus %) 0)
                 laskettavat)))))

(defn muistutusten-maara
  ([rivit] (muistutusten-maara rivit {}))
  ([rivit suodattimet]
   (count (suodata-muistutukset rivit suodattimet))))

(defn- luo-rivi-sakkojen-summa
  ([otsikko rivit alueet]
   (luo-rivi-sakkojen-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (€)")] (mapv (fn [alue]
                                                         (fmt/desimaaliluku-opt (sakkojen-summa rivit (merge optiot alue))
                                                                                2))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (fmt/desimaaliluku-opt (sakkojen-summa rivit optiot) 2))
       rivi))))

(defn- luo-rivi-muistutusten-maara
  ([otsikko rivit alueet]
   (luo-rivi-muistutusten-maara otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (kpl)")] (mapv (fn [alue]
                                                           (muistutusten-maara rivit (merge optiot alue)))
                                                         alueet))]
     (if yhteensa-sarake?
       (conj rivi (muistutusten-maara rivit optiot))
       rivi))))

(defn- luo-rivi-indeksien-summa
  ([otsikko rivit alueet]
   (luo-rivi-indeksien-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (€)")] (mapv (fn [alue]
                                                         (fmt/desimaaliluku-opt (indeksien-summa rivit (merge optiot alue))
                                                                                2))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (fmt/desimaaliluku-opt (indeksien-summa rivit optiot)
                                         2))
       rivi))))

(defn- luo-rivi-kaikki-yht
  ([otsikko rivit alueet] (luo-rivi-kaikki-yht otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (€)")] (mapv (fn [alue]
                                                         (fmt/desimaaliluku-opt (+ (sakkojen-summa rivit alue)
                                                                                   (indeksien-summa rivit alue))
                                                                                2))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (fmt/desimaaliluku-opt (+ (sakkojen-summa rivit)
                                            (indeksien-summa rivit))
                                         2))
       rivi))))

(defn- raporttirivit-talvihoito [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Talvihoito"}
   (luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
                                {:talvihoito? true
                                 :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Sakko A" rivit alueet
                            {:sakkoryhma :A
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Päätiet" rivit alueet
                            {:sanktiotyyppi "Talvihoito, päätiet"
                             :sakkoryhma :A
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Muut tiet" rivit alueet
                            {:sanktiotyyppi "Talvihoito, muut tiet"
                             :sakkoryhma :A
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                            {:sakkoryhma :B
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Päätiet" rivit alueet
                            {:sanktiotyyppi "Talvihoito, päätiet"
                             :sakkoryhma :B
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Muut tiet" rivit alueet
                            {:sanktiotyyppi "Talvihoito, muut tiet"
                             :sakkoryhma :B
                             :talvihoito? true
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Talvihoito, sakot yht." rivit alueet
                            {:talvihoito? true
                             :sakkoryhma #{:A :B}
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Talvihoito, indeksit yht." rivit alueet
                             {:talvihoito? true
                              :sakkoryhma #{:A :B}
                              :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-muut-tuotteet [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Muut tuotteet"}
   (luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
                                {:talvihoito? false
                                 :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Sakko A" rivit alueet
                            {:sakkoryhma :A
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Liikenneymp. hoito" rivit alueet
                            {:sanktiotyyppi "Liikenneympäristön hoito"
                             :sakkoryhma :A
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Sorateiden hoito" rivit alueet
                            {:sanktiotyyppi "Sorateiden hoito ja ylläpito"
                             :sakkoryhma :A
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Sakko B" rivit alueet
                            {:sakkoryhma :B
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Liikenneymp. hoito" rivit alueet
                            {:sanktiotyyppi "Liikenneympäristön hoito"
                             :sakkoryhma :B
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "- Sorateiden hoito" rivit alueet
                            {:sanktiotyyppi "Sorateiden hoito ja ylläpito"
                             :sakkoryhma :B
                             :talvihoito? false
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Muut tuotteet, sakot yht." rivit alueet
                            {:talvihoito? false
                             :sakkoryhma #{:A :B}
                             :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Muut tuotteet, indeksit yht." rivit alueet
                             {:talvihoito? false
                              :sakkoryhma #{:A :B}
                              :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-ryhma-c [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Ryhmä C"}
   (luo-rivi-sakkojen-summa "Ryhmä C, sakot yht." rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Ryhmä C, indeksit yht." rivit alueet  {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-yhteensa [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Yhteensä"}
   (luo-rivi-muistutusten-maara "Muistutukset yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Indeksit yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Kaikki sakot yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-kaikki-yht "Kaikki yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit [rivit alueet optiot]
  (into [] (concat
             (raporttirivit-talvihoito rivit alueet optiot)
             (raporttirivit-muut-tuotteet rivit alueet optiot)
             (raporttirivit-ryhma-c rivit alueet optiot)
             (raporttirivit-yhteensa rivit alueet optiot))))

(defn- muodosta-listattavat-alueet
  "Palauttaa listan joka sisältää pelkästään urakat tai hallintayksiköt, riippuen kontekstista"
  [urakat konteksti]
  (if (= konteksti :koko-maa)
    (distinct
      (map
        #(-> %
             (dissoc :urakka_id :urakka_nimi)
             (set/rename-keys {:hallintayksikko_id :hallintayksikko-id
                               :hallintayksikko_nimi :nimi
                               :hallintayksikko_elynumero :hallintayksikko-elynumero}))

        urakat))
    (map
      #(-> %
           (dissoc :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_elynumero)
           (set/rename-keys {:urakka_id :urakka-id
                             :urakka_nimi :nimi}))
      urakat)))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        urakat (hae-kontekstin-urakat db
                                      {:urakka urakka-id
                                       :hallintayksikko hallintayksikko-id
                                       :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                       :alku alkupvm
                                       :loppu loppupvm})
        naytettavat-alueet (muodosta-listattavat-alueet urakat konteksti)
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
        yhteensa-sarake? (> (count naytettavat-alueet) 1)
        raportin-otsikot (into [] (concat
                                    [{:otsikko "" :leveys 8}]
                                    (mapv
                                      (fn [alue]
                                        {:otsikko (if (= konteksti :koko-maa)
                                                    (str (:hallintayksikko-elynumero alue) " " (:nimi alue))
                                                    (:nimi alue))
                                         :leveys 15})
                                      naytettavat-alueet)
                                    (when yhteensa-sarake?
                                      [{:otsikko "Yhteensä" :leveys 8}])))
        raportin-rivit (when (> (count naytettavat-alueet) 0)
                         (raporttirivit sanktiot-kannassa naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?}))
        raportin-nimi "Sanktioraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
      raportin-otsikot
      raportin-rivit]]))
