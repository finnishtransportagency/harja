(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.palvelin.palvelut.urakan-toimenpiteet :as toimenpiteet]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
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
                                     {:sanktiotyyppi "Talvihoito, päätiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "        • Muut tiet" rivit alueet
                                     {:sanktiotyyppi "Talvihoito, muut tiet"
                                      :sakkoryhma :A
                                      :talvihoito? true
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
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
   (yhteiset/luo-rivi-sakkojen-summa "Talvihoito yhteensä" rivit alueet
                                     {:talvihoito? true
                                      :sakkoryhma #{:A :B}
                                      :yhteensa-sarake? yhteensa-sarake?})
   (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet
                                      {:talvihoito? true
                                       :sakkoryhma #{:A :B}
                                       :yhteensa-sarake? yhteensa-sarake?})])

(defn- raporttirivit-muut-tuotteet [rivit alueet toimenpide-haku-fn {:keys [yhteensa-sarake?] :as optiot}]
  (let [toimenpiteet (sort-by #(case (:nimi %)
                                 "Liikenneympäristön hoito" 1
                                 "Soratien hoito" 2
                                 3)
                       (filter #(not= "Talvihoito" (:nimi %))
                         (toimenpide-haku-fn)))]
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
      (map (fn [{:keys [nimi]}]
             (yhteiset/luo-rivi-sakkojen-summa (str "        • " nimi) rivit alueet
               {:sanktiotyyppi #{nimi "Muut hoitourakan tehtäväkokonaisuudet"}
                :sailytettavat-tpkt [nimi]
                :sakkoryhma :A
                :talvihoito? false
                :yhteensa-sarake? yhteensa-sarake?}))
        toimenpiteet)
      [(yhteiset/luo-rivi-sakkojen-summa "B-ryhmä (vakava laiminlyönti)" rivit alueet
         {:sakkoryhma :B
          :talvihoito? false
          :yhteensa-sarake? yhteensa-sarake?})]
      (map (fn [{:keys [nimi]}]
             (yhteiset/luo-rivi-sakkojen-summa (str "        • " nimi) rivit alueet
               {:sanktiotyyppi #{nimi "Muut hoitourakan tehtäväkokonaisuudet"}
                :sailytettavat-tpkt [nimi]
                :sakkoryhma :B
                :talvihoito? false
                :yhteensa-sarake? yhteensa-sarake?}))
        toimenpiteet)

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
        c-ryhman-tyypit (keys (group-by #(:sanktiotyyppi_nimi %) c-ryhman-rivit))]
    (concat
      [{:otsikko "C-ryhmä"}]
      (map
        (fn [nimi]
          (yhteiset/luo-rivi-sakkojen-summa nimi c-ryhman-rivit alueet
            {:sanktiotyyppi #{nimi}
             :sakkoryhma :C
             :yhteensa-sarake? yhteensa-sarake?}))
        c-ryhman-tyypit)
      [(yhteiset/luo-rivi-sakkojen-summa "C-ryhmä yhteensä" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})
       (yhteiset/luo-rivi-indeksien-summa "Indeksit" rivit alueet {:sakkoryhma :C :yhteensa-sarake? yhteensa-sarake?})])))


(defn- raporttirivit [rivit alueet toimenpide-haku-fn optiot]
  (into [] (concat
             (raporttirivit-talvihoito rivit alueet optiot)
             (raporttirivit-muut-tuotteet rivit alueet toimenpide-haku-fn optiot)
             (raporttirivit-ryhma-c rivit alueet optiot)
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
        raportin-nimi (jasenna-raportin-nimi db parametrit)]

    (yhteiset/suorita-runko db user (merge parametrit {:raportin-rivit-fn raportin-rivit-fn
                                                       :db-haku-fn db-haku-fn
                                                       :raportin-nimi raportin-nimi}))))
