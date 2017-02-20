(ns harja.palvelin.raportointi.raportit.sanktioraportti-yllapito
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.set :as set]
            [clojure.string :as str]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")



(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakkoryhmasta-sakko? rivi)
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))))
    rivit))

(defn- suodata-muistutukset [rivit {:keys [urakka-id hallintayksikko-id] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (not (sanktiot-domain/sakkoryhmasta-sakko? rivi))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))))
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
   (str (count (suodata-muistutukset rivit suodattimet)) " kpl")))

(defn- luo-rivi-sakkojen-summa
  ([otsikko rivit alueet]
   (luo-rivi-sakkojen-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (€)")] (mapv (fn [alue]
                                                         (sakkojen-summa rivit (merge optiot alue)))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (sakkojen-summa rivit optiot))
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
                                                         (indeksien-summa rivit (merge optiot alue)))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (indeksien-summa rivit optiot))
       rivi))))

(defn- luo-rivi-kaikki-yht
  ([otsikko rivit alueet] (luo-rivi-kaikki-yht otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [(str otsikko " (€)")] (mapv (fn [alue]
                                                         (+ (sakkojen-summa rivit alue)
                                                            (indeksien-summa rivit alue)))
                                                       alueet))]
     (if yhteensa-sarake?
       (conj rivi (+ (sakkojen-summa rivit)
                     (indeksien-summa rivit)))
       rivi))))


(defn- yllapitoluokan-raporttirivit
  [luokka luokan-rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko (if luokka
               (str "Ylläpitoluokka: " (yllapitokohteet-domain/yllapitoluokan-lyhyt-nimi luokka))
               "Ei ylläpitoluokkaa")}
   (luo-rivi-muistutusten-maara "Muistutukset yht." luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Indeksit yht." luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Kaikki sakot yht." luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-kaikki-yht "Kaikki yht." luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-yhteensa [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Yhteensä"}
   (luo-rivi-muistutusten-maara "Muistutukset yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Indeksit yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Kaikki sakot yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-kaikki-yht "Kaikki yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])

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
        raportin-nimi "Sanktioraportti"
        optiot {:yhteensa-sarake? yhteensa-sarake?}
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        yllapitoluokittaiset-rivit (mapcat (fn [[luokka luokan-rivit]]
                                             (yllapitoluokan-raporttirivit luokka luokan-rivit naytettavat-alueet optiot))
                                           sanktiot-yllapitoluokittain)
        yhteensa-rivit (raporttirivit-yhteensa sanktiot-kannassa naytettavat-alueet optiot)]

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
                yhteensa-rivit)))]]))
