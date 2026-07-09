(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.palvelin.palvelut.urakan-toimenpiteet :as toimenpiteet]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.sanktiot :as sanktiot-kyselyt]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(declare hae-sanktiot hae-bonukset hae-urakkataso-sanktiot hae-urakkataso-bonukset hae-urakkataso-sanktiolajit hae-urakkataso-bonuslajit)

(defn- jasenna-raportin-nimi [db parametrit]
  (let [urakan-tiedot (if (not (nil? (:urakka-id parametrit)))
                        (first (urakat-kyselyt/hae-urakka db (:urakka-id parametrit)))
                        nil)
        hallintayksikon-tiedot (if (not (nil? (:elinvoimakeskus-id parametrit)))
                                 (first (organisaatiot-kyselyt/hae-organisaatio db (:elinvoimakeskus-id parametrit)))
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
(defn suorita-vanha [db user {:keys [urakka-id elinvoimakeskus-id urakkatyyppi alkupvm loppupvm] :as parametrit}]
  (let [sanktiot (hae-sanktiot db
                   {:urakka urakka-id
                    :elinvoimakeskus elinvoimakeskus-id
                    :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                    :alku alkupvm
                    :loppu loppupvm})
        bonukset (hae-bonukset db {:urakka urakka-id
                                   :elinvoimakeskus elinvoimakeskus-id
                                   :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                   :alku alkupvm
                                   :loppu loppupvm})
        raportin-nimi (jasenna-raportin-nimi db parametrit)]

    (yhteiset/suorita-runko db user (merge parametrit {:sanktiot sanktiot
                                                       :bonukset bonukset
                                                       :raportin-nimi raportin-nimi}))))



; ----------------------------------------- ;
;   Urakkataso-sanktioraportti (MHU25+)      ;
; ----------------------------------------- ;

(defn- koosta-urakkataso-runko
  "Muodostaa urakkataso-sanktioraportin raporttirakennelman.
   Käyttää profiili-driven kyselyitä jotka palauttavat sanktiolaji/bonuslaji-tiedot
   suoraan tietokannasta (sanktio_profiili_*, bonus_profiili_*-taulut).

   sanktiolajit ja bonuslajit sisältävät kaikki urakan profilin mukaiset lajit ja tyypit
   (myös tyhjät = nollasummat)."
  [urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit]
  (let [otsikko (str "Sanktiot, bonukset ja arvovähennykset — " urakan-nimi
                  " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))

        ;; Erotetaan sanktioista arvonvähennykset
        ;; arvonvahennyssanktio-koodin sanktiot näytetään omassa taulukossaan
        sanktiot-ilman-arvonvahennyksia (filterv #(not= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)
        arvonvahennykset-summa (reduce + 0 (map #(or (:summa %) 0) arvonvahennykset))

        ;; Lasketaan yhteenvetoarvot suoraan profiili-driven tuloksista
        sanktiot-yhteensa (reduce + 0 (map #(or (:summa %) 0) sanktiot-ilman-arvonvahennyksia))
        bonukset-yhteensa (reduce + 0 (map #(or (:summa %) 0) bonukset))
        muistutusten-maara (count (filterv #(= "muistutus" (:sanktiolaji_koodi %)) sanktiot-ilman-arvonvahennyksia))
        vastuuhenkilon-vaihto-summa (reduce + 0
                                     (map #(or (:summa %) 0)
                                       (filterv #(= "vastuuhenkilon-vaihto" (:sanktiotyyppi_koodi %)) sanktiot-ilman-arvonvahennyksia)))

        yhteenveto-data [{:avain "Sanktiot yhteensä" :arvo sanktiot-yhteensa :fmt :raha}
                         {:avain "Bonukset yhteensä" :arvo bonukset-yhteensa :fmt :raha}
                         {:avain "Kirjalliset muistutukset" :arvo (str muistutusten-maara " kpl")}
                         {:avain "Vastuuhenkilön vaihto" :arvo vastuuhenkilon-vaihto-summa :fmt :raha}
                         {:avain "Arvovähennykset" :arvo arvonvahennykset-summa :fmt :raha}
                         {:avain "Yhteensä" :arvo (+ sanktiot-yhteensa bonukset-yhteensa arvonvahennykset-summa) :fmt :raha :lihavoi? true}]

        ;; Yhdistetään profiilin lajit/tyypit dataan - näytetään kaikki, myös tyhjät
        ;; Luodaan maps: laji+tyyppi -> summa
        sanktio-data-map (reduce
                           (fn [m s]
                             (let [key [(:sanktiolaji_koodi s) (:sanktiotyyppi_koodi s)]]
                               (update m key (fnil + 0) (or (:summa s) 0))))
                           {}
                           sanktiot-ilman-arvonvahennyksia)

        ;; Muodostetaan sanktiorivit: kaikki profiilin lajit ja tyypit
        ;; Jos lajille ei löydy tyyppiä, näytetään "Muut sanktiot" -rivinä
        sanktio-rivit
        (let [;; Ryhmitellään sanktiolajit
              ;; group-by palauttaa {laji-koodi [rivi1 rivi2 ...]}, joten
              ;; laji-tiedot haetaan ensimmäisestä rivistä: (first (second %))
              lajit-grupoituina (group-by :sanktiolaji_koodi sanktiolajit)
              lajit-jarjestyksessa (sort-by :sanktiolaji_jarjestys
                                  (mapv #(let [ensimmainen-rivi (first (second %))]
                                           (hash-map :sanktiolaji_koodi (:sanktiolaji_koodi ensimmainen-rivi)
                                                     :sanktiolaji_nimi (:sanktiolaji_nimi ensimmainen-rivi)
                                                     :sanktiolaji_jarjestys (:sanktiolaji_jarjestys ensimmainen-rivi)))
                                    lajit-grupoituina))
              laji-tyyppi-map (reduce (fn [m [k v]] (assoc m k (mapv #(select-keys % [:sanktiotyyppi_koodi :sanktiotyyppi_nimi]) v))) {} lajit-grupoituina)]
          (mapcat
            (fn [laji]
              (let [laji-koodi (:sanktiolaji_koodi laji)
                    laji-nimi (:sanktiolaji_nimi laji)
                    laji-tyypit (get laji-tyyppi-map laji-koodi)
                    ;; Laske lajin kokonaissumma
                    laji-summa (reduce + 0
                                     (map #(or (get sanktio-data-map [laji-koodi (:sanktiotyyppi_koodi %)]) 0)
                                       laji-tyypit))]
                (concat
                  ;; Lajiotsikko
                  [{:korosta-harmaa? true
                    :lihavoi? true
                    :rivi (rivi laji-nimi nil)}]
                  ;; Tyyppirivit
                  (mapv
                    (fn [tyyppi]
                      (let [tyyppi-koodi (:sanktiotyyppi_koodi tyyppi)
                            tyyppi-nimi (:sanktiotyyppi_nimi tyyppi)
                            summa (or (get sanktio-data-map [laji-koodi tyyppi-koodi]) 0)
                            nolla-summa? (zero? summa)]
                        {:korosta-harmaa? nolla-summa?  ; Nollasummat harmaalla
                         :rivi (rivi (or tyyppi-nimi laji-nimi) summa)}))
                    laji-tyypit)
                  ;; Laji yhteensä -rivi
                  [{:lihavoi? true
                    :korosta-hennosti? true
                    :rivi (rivi "Yhteensä" laji-summa)}])))
            lajit-jarjestyksessa))

        ;; Sanktioiden kokonaissumma
        sanktiot-yhteensa-laskettu (reduce + 0 (map #(or (:summa %) 0) sanktiot-ilman-arvonvahennyksia))
        sanktio-yhteensa-rivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi (rivi "Sanktiot yhteensä" sanktiot-yhteensa-laskettu)}]

        ;; Bonusrivit - yhdistetään profiili dataan
        bonus-data-map (reduce
                         (fn [m b]
                           (update m (:bonuslaji_koodi b) (fnil + 0) (or (:summa b) 0)))
                         {}
                         bonukset)
        bonus-lajit-jarjestyksessa (sort-by :bonuslaji_jarjestys bonuslajit)
        bonus-rivit (mapv
                      (fn [laji]
                        (let [laji-koodi (:bonuslaji_koodi laji)
                              summa (or (get bonus-data-map laji-koodi) 0)
                              nolla-summa? (zero? summa)]
                          {:korosta-harmaa? nolla-summa?
                           :rivi (rivi (:bonuslaji_nimi laji) summa)}))
                      bonus-lajit-jarjestyksessa)
        bonukset-yhteensa-laskettu (reduce + 0 (map #(or (:summa %) 0) bonukset))
        bonus-yhteensa-rivi [{:lihavoi? true
                              :korosta-hennosti? true
                              :rivi (rivi "Bonukset yhteensä" bonukset-yhteensa-laskettu)}]

        ;; Arvonvähennysrivit
        arvonvahennykset-lajeittain (group-by :sanktiolaji_nimi arvonvahennykset)
        arvonvahennys-rivit (mapv (fn [[laji-nimi laji-arvonvahennykset]]
                                    (let [summa (reduce + 0 (map #(or (:summa %) 0) laji-arvonvahennykset))]
                                      (rivi (or laji-nimi "Arvonvähennys") summa)))
                                  arvonvahennykset-lajeittain)
        arvonvahennys-yhteensa-rivi [{:lihavoi? true
                                       :korosta-hennosti? true
                                       :rivi (rivi "Yhteensä" arvonvahennykset-summa)}]]

    [:raportti {:nimi urakan-nimi :orientaatio :landscape}
     [:otsikko otsikko]
     [:jakaja nil]

     [:display-flex
      [:sininen-laatikko {:otsikko "Yhteenveto"}
       yhteenveto-data]]

     ;; Sanktiotaulukko
     [:taulukko {:otsikko "Sanktiot"
                 :sheet-nimi "Sanktiot"
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja (when (empty? sanktiolajit) "Ei sanktiolajeja profiilissa.")}
      [{:leveys 12 :otsikko "Sanktiolaji / Tyyppi"}
       {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
      (into [] (concat sanktio-rivit sanktio-yhteensa-rivi))]

     ;; Bonustaulukko
     [:taulukko {:otsikko "Bonukset"
                 :sheet-nimi "Bonukset"
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja (when (empty? bonuslajit) "Ei bonuslajeja profiilissa.")}
      [{:leveys 12 :otsikko "Bonuslaji"}
       {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
      (into [] (concat bonus-rivit bonus-yhteensa-rivi))]

     ;; Arvonvähennystaulukko (bonusten jälkeen)
     [:taulukko {:otsikko "Arvonvähennykset"
                 :sheet-nimi "Arvonvähennykset"
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja (when (empty? arvonvahennykset) "Ei arvonvähennyksiä.")}
      [{:leveys 12 :otsikko "Arvonvähennyslaji"}
       {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
      (into [] (concat arvonvahennys-rivit arvonvahennys-yhteensa-rivi))]]))

(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiili-driven SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset."
  [db _user {:keys [urakka-id alkupvm loppupvm hoitovuosi] :as parametrit}]
  (let [urakan-tiedot (when urakka-id
                        (first (urakat-kyselyt/hae-urakka db urakka-id)))
        urakan-nimi (or (:nimi urakan-tiedot) "")
        hoitovuosi (or hoitovuosi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) alkupvm))

        ;; Haetaan kaikki profiilin sanktiolajit ja -tyypit (myös tyhjät = nollasummat)
        sanktiolajit (hae-urakkataso-sanktiolajit db {:urakka urakka-id
                                                      :hoitovuosi hoitovuosi
                                                      :alku alkupvm
                                                      :loppu loppupvm})
        bonuslajit (hae-urakkataso-bonuslajit db {:urakka urakka-id
                                                  :hoitovuosi hoitovuosi})

        ;; Haetaan sanktioden ja bonusten data
        sanktiot (hae-urakkataso-sanktiot db {:urakka urakka-id
                                              :alku alkupvm
                                              :loppu loppupvm
                                              :hoitovuosi hoitovuosi})
        bonukset (hae-urakkataso-bonukset db {:urakka urakka-id
                                              :alku alkupvm
                                              :loppu loppupvm
                                              :hoitovuosi hoitovuosi})

        ;; Haetaan arvonvähennykset erikseen (sanktiolaji_koodi = 'arvonvahennyssanktio')
        arvonvahennykset (filterv #(= "arvonvahennyssanktio" (:sanktiolaji_koodi %)) sanktiot)]
    (koosta-urakkataso-runko urakan-nimi alkupvm loppupvm sanktiot bonukset arvonvahennykset sanktiolajit bonuslajit)))
