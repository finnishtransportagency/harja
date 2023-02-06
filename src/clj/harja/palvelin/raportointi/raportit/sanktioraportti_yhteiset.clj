(ns harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset
  (:require
    [harja.domain.laadunseuranta.sanktio :as sanktiot-domain]
    [clojure.string :as str]
    [clojure.set :as set]
    [harja.kyselyt.konversio :as konv]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
    [harja.pvm :as pvm]
    [harja.domain.urakka :as urakka-domain]))

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (if (:toimenpidekoodi_taso2 rivi)
    (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito")
    false))

(defn- rivi-kuuluu-tpkhn? [{:keys [toimenpidekoodi_taso2]} tpkt]
  ((into #{} (map str/lower-case) tpkt) (str/lower-case toimenpidekoodi_taso2)))

(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id sakkoryhma talvihoito?
                                    sanktiotyyppi_koodi sailytettavat-toimenpidekoodit
                                    poistettavat-tpkt sailytettavat-tpkt]}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakkoryhmasta-sakko? rivi)
        (or (nil? sakkoryhma) (if (set? sakkoryhma)
                                (sakkoryhma (:sakkoryhma rivi))
                                (= sakkoryhma (:sakkoryhma rivi))))
        (or (nil? urakka-id) (= urakka-id (:urakka-id rivi)))
        (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id rivi)))
        (or (nil? sanktiotyyppi_koodi)
          (nil? (:sanktiotyyppi_koodi rivi))
          (if (set? sanktiotyyppi_koodi)
            (contains? sanktiotyyppi_koodi (:sanktiotyyppi_koodi rivi))
            (= sanktiotyyppi_koodi (:sanktiotyyppi_koodi rivi))))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))
        (or (nil? poistettavat-tpkt)
          (and (:toimenpidekoodi_taso2 rivi)
            (not (rivi-kuuluu-tpkhn? rivi poistettavat-tpkt))))
        (or (nil? sailytettavat-tpkt)
          (rivi-kuuluu-tpkhn? rivi sailytettavat-tpkt))
        (or (nil? sailytettavat-toimenpidekoodit)
          (contains? sailytettavat-toimenpidekoodit (:toimenpide_koodi rivi)))))
    rivit))

(defn- suodata-bonukset [bonukset {:keys [laji urakka-id] :as suodattimet}]
  (filter (fn [bonus]
            (and
              (or (nil? laji) (contains? laji (:laji bonus)))
              (or (nil? urakka-id) (= urakka-id (:urakka-id bonus)))))
    bonukset))
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
  ([rivit] (muistutusten-maara rivit {} false))
  ([rivit suodattimet] (muistutusten-maara rivit suodattimet false))
  ([rivit suodattimet yhteensa-sarake?]
   (if yhteensa-sarake?
     [:arvo-ja-yksikko-korostettu {:arvo (count (suodata-muistutukset rivit suodattimet))
                                   :yksikko " kpl"
                                   :fmt :numero
                                   :korosta-hennosti? true}]
     [:arvo-ja-yksikko {:arvo (count (suodata-muistutukset rivit suodattimet))
                                           :yksikko " kpl"
                                           :fmt :numero}])))


(defn sakkojen-summa
  ([rivit] (sakkojen-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:summa %) 0)
                 laskettavat)))))

(defn bonusten-summa [rivit suodattimet]
  (let [laskettavat (suodata-bonukset rivit suodattimet)]
    (reduce + (map
                #(or (:summa %) 0)
                laskettavat))))

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
       (conj rivi
         [:arvo-ja-yksikko-korostettu {:arvo (sakkojen-summa rivit optiot)
                                       :fmt :raha
                                       :korosta-hennosti? true}])
       rivi))))

(defn luo-rivi-bonusten-summa [otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                           (bonusten-summa rivit (merge optiot alue)))
                                     alueet))]
    (if yhteensa-sarake?
      (conj rivi
        [:arvo-ja-yksikko-korostettu {:arvo (bonusten-summa rivit optiot)
                                      :fmt :raha
                                      :korosta-hennosti? true}])
      rivi)))

(defn luo-rivi-muistutusten-maara
  ([otsikko rivit alueet]
   (luo-rivi-muistutusten-maara otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (muistutusten-maara rivit (merge optiot alue)))
                                          alueet))]
     (if yhteensa-sarake?
       (conj rivi (muistutusten-maara rivit optiot yhteensa-sarake?))
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
  (let [yllapito? (urakka-domain/yllapitourakka? urakkatyyppi)
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

(defn koosta-taulukko [{:keys [raportin-otsikot nimi osamateriaalit]}]

  [:taulukko {:otsikko "Sanktiot"
              :sheet-nimi nimi
              :sivuttain-rullattava? true
              :esta-tiivis-grid? true
              :ensimmainen-sarake-sticky? false
              :samalle-sheetille? true}

   ;; Muodostaa taulukon urakka otsikot
   (into [] (concat
             (map (fn [otsikko-rivi]
                    otsikko-rivi) raportin-otsikot)))
   ;; Taulukon rivit 
   (mapcat (fn [rivi]

             (if (:otsikko rivi)
               [rivi]

               (let [valkoinen? (str/includes? (first rivi) "•")
                     korosta-rivi? (or (str/includes? (first rivi) "yhteensä")
                                       (str/includes? (first rivi) "yht.")
                                       (str/includes? (first rivi) "Indeksit"))
                     harmaa? (not (or valkoinen? korosta-rivi?))]

                 [{:korosta-harmaa? harmaa?
                   :valkoinen? valkoinen?
                   :korosta-hennosti? korosta-rivi?
                   :lihavoi? korosta-rivi?
                   :rivi (into []
                               (concat
                                (map (fn [x]
                                       x) rivi)))}]))) osamateriaalit)])
(defn- raporttirivit-bonukset [bonukset alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [bonustyypit (keys (group-by :laji bonukset))
        r (luo-rivi-bonusten-summa "Bonukset yhteensä" bonukset alueet
            {:yhteensa-sarake? yhteensa-sarake?})]
    (concat
      [{:otsikko "Bonusten yhteenveto"}]
      (map
        (fn [b]
          (luo-rivi-bonusten-summa (sanktiot-domain/sanktiolaji->teksti (keyword b)) bonukset alueet
            {:laji #{b}
             :yhteensa-sarake? yhteensa-sarake?}))
        bonustyypit)
      [(luo-rivi-bonusten-summa "Bonukset yhteensä" bonukset alueet
         {:yhteensa-sarake? yhteensa-sarake?})
       (luo-rivi-indeksien-summa "Indeksit" bonukset alueet
         {:sakkoryhma #{:lupaussanktio}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn suorita-runko [db user {:keys [alkupvm loppupvm
                                     urakka-id hallintayksikko-id
                                     urakkatyyppi db-haku-fn
                                     raportin-nimi raportin-rivit-fn
                                     info-teksti sanktiotyypit]}]
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
        otsikko (str "Sanktiot, bonukset ja arvonvähennykset " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        sanktioiden-rivit (kasittele-sanktioiden-rivit db {:konteksti konteksti
                                                           :naytettavat-alueet naytettavat-alueet
                                                           :sanktiot-kannassa sanktiot-kannassa
                                                           :urakat-joista-loytyi-sanktioita urakat-joista-loytyi-sanktioita
                                                           :yhteensa-sarake? yhteensa-sarake?})
        bonusten-rivit (raporttirivit-bonukset bonukset naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?})

        taulukon-tiedot {:urakka nil
                         :otsikko nil
                         :osamateriaalit nil
                         :nimi raportin-nimi
                         :raportin-rivit sanktioiden-rivit
                         :raportin-otsikot raportin-otsikot}

        runko [:raportti {:nimi raportin-nimi
                          :orientaatio :landscape}
               
               [:otsikko otsikko]
               [:jakaja nil]

               ;; Sanktiotaulukko
               (koosta-taulukko
                 "Sanktiot"
                 (-> taulukon-tiedot
                   (assoc :sheet-nimi "Sanktiot")
                   (assoc :osamateriaalit sanktioiden-rivit)))

               (koosta-taulukko
                 "Bonukset"
                 (-> taulukon-tiedot
                   (assoc :sheet-nimi "Bonukset")
                   (assoc :osamateriaalit bonusten-rivit)))]]
    (if info-teksti
      (conj runko [:teksti info-teksti])
      runko)))
