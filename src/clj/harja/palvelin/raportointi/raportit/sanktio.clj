(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.palvelin.palvelut.urakan-toimenpiteet :as toimenpiteet]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.sanktiot :as sanktiot-kyselyt]
            [harja.kyselyt.urakan-toimenpiteet :as toimenpiteet-kyselyt]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- raporttirivit-talvihoito [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  [{:otsikko "Tehtäväkohtaiset sanktiot / TALVIHOITO"}
   (yhteiset/luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
                                         {:talvihoito? true
                                          :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "A-ryhmä (tehtäväkohtainen sanktio)" rivit alueet
                                     {:sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   ; Content kopioitu figmasta, tyhjän välin voisi formatoida html mutta näin ehkä helpompi?
   (yhteiset/luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
     {:sanktiotyyppi_koodi 13 ;"Talvihoito, päätiet"
      :sakkoryhma :A
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
     {:sanktiotyyppi_koodi 14 ;"Talvihoito, muut tiet"
      :sakkoryhma :A
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
     {:sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Päätiet" rivit alueet
     {:sanktiotyyppi_koodi 13 ;"Talvihoito, päätiet"
      :sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
     {:sanktiotyyppi_koodi 14 ;"Talvihoito, muut tiet"
      :sakkoryhma :B
      :talvihoito? true
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "Talvihoito yhteensä" rivit alueet
     {:talvihoito? true
      :sakkoryhma #{:A :B}
      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
     {:talvihoito? true
      :sakkoryhma #{:A :B}
      :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-muut-tuotteet [rivit alueet toimenpide-haku-fn {:keys [yhteensa-sarake?] :as optiot}]
  (let [toimenpiteet (toimenpide-haku-fn)
        loput-toimenpiteet (filter #(and
                                (not= "Talvihoito" (:nimi %))
                                (not= "Liikenneympäristön hoito" (:nimi %))
                                (not= "Soratien hoito" (:nimi %)))
                             toimenpiteet)
        loput-toimenpidekoodit (filter #(and
                                             (not= "23100" (:koodi %)) ;Talvihoito
                                             (not= "23110" (:koodi %)) ;"Liikenneympäristön hoito"
                                             (not= "23120" (:koodi %))) ;"Soratien hoito" 23120
                                    toimenpiteet)]
    (concat
      [{:otsikko "Tehtäväkohtaiset sanktiot / MUUT TEHTÄVÄKOKONAISUUDET"}
       (yhteiset/luo-rivi-muistutusten-maara "Muistutukset" rivit alueet
         {:talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-sakkojen-summa "A-ryhmä (tehtäväkohtainen sanktio)" rivit alueet
         {:sakkoryhma :A
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      ;; A-ryhmän eri toimenpiteiden rivit
      ;"Liikenneympäristön hoito"
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Liikenneympäristön hoito") rivit alueet
         {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit #{"23110"} ;"Liikenneympäristön hoito"
          :sakkoryhma :A
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      ;"Soratien hoito"
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Soratien hoito") rivit alueet
         {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit #{"23120"}        ;"Soratien hoito"
          :sakkoryhma :A
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      ;; Yhdistä loput toimenpiteet "Muut" rivin alle
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Muut") rivit alueet
         {:sanktiotyyppi_koodi 17 ;" 17 Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit (into #{} (mapv #(str (:koodi %)) loput-toimenpidekoodit))
          :sakkoryhma :A
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
         {:sakkoryhma :B
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Liikenneympäristön hoito") rivit alueet
         {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit #{"23110"} ;"Liikenneympäristön hoito"
          :sakkoryhma :B
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      ;"Soratien hoito"
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Soratien hoito") rivit alueet
         {:sanktiotyyppi_koodi 17 ;"Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit #{"23120"} ;"Soratien hoito"
          :sakkoryhma :B
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      ;; Yhdistä loput toimenpiteet "Muut" rivin alle
      [(yhteiset/luo-rivi-sakkojen-summa (str "        • Muut") rivit alueet
         {:sanktiotyyppi_koodi 17 ;" 17 Muut hoitourakan tehtäväkokonaisuudet"
          :sailytettavat-toimenpidekoodit (into #{} (mapv #(str (:koodi %)) loput-toimenpidekoodit))
          :sakkoryhma :B
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]

      [(yhteiset/luo-rivi-sakkojen-summa "Muut tehtäväkokonaisuudet yhteensä" rivit alueet
         {:talvihoito? false
          :sakkoryhma #{:A :B}
          :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
         {:talvihoito? false
          :sakkoryhma #{:A :B}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn- raporttirivit-ryhma-c [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [c-ryhman-rivit (filter #(= :C (:sakkoryhma %)) rivit)
        c-ryhman-ryhmat (keys (group-by (juxt :sanktiotyyppi_nimi :sanktiotyyppi_koodi) c-ryhman-rivit))]
    (concat
      [{:otsikko "C-ryhmä"}]
       (map
        (fn [r]
          (yhteiset/luo-rivi-sakkojen-summa (first r) c-ryhman-rivit alueet
            {:sanktiotyyppi_koodi (second r)
             :sakkoryhma :C
             :yhteensa-sarake? yhteensa-sarake?}))
        c-ryhman-ryhmat)
      [(yhteiset/luo-rivi-sakkojen-summa "C-ryhmä yhteensä" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})])))

(defn- raporttirivit-suolasakot [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [pohjavesiylitys-rivit (filter #(= :pohjavesisuolan_ylitys (:sakkoryhma %)) rivit)
        talvisuolaylitys-rivit (filter #(= :talvisuolan_ylitys (:sakkoryhma %)) rivit)]
    (concat
      [{:otsikko "Suolasakot"}]
      [(yhteiset/luo-rivi-sakkojen-summa "Pohjavesialueiden suolankäytön ylitys" pohjavesiylitys-rivit alueet
         {:sanktiotyyppi_koodi 7 ; Suolasakko
          :sakkoryhma :pohjavesisuolan_ylitys
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "Talvisuolan kokonaiskäytön ylitys" talvisuolaylitys-rivit alueet
         {:sanktiotyyppi_koodi 7 ; Suolasakko
          :sakkoryhma :talvisuolan_ylitys
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "Suolasakot yhteensä" rivit alueet
         {:sakkoryhma #{:talvisuolan_ylitys :pohjavesisuolan_ylitys}
          :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
         {:sakkoryhma #{:talvisuolan_ylitys :pohjavesisuolan_ylitys}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn- raporttirivit-henkilosto [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (let [vaihtosanktio-rivit (filter #(= :vaihtosanktio (:sakkoryhma %)) rivit)
        testikeskiarvo-rivit (filter #(= :testikeskiarvo-sanktio (:sakkoryhma %)) rivit)
        tenttikeskiarvo-rivit (filter #(= :tenttikeskiarvo-sanktio (:sakkoryhma %)) rivit)]
    (concat
      [{:otsikko "Henkilöstöön liittyvät sanktiot"}]
      [(yhteiset/luo-rivi-sakkojen-summa "Vastuuhenkilöiden vaihtaminen" vaihtosanktio-rivit alueet
         {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
          :sakkoryhma :vaihtosanktio
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "Vastuuhenkilöiden tenttipistemäärän alentuminen" tenttikeskiarvo-rivit alueet
         {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
          :sakkoryhma :tenttikeskiarvo-sanktio
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "Vastuuhenkilöiden testipistemäärän alentuminen" testikeskiarvo-rivit alueet
         {:sanktiotyyppi_koodi 0 ;"Ei tarvita sanktiotyyppiä"
          :sakkoryhma :testikeskiarvo-sanktio
          :yhteensa-sarake? yhteensa-sarake?})]
      [(yhteiset/luo-rivi-sakkojen-summa "Henkilöstöön liittyvät sanktiot yhteensä" rivit alueet
         {:sakkoryhma #{:vaihtosanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio}
          :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
         {:sakkoryhma #{:vaihtosanktio :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio}
          :yhteensa-sarake? yhteensa-sarake?})])))

(defn- raporttirivit-lupaus [rivit alueet {:keys [yhteensa-sarake?] :as optiot}]
  (concat
    [{:otsikko "Lupaussanktiot"}]
    [(yhteiset/luo-rivi-sakkojen-summa "Lupaussanktiot yht." rivit alueet
       {:sakkoryhma #{:lupaussanktio}
        :yhteensa-sarake? yhteensa-sarake?})
     (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
       {:sakkoryhma #{:lupaussanktio}
        :yhteensa-sarake? yhteensa-sarake?})]))

(defn- raporttirivit [rivit alueet toimenpide-haku-fn optiot]
  (into [] (concat
             (raporttirivit-talvihoito rivit alueet optiot)
             (raporttirivit-muut-tuotteet rivit alueet toimenpide-haku-fn optiot)
             (raporttirivit-ryhma-c rivit alueet optiot)
             (raporttirivit-suolasakot rivit alueet optiot)
             (raporttirivit-henkilosto rivit alueet optiot)
             (raporttirivit-lupaus rivit alueet optiot)
             (yhteiset/raporttirivit-yhteensa rivit alueet optiot))))


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
(defn suorita [db user parametrit]
  (let [raportin-rivit-fn (fn [{:keys [naytettavat-alueet sanktiot-kannassa yhteensa-sarake?
                                       urakat-joista-loytyi-sanktioita]}]
                           (when (> (count naytettavat-alueet) 0)
                             (raporttirivit sanktiot-kannassa naytettavat-alueet
                               #(toimenpiteet-kyselyt/hae-mh-urakoiden-toimenpiteet
                                  db (map :urakka-id
                                       urakat-joista-loytyi-sanktioita))
                               {:yhteensa-sarake? yhteensa-sarake?})))
          db-haku-fn hae-sanktiot
        raportin-nimi (jasenna-raportin-nimi db parametrit)
        sanktiotyypit (sanktiot-kyselyt/hae-sanktiotyypit db)]

    (yhteiset/suorita-runko db user (merge parametrit {:raportin-rivit-fn raportin-rivit-fn
                                                       :db-haku-fn db-haku-fn
                                                       :raportin-nimi raportin-nimi
                                                       :sanktiotyypit sanktiotyypit}))))
