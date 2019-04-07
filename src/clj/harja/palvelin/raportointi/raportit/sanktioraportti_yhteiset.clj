(ns harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset
  (:require
    [harja.domain.laadunseuranta.sanktio :as sanktiot-domain]
    [jeesql.core :refer [defqueries]]
    [harja.tyokalut.functor :refer [fmap]]
    [clojure.string :as str]
    [clojure.set :as set]
    [harja.kyselyt.konversio :as konv]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.kyselyt.hallintayksikot :as hallintayksikot-q]))

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (if (:toimenpidekoodi_taso2 rivi)
    (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito")
    false))

(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id sakkoryhma talvihoito? sanktiotyyppi] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakkoryhmasta-sakko? rivi)
        (or (nil? sakkoryhma) (if (set? sakkoryhma)
                                (sakkoryhma (:sakkoryhma rivi))
                                (= sakkoryhma (:sakkoryhma rivi))))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? sanktiotyyppi) (str/includes? (str/lower-case (:sanktiotyyppi_nimi rivi)) (str/lower-case sanktiotyyppi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- suodata-muistutukset [rivit {:keys [urakka-id hallintayksikko-id talvihoito?] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (not (sanktiot-domain/sakkoryhmasta-sakko? rivi))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn muistutusten-maara
  ([rivit] (muistutusten-maara rivit {}))
  ([rivit suodattimet]
   [:arvo-ja-yksikko {:arvo (count (suodata-muistutukset rivit suodattimet))
                      :yksikko " kpl"
                      :fmt? false}]))


(defn sakkojen-summa
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


(defn luo-rivi-sakkojen-summa
  ([otsikko rivit alueet]
   (luo-rivi-sakkojen-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (sakkojen-summa rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (sakkojen-summa rivit optiot))
       rivi))))

(defn luo-rivi-muistutusten-maara
  ([otsikko rivit alueet]
   (luo-rivi-muistutusten-maara otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (muistutusten-maara rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (muistutusten-maara rivit optiot))
       rivi))))

(defn luo-rivi-indeksien-summa
  ([otsikko rivit alueet]
   (luo-rivi-indeksien-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (indeksien-summa rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (indeksien-summa rivit optiot))
       rivi))))

(defn luo-rivi-kaikki-yht
  ([otsikko rivit alueet] (luo-rivi-kaikki-yht otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (+ (sakkojen-summa rivit alue)
                                               (indeksien-summa rivit alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (+ (sakkojen-summa rivit)
                     (indeksien-summa rivit)))
       rivi))))

(def +muistutusrivin-nimi-hoito+ "Muistutukset yht.")
(def +muistutusrivin-nimi-yllapito+ "Muistutukset")

(def +sakkorivin-nimi-hoito+ "Kaikki sakot yht.")
(def +sakkorivin-nimi-yllapito+ "Sakot (-) ja bonukset (+)")

(defn raporttirivit-yhteensa [rivit alueet {:keys [yhteensa-sarake? urakkatyyppi] :as optiot}]
  (let [yllapito? (or (= urakkatyyppi :paallystys)
                      (= urakkatyyppi :paikkaus)
                      (= urakkatyyppi :tiemerkinta))
        sakkorivin-nimi (if yllapito?
                          +sakkorivin-nimi-yllapito+
                          +sakkorivin-nimi-hoito+)
        muistutusrivin-nimi (if yllapito?
                              +muistutusrivin-nimi-yllapito+
                              +muistutusrivin-nimi-hoito+)]
    (keep identity
          [{:otsikko "Yhteensä"}
           (luo-rivi-muistutusten-maara muistutusrivin-nimi rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
           (when-not yllapito?
             (luo-rivi-indeksien-summa "Indeksit yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))
           (luo-rivi-sakkojen-summa sakkorivin-nimi rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
           (when-not yllapito?
             (luo-rivi-kaikki-yht "Kaikki yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))])))

(defn suorita-runko [db user {:keys [alkupvm loppupvm
                                     urakka-id hallintayksikko-id
                                     urakkatyyppi db-haku-fn
                                     raportin-nimi raportin-rivit-fn
                                     info-teksti]}]
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
                                (db-haku-fn db
                                            {:urakka urakka-id
                                             :hallintayksikko hallintayksikko-id
                                             :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                             :alku alkupvm
                                             :loppu loppupvm}))

        urakat-joista-loytyi-sanktioita (into #{} (map #(select-keys % [:urakka-id :nimi :loppupvm]) sanktiot-kannassa))
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
        otsikko (yleinen/raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        raportin-rivit (raportin-rivit-fn {:konteksti konteksti
                                           :naytettavat-alueet naytettavat-alueet
                                           :sanktiot-kannassa sanktiot-kannassa
                                           :urakat-joista-loytyi-sanktioita urakat-joista-loytyi-sanktioita
                                           :yhteensa-sarake? yhteensa-sarake?})
        runko [:raportti {:nimi raportin-nimi
                          :orientaatio :landscape}
               [:taulukko {:otsikko otsikko
                           :oikealle-tasattavat-kentat (into #{} (range 1 (yleinen/sarakkeiden-maara raportin-otsikot)))
                           :sheet-nimi raportin-nimi}
                raportin-otsikot
                raportin-rivit]]]
    (if info-teksti
      (conj runko [:teksti info-teksti])
      runko)))
