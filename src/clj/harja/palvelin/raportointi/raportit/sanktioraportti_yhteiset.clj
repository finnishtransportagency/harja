(ns harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset
  (:require
    [harja.domain.laadunseuranta.sanktio :as sanktiot-domain]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [clojure.string :as str]
    [clojure.set :as set]
    [harja.kyselyt.konversio :as konv]
    [harja.kyselyt.urakan-toimenpiteet :as toimenpiteet-kyselyt]
    [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
    [harja.pvm :as pvm]
    [harja.domain.urakka :as urakka-domain]))

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (if (:toimenpidekoodi_taso2 rivi)
    (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito")
    false))

(defn- suodata-sakot [rivit {:keys [urakka-id hallintayksikko-id sakkoryhma talvihoito?
                                    sanktiotyyppi_koodi sailytettavat-toimenpidekoodit]}]
  (filterv
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
        (or (nil? sailytettavat-toimenpidekoodit)
          (contains? sailytettavat-toimenpidekoodit (:toimenpide_koodi rivi)))))
    rivit))

(defn- suodata-bonukset [bonukset {:keys [laji urakka-id hallintayksikko-id] :as suodattimet}]
  (filterv (fn [bonus]
             (and
               (or (nil? hallintayksikko-id) (= hallintayksikko-id (:hallintayksikko_id bonus)))
               (or (nil? laji) (contains? laji (:laji bonus)))
               (or (nil? urakka-id) (= urakka-id (:urakka-id bonus)))))
    bonukset))
(defn- suodata-muistutukset [rivit {:keys [urakka-id hallintayksikko-id talvihoito?] :as suodattimet}]
  (filterv
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
       (into [] (conj rivi (muistutusten-maara rivit optiot yhteensa-sarake?)))
       rivi))))

(defn luo-rivi-indeksien-summa
  ([otsikko rivit alueet]
   (luo-rivi-indeksien-summa otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (indeksien-summa rivit (merge optiot alue)))
                                      alueet))]
     (if yhteensa-sarake?
       (into [] (conj rivi (indeksien-summa rivit optiot)))
       rivi))))

(defn luo-rivi-kaikki-yht
  ([otsikko rivit alueet] (luo-rivi-kaikki-yht otsikko rivit alueet {}))
  ([otsikko rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
   (let [rivi (apply conj [otsikko] (mapv (fn [alue]
                                            (+ (sakkojen-summa rivit alue)
                                              (indeksien-summa rivit alue)))
                                      alueet))]
     (if yhteensa-sarake?
       (into [] (conj rivi (+ (sakkojen-summa rivit)
                             (indeksien-summa rivit))))
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
       (luo-rivi-sakkojen-summa sakkorivin-nimi rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
       (when-not yllapito?
         (luo-rivi-indeksien-summa "Indeksit yht." rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))
       (when-not yllapito?
         (luo-rivi-kaikki-yht "Sanktiot yht. (indeksikorjattu) " rivit alueet {:yhteensa-sarake? yhteensa-sarake?}))])))

(defn koosta-taulukko [otsikko {:keys [raportin-otsikot nimi osamateriaalit sheet-nimi]}]

  [:taulukko {:otsikko otsikko
              :sheet-nimi sheet-nimi
              :sivuttain-rullattava? true
              :esta-tiivis-grid? true
              :ensimmainen-sarake-sticky? false
              :samalle-sheetille? true}

   ;; Muodostaa taulukon urakka otsikot
   (into [] (concat
              (mapv (fn [otsikko-rivi]
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
                             (mapv (fn [x]
                                     x) rivi)))}]))) osamateriaalit)])

(defn- raporttirivit-talvihoito [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Tehtäväkohtaiset sanktiot / TALVIHOITO"}
   (luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
     {:talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "A-ryhmä (tehtäväkohtainen sanktio)" rivit alueet
     {:sakkoryhma :A
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   ; Content kopioitu figmasta, tyhjän välin voisi formatoida html mutta näin ehkä helpompi?
   (luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
     {:sanktiotyyppi_koodi 13 ;"Talvihoito, päätiet"
      :sakkoryhma :A
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
     {:sanktiotyyppi_koodi 14 ;"Talvihoito, muut tiet"
      :sakkoryhma :A
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
     {:sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
     {:sanktiotyyppi_koodi 13 ;"Talvihoito, päätiet"
      :sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
     {:sanktiotyyppi_koodi 14 ;"Talvihoito, muut tiet"
      :sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa "Talvihoito yhteensä" rivit alueet
     {:talvihoito? true
      :sakkoryhma #{:A :B}
      :yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-indeksien-summa "Indeksit" rivit alueet
     {:talvihoito? true
      :sakkoryhma #{:A :B}
      :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-muut-tuotteet [rivit alueet toimenpide-haku-fn {:keys [yhteensa-sarake?] :as optiot}]
  (let [toimenpiteet (toimenpide-haku-fn)
        loput-toimenpidekoodit (filterv #(and
                                           (not= "23100" (:koodi %)) ;Talvihoito
                                           (not= "23110" (:koodi %)) ;"Liikenneympäristön hoito"
                                           (not= "23120" (:koodi %))) ;"Soratien hoito" 23120
                                 toimenpiteet)]
    (into []
      (concat
        [{:otsikko "Tehtäväkohtaiset sanktiot / MUUT TEHTÄVÄKOKONAISUUDET"}
         (luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
           {:talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-sakkojen-summa "A-ryhmä (tehtäväkohtainen sanktio)" rivit alueet
           {:sakkoryhma :A
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        ;; A-ryhmän eri toimenpiteiden rivit
        ;"Liikenneympäristön hoito"
        [(luo-rivi-sakkojen-summa (str "        • Liikenneympäristön hoito") rivit alueet
           {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit #{"23110"} ;"Liikenneympäristön hoito"
            :sakkoryhma :A
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        ;"Soratien hoito"
        [(luo-rivi-sakkojen-summa (str "        • Soratien hoito") rivit alueet
           {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit #{"23120"} ;"Soratien hoito"
            :sakkoryhma :A
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        ;; Yhdistä loput toimenpiteet "Muut" rivin alle
        [(luo-rivi-sakkojen-summa (str "        • Muut") rivit alueet
           {:sanktiotyyppi_koodi 17 ;" 17 Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit (into #{} (mapv #(str (:koodi %)) loput-toimenpidekoodit))
            :sakkoryhma :A
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
           {:sakkoryhma :B
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa (str "        • Liikenneympäristön hoito") rivit alueet
           {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit #{"23110"} ;"Liikenneympäristön hoito"
            :sakkoryhma :B
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        ;"Soratien hoito"
        [(luo-rivi-sakkojen-summa (str "        • Soratien hoito") rivit alueet
           {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit #{"23120"} ;"Soratien hoito"
            :sakkoryhma :B
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]
        ;; Yhdistä loput toimenpiteet "Muut" rivin alle
        [(luo-rivi-sakkojen-summa (str "        • Muut") rivit alueet
           {:sanktiotyyppi_koodi 17 ;" 17 Muut hoitourakan tehtäväkokonaisuudet"
            :sailytettavat-toimenpidekoodit (into #{} (mapv #(str (:koodi %)) loput-toimenpidekoodit))
            :sakkoryhma :B
            :talvihoito? false
            :yhteensa-sarake? yhteensa-sarake?})]

        [(luo-rivi-sakkojen-summa "Muut tehtäväkokonaisuudet yhteensä" rivit alueet
           {:talvihoito? false
            :sakkoryhma #{:A :B}
            :yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-indeksien-summa "Indeksit" rivit alueet
           {:talvihoito? false
            :sakkoryhma #{:A :B}
            :yhteensa-sarake? yhteensa-sarake?})]))))

(defn- raporttirivit-ryhma-c [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [c-ryhman-rivit (filterv #(= :C (:sakkoryhma %)) rivit)
        c-ryhman-ryhmat (keys (group-by (juxt :sanktiotyyppi_nimi :sanktiotyyppi_koodi) c-ryhman-rivit))]
    (into []
      (concat
        [{:otsikko "C-ryhmä"}]
        (mapv
          (fn [r]
            (luo-rivi-sakkojen-summa (first r) c-ryhman-rivit alueet
              {:sanktiotyyppi_koodi (second r)
               :sakkoryhma :C
               :yhteensa-sarake? yhteensa-sarake?}))
          c-ryhman-ryhmat)
        [(luo-rivi-sakkojen-summa "C-ryhmä yhteensä" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-indeksien-summa "Indeksit" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})]))))

(defn- raporttirivit-suolasakot [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [pohjavesiylitys-rivit (filterv #(= :pohjavesisuolan_ylitys (:sakkoryhma %)) rivit)
        talvisuolaylitys-rivit (filterv #(= :talvisuolan_ylitys (:sakkoryhma %)) rivit)]
    (into []
      (concat
        [{:otsikko "Suolasakot"}]
        [(luo-rivi-sakkojen-summa "Pohjavesialueiden suolankäytön ylitys" pohjavesiylitys-rivit alueet
           {:sanktiotyyppi_koodi 7 ; Suolasakko
            :sakkoryhma :pohjavesisuolan_ylitys
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "Talvisuolan kokonaiskäytön ylitys" talvisuolaylitys-rivit alueet
           {:sanktiotyyppi_koodi 7 ; Suolasakko
            :sakkoryhma :talvisuolan_ylitys
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "Suolasakot yhteensä" rivit alueet
           {:sakkoryhma #{:talvisuolan_ylitys :pohjavesisuolan_ylitys}
            :yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-indeksien-summa "Indeksit" rivit alueet
           {:sakkoryhma #{:talvisuolan_ylitys :pohjavesisuolan_ylitys}
            :yhteensa-sarake? yhteensa-sarake?})]))))

(defn- raporttirivit-henkilosto [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [vaihtosanktio-rivit (filterv #(= :vaihtosanktio (:sakkoryhma %)) rivit)
        testikeskiarvo-rivit (filterv #(= :testikeskiarvo-sanktio (:sakkoryhma %)) rivit)
        tenttikeskiarvo-rivit (filterv #(= :tenttikeskiarvo-sanktio (:sakkoryhma %)) rivit)]
    (into []
      (concat
        [{:otsikko "Henkilöstöön liittyvät sanktiot"}]
        [(luo-rivi-sakkojen-summa "Vastuuhenkilöiden vaihtaminen" vaihtosanktio-rivit alueet
           {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
            :sakkoryhma :vaihtosanktio
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "Vastuuhenkilöiden tenttipistemäärän alentuminen" tenttikeskiarvo-rivit alueet
           {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
            :sakkoryhma :tenttikeskiarvo-sanktio
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "Vastuuhenkilöiden testipistemäärän alentuminen" testikeskiarvo-rivit alueet
           {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
            :sakkoryhma :testikeskiarvo-sanktio
            :yhteensa-sarake? yhteensa-sarake?})]
        [(luo-rivi-sakkojen-summa "Henkilöstöön liittyvät sanktiot yhteensä" rivit alueet
           {:sakkoryhma #{:vaihtosanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio}
            :yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-indeksien-summa "Indeksit" rivit alueet
           {:sakkoryhma #{:vaihtosanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio}
            :yhteensa-sarake? yhteensa-sarake?})]))))

(defn- raporttirivit-lupaus [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (into []
    (concat
      [{:otsikko "Lupaussanktiot"}]
      [(luo-rivi-sakkojen-summa "Lupaussanktiot yht." rivit alueet
         {:sakkoryhma #{:lupaussanktio}
          :yhteensa-sarake? yhteensa-sarake?})
       (luo-rivi-indeksien-summa "Indeksit" rivit alueet
         {:sakkoryhma #{:lupaussanktio}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn- sanktiorivit [rivit alueet toimenpide-haku-fn optiot]
  (into [] (concat
             (raporttirivit-talvihoito rivit alueet optiot)
             (raporttirivit-muut-tuotteet rivit alueet toimenpide-haku-fn optiot)
             (raporttirivit-ryhma-c rivit alueet optiot)
             (raporttirivit-suolasakot rivit alueet optiot)
             (raporttirivit-henkilosto rivit alueet optiot)
             (raporttirivit-lupaus rivit alueet optiot)
             (raporttirivit-yhteensa rivit alueet optiot))))

(defn- raporttirivit-arvonvahennys [sanktiot alueet {:keys [yhteensa-sarake?] :as optiot}]
  (into []
    (concat
      [{:otsikko "Arvonvähennysten yhteenveto"}]
      [(luo-rivi-sakkojen-summa "Arvonvähennykset yhteensä" sanktiot alueet
         {:sakkoryhma #{:arvonvahennyssanktio}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn- raporttirivit-bonukset [bonukset alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [bonustyypit (keys (group-by :laji bonukset))]
    (into []
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
           {:yhteensa-sarake? yhteensa-sarake?})
         (luo-rivi-kaikki-yht "Bonukset yhteensä (indeksikorjattu)" bonukset alueet
           {:sakkoryhma #{:lupaussanktio}
            :yhteensa-sarake? yhteensa-sarake?})]))))

(defn kasittele-sanktioiden-rivit [db {:keys [naytettavat-alueet sanktiot-kannassa yhteensa-sarake?
                                              urakat-joista-loytyi-sanktioita]}]
  (when (> (count naytettavat-alueet) 0)
    (sanktiorivit sanktiot-kannassa naytettavat-alueet
      #(toimenpiteet-kyselyt/hae-mh-urakoiden-toimenpiteet
         db (map :urakka-id
              urakat-joista-loytyi-sanktioita))
      {:yhteensa-sarake? yhteensa-sarake?})))

(defn- yllapitoluokan-raporttirivit
  [luokka luokan-rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko (if luokka
               (str "Ylläpitoluokka " (yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi luokka))
               "Ei ylläpitoluokkaa")}
   (luo-rivi-muistutusten-maara +muistutusrivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})
   (luo-rivi-sakkojen-summa +sakkorivin-nimi-yllapito+ luokan-rivit alueet {:yhteensa-sarake? yhteensa-sarake?})])

(defn kasittele-yllapitoraportti [{:keys [naytettavat-alueet sanktiot-kannassa yhteensa-sarake? urakkatyyppi] :as o}]
  (let [optiot {:yhteensa-sarake? yhteensa-sarake? :urakkatyyppi urakkatyyppi}
        sanktiot-yllapitoluokittain (group-by :yllapitoluokka sanktiot-kannassa)
        yllapitoluokittaiset-rivit (mapcat (fn [[luokka luokan-rivit]]
                                             (yllapitoluokan-raporttirivit luokka luokan-rivit naytettavat-alueet optiot))
                                     sanktiot-yllapitoluokittain)
        yhteensa-rivit (raporttirivit-yhteensa sanktiot-kannassa naytettavat-alueet optiot)]
    (into []
      (when (> (count naytettavat-alueet) 0)
        (concat
          yllapitoluokittaiset-rivit
          yhteensa-rivit)))))

(defn suorita-runko [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id urakkatyyppi sanktiot bonukset
                                     raportin-nimi info-teksti]}]
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
                            sanktiot)
        ;; Poistetaan sanktioden joukostaarvonvähennykset, koska ne on erotettu omaan taulukkoon
        filtteroidyt-sanktiot (filterv #(not= :arvonvahennyssanktio (:sakkoryhma %)) sanktiot-kannassa)

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
        otsikko (if (urakka-domain/yllapitourakka? urakkatyyppi)
                  (str "Sakko- ja bonusraportti " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
                  (str "Sanktiot, bonukset ja arvonvähennykset " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)))
        sanktioiden-rivit (when-not (urakka-domain/yllapitourakka? urakkatyyppi)
                            (kasittele-sanktioiden-rivit db {:konteksti konteksti
                                                             :naytettavat-alueet naytettavat-alueet
                                                             :sanktiot-kannassa filtteroidyt-sanktiot
                                                             :urakat-joista-loytyi-sanktioita urakat-joista-loytyi-sanktioita
                                                             :yhteensa-sarake? yhteensa-sarake?}))
        bonusten-rivit (when-not (urakka-domain/yllapitourakka? urakkatyyppi)
                         (raporttirivit-bonukset bonukset naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?}))
        arvonvahennys-rivit (when-not (urakka-domain/yllapitourakka? urakkatyyppi)
                              (raporttirivit-arvonvahennys sanktiot-kannassa naytettavat-alueet {:yhteensa-sarake? yhteensa-sarake?}))
        paallystysurakan-rivit (when (urakka-domain/yllapitourakka? urakkatyyppi)
                                 (kasittele-yllapitoraportti {:konteksti konteksti
                                                              :urakkatyyppi urakkatyyppi
                                                              :naytettavat-alueet naytettavat-alueet
                                                              :sanktiot-kannassa sanktiot-kannassa
                                                              :urakat-joista-loytyi-sanktioita urakat-joista-loytyi-sanktioita
                                                              :yhteensa-sarake? yhteensa-sarake?}))

        taulukon-tiedot {:urakka nil
                         :otsikko nil
                         :osamateriaalit nil
                         :nimi raportin-nimi
                         :raportin-rivit sanktioiden-rivit
                         :raportin-otsikot raportin-otsikot}

        runko (if (urakka-domain/yllapitourakka? urakkatyyppi)
                [:raportti {:nimi raportin-nimi :orientaatio :landscape}
                 [:otsikko otsikko]
                 [:jakaja nil]

                 ;; Päällystyksen taulukko
                 (koosta-taulukko
                   "Sakot ja bonukset"
                   (-> taulukon-tiedot
                     (assoc :sheet-nimi "Sakot ja bonukset")
                     (assoc :osamateriaalit paallystysurakan-rivit)))]
                [:raportti {:nimi raportin-nimi :orientaatio :landscape}
                 [:otsikko otsikko]
                 [:jakaja nil]
                 ;; Sanktiotaulukko
                 (koosta-taulukko
                   "Sanktiot"
                   (-> taulukon-tiedot
                     (assoc :sheet-nimi "Sanktiot")
                     (assoc :osamateriaalit sanktioiden-rivit)))

                 ;; Bonustaulukko
                 (koosta-taulukko
                   "Bonukset"
                   (-> taulukon-tiedot
                     (assoc :sheet-nimi "Bonukset")
                     (assoc :osamateriaalit bonusten-rivit)))

                 ;; Arvonvähennystaulukko
                 (koosta-taulukko
                   "Arvonvähennykset"
                   (-> taulukon-tiedot
                     (assoc :sheet-nimi "Arvonvähennykset")
                     (assoc :osamateriaalit arvonvahennys-rivit)))])]
    (if info-teksti
      (conj runko [:teksti info-teksti])
      runko)))
