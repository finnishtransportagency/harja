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

(declare hae-sanktiot hae-bonukset hae-urakkataso-sanktiot hae-urakkataso-bonukset)

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
   suoraan tietokannasta (sanktio_profiili_*, bonus_profiili_*-taulut)."
  [urakan-nimi alkupvm loppupvm sanktiot bonukset]
  (let [otsikko (str "Sanktiot, bonukset ja arvovähennykset — " urakan-nimi
                  " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))

        ;; Lasketaan yhteenvetoarvot suoraan profiili-driven tuloksista
        sanktiot-yhteensa (reduce + 0 (map #(or (:summa %) 0) sanktiot))
        bonukset-yhteensa (reduce + 0 (map #(or (:summa %) 0) bonukset))
        muistutusten-maara (count (filterv #(= "muistutus" (:sanktiolaji_koodi %)) sanktiot))
        vastuuhenkilon-vaihto-summa (reduce + 0
                                     (map #(or (:summa %) 0)
                                       (filterv #(= "vastuuhenkilon-vaihto" (:sanktiotyyppi_koodi %)) sanktiot)))
        arvovahennykset-summa (reduce + 0
                                (map #(or (:summa %) 0)
                                  (filterv :suorasanktio sanktiot)))

        yhteenveto-data [{:avain "Sanktiot yhteensä" :arvo sanktiot-yhteensa :fmt :raha}
                         {:avain "Bonukset yhteensä" :arvo bonukset-yhteensa :fmt :raha}
                         {:avain "Kirjalliset muistutukset" :arvo (str muistutusten-maara " kpl")}
                         {:avain "Vastuuhenkilön vaihto" :arvo vastuuhenkilon-vaihto-summa :fmt :raha}
                         {:avain "Arvovähennykset" :arvo arvovahennykset-summa :fmt :raha}
                         {:avain "Yhteensä" :arvo (+ sanktiot-yhteensa bonukset-yhteensa) :fmt :raha :lihavoi? true}]

        ;; Sanktiorivit lajeittain (profiili-driven, dynaaminen)
        sanktiot-lajeittain (group-by :sanktiolaji_nimi sanktiot)
        sanktio-rivit (mapcat
                        (fn [[laji-nimi laji-sanktiot]]
                          (let [tyypit (group-by :sanktiotyyppi_nimi laji-sanktiot)]
                            (concat
                              [{:korosta-harmaa? true
                                :lihavoi? true
                                :rivi (rivi laji-nimi "" "")}]
                              (mapv (fn [[tyyppi-nimi tyyppi-sanktiot]]
                                      (let [summa (reduce + 0 (map #(or (:summa %) 0) tyyppi-sanktiot))]
                                        (rivi (or tyyppi-nimi laji-nimi) summa)))
                                tyypit))))
                        sanktiot-lajeittain)
        sanktio-yhteensa-rivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi (rivi "Yhteensä" sanktiot-yhteensa)}]

        ;; Bonusrivit lajeittain (profiili-driven, dynaaminen)
        bonukset-lajeittain (group-by :bonuslaji_nimi bonukset)
        bonus-rivit (mapv (fn [[laji-nimi laji-bonukset]]
                            (let [summa (reduce + 0 (map #(or (:summa %) 0) laji-bonukset))]
                              (rivi (or laji-nimi "-") summa)))
                          bonukset-lajeittain)
        bonus-yhteensa-rivi [{:lihavoi? true
                              :korosta-hennosti? true
                              :rivi (rivi "Yhteensä" bonukset-yhteensa)}]]

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
                 :tyhja (when (empty? sanktiot) "Ei sanktioita.")}
      [{:leveys 12 :otsikko "Sanktiolaji"}
       {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
      (into [] (concat sanktio-rivit sanktio-yhteensa-rivi))]

     ;; Bonustaulukko
     [:taulukko {:otsikko "Bonukset"
                 :sheet-nimi "Bonukset"
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja (when (empty? bonukset) "Ei bonuksia.")}
      [{:leveys 12 :otsikko "Bonuslaji"}
       {:leveys 15 :otsikko "Summa (€)" :fmt :raha}]
      (into [] (concat bonus-rivit bonus-yhteensa-rivi))]]))

(defn suorita
  "Raportin suoritin urakkataso-sanktioraportille (MHU25+).
   Käyttää profiili-driven SQL-kyselyitä: hae-urakkataso-sanktiot ja hae-urakkataso-bonukset."
  [db _user {:keys [urakka-id alkupvm loppupvm hoitovuosi] :as parametrit}]
  (let [urakan-tiedot (when urakka-id
                        (first (urakat-kyselyt/hae-urakka db urakka-id)))
        urakan-nimi (or (:nimi urakan-tiedot) "")
        hoitovuosi (or hoitovuosi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) alkupvm))
        sanktiot (hae-urakkataso-sanktiot db {:urakka urakka-id
                                              :alku alkupvm
                                              :loppu loppupvm
                                              :hoitovuosi hoitovuosi})
        bonukset (hae-urakkataso-bonukset db {:urakka urakka-id
                                              :alku alkupvm
                                              :loppu loppupvm
                                              :hoitovuosi hoitovuosi})]
    (koosta-urakkataso-runko urakan-nimi alkupvm loppupvm sanktiot bonukset)))