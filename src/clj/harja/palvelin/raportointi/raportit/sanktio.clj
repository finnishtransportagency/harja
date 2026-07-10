(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.organisaatiot :as organisaatiot-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.pvm :as pvm]))

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

(defn- muodosta-sanktio-taulukko
  "Muodostaa yhden sanktiolaji-ryhmän taulukon.
   Lajit ovat jo ryhmiteltyjä ja järjestettyjä tietokannan mukaisesti."
  [lajit sanktio-data-map]
  (let [ensimmainen (first lajit)
        laji-koodi (:sanktiolaji_koodi ensimmainen)
        laji-nimi (:sanktiolaji_nimi ensimmainen)
        ;; Jos lajilla ei ole tyyppejä, käytetään lajia itsessään
        tyypit (or (not-empty (rest lajit)) [ensimmainen])
        ;; Laske lajin kokonaissumma
        laji-summa (reduce + 0
                     (map #(or (get sanktio-data-map [laji-koodi (:sanktiotyyppi_koodi %)]) 0)
                       tyypit))]
    [:taulukko {:otsikko laji-nimi
                :sheet-nimi (or laji-koodi "sanktio")
                :viimeinen-rivi-yhteenveto? true
                :tyhja "Ei tietoja."}
     (if (> (count tyypit) 1)
       [{:leveys 12 :otsikko "Tyyppi"}
        {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
       [{:leveys 12 :otsikko "Sanktiolaji"}
        {:leveys 15 :otsikko "Summa (€)" :fmt :raha}])
     (concat
       ;; Laji yhteensä -rivi ensin
       [{:lihavoi? true
         :korosta-hennosti? true
         :rivi (rivi laji-nimi laji-summa)}]
       ;; Tyyppirivit (jos useita)
       (when (> (count tyypit) 1)
         (mapv
           (fn [tyyppi]
             (let [tyyppi-koodi (:sanktiotyyppi_koodi tyyppi)
                   summa (or (get sanktio-data-map [laji-koodi tyyppi-koodi]) 0)]
               {:korosta-harmaa? (zero? summa)
                :rivi (rivi (:sanktiotyyppi_nimi tyyppi) summa)}))
           tyypit)))]))

(defn- koosta-sanktio-taulukot
  "Ryhmittelee lajit tietokannan mukaisesti, muodostaa erillisen taulukon kutakin lajia vasten.
   Arvonalennukset (arvonvahennyssanktio) erotetaan omaksi taulukokseen."
  [sanktiolajit sanktio-data-map]
  (let [;;Ryhmitellään lajit koodin mukaan
        ryhmitelty (group-by :sanktiolaji_koodi sanktiolajit)
        ;; Järjestetään jokainen ryhmä jarjestys-kentän mukaan
        jarjestetty (->> ryhmitelty
                      (sort-by (fn [[_ lajit]] (or (:sanktiolaji_jarjestys (first lajit)) 99)))
                        (map val)
                        (mapv #(sort-by :sanktiolaji_jarjestys %)))]
      ;; Muodosta jokaiselle ryhmälle oma taulukko
      (mapv #(muodosta-sanktio-taulukko % sanktio-data-map) jarjestetty))))

(defn- muodosta-bonus-taulukko
  "Muodostaa bonus-taulukon annetuista bonusriveistä."
  [bonus-lajit bonus-data-map]
  (let [bonus-rivit (mapv
                      (fn [laji]
                        (let [summa (or (get bonus-data-map (:bonuslaji_koodi laji)) 0)
                              nolla-summa? (zero? summa)]
                          {:korosta-harmaa? nolla-summa?
                           :rivi (rivi (:bonuslaji_nimi laji) summa)}))
                      bonus-lajit)
        bonukset-yhteensa (reduce + 0 (vals bonus-data-map))]
    [:taulukko {:otsikko "Bonukset"
                :sheet-nimi "Bonukset"
                :viimeinen-rivi-yhteenveto? true
                :tyhja (when (empty? bonus-lajit) "Ei bonuslajeja profiilissa.")}
     [{:leveys 12 :otsikko "Bonuslaji"}
      {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
     (into [] (concat bonus-rivit
                [{:lihavoi? true
                  :korosta-hennosti? true
                  :rivi (rivi "Bonukset yhteensä" bonukset-yhteensa)}]))]))

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

        ;; Bonus-data-map
        bonus-data-map (reduce
                         (fn [m b]
                           (update m (:bonuslaji_koodi b) (fnil + 0) (or (:summa b) 0)))
                         {}
                         bonukset)

        ;; Bonus-lajit järjestyksessä
        bonus-lajit-jarjestyksessa (sort-by :bonuslaji_jarjestys bonuslajit)

        ;; Arvonvähennysrivit
        arvonvahennykset-lajeittain (group-by :sanktiolaji_nimi arvonvahennykset)
        arvonvahennys-rivit (mapv (fn [[laji-nimi laji-arvonvahennykset]]
                                    (let [summa (reduce + 0 (map #(or (:summa %) 0) laji-arvonvahennykset))]
                                      (rivi (or laji-nimi "Arvonvähennys") summa)))
                              arvonvahennykset-lajeittain)
        arvonvahennys-yhteensa-rivi [{:lihavoi? true
                                      :korosta-hennosti? true
                                      :rivi (rivi "Yhteensä" arvonvahennykset-summa)}]]

    (into [:raportti {:nimi urakan-nimi :orientaatio :landscape}
           [:otsikko otsikko]
           [:jakaja nil]
           ;; Yhteenveto-laatikko
           [:display-flex
            [:sininen-laatikko {:otsikko "Yhteenveto"}
             yhteenveto-data]]]
      (concat
        ;; Sanktiotaulukot -- yksi taulukko per sanktiolaji, levitetaan concat:iin
        (when (seq sanktiolajit)
          (koosta-sanktio-taulukot sanktiolajit sanktio-data-map))
        ;; Bonus-taulukko (erillinen)
        [(muodosta-bonus-taulukko bonus-lajit-jarjestyksessa bonus-data-map)]
        ;; Arvonvahennystaulukko
        [[:taulukko {:otsikko "Arvonvähennykset"
                     :sheet-nimi "Arvonvähennykset"
                     :viimeinen-rivi-yhteenveto? true
                     :tyhja (when (empty? arvonvahennykset) "Ei arvonvähennyksiä.")}
          [{:leveys 12 :otsikko "Arvonvähennyslaji"}
           {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
          (into [] (concat arvonvahennys-rivit arvonvahennys-yhteensa-rivi))]]))))

(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiili-driven SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset."
  [db _user {:keys [urakka-id alkupvm loppupvm hoitovuosi]}]
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
