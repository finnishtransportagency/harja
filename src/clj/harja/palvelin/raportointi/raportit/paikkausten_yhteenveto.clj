(ns harja.palvelin.raportointi.raportit.paikkausten-yhteenveto
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]))

(defqueries "harja/palvelin/raportointi/raportit/paikkausten_yhteenveto.sql")
(declare hae-kustannukset-tyomenetelmittain hae-maarat-tyomenetelmittain hae-kasin-lisatyt-paikkauskustannukset
  hae-reikapaikkauskustannukset-tyomenetelmittain)

(defn tyomenetelma-rivi-xf
  "Parsitaan työmenetelmädatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? (if (:yhteenveto rivi) true nil)
   :korosta-hennosti? (:yhteenveto rivi)
   :rivi
   (into []
     (concat
       [(:nimi rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:suunniteltu-hinta rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut-hinta rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn tyomenetelma-maara-rivi-xf
  "Parsitaan tehtävädatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? nil
   :rivi
   (into []
     (concat
       [(:nimi rivi)]
       [(:yksikko rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:suunniteltu-maara rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut-maara rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn reikapaikkaus-rivi-xf
  "Parsitaan tehtävädatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? (if (:yhteenveto rivi) true nil)
   :korosta-hennosti? (:yhteenveto rivi)
   :rivi
   (into []
     (concat
       [(:nimi rivi)]
       [(:yksikko rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut-maara rivi)
                                      :yksikko nil
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut-hinta rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn muut-kustannukset-rivi-xf
  "Parsitaan raportille sopiva rivi."
  [rivi]
  {:lihavoi? (if (:yhteenveto rivi) true nil)
   :korosta-hennosti? (:yhteenveto rivi)
   :rivi
   (into []
     (concat
       [(:nimi rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut-hinta rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn yhteenveto-rivi-xf
  "Parsitaan yhteenvetodatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? true
   :rivi
   (into []
     (concat
       [[:arvo-ja-yksikko-korostettu {:arvo (:tilatut rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutuneet rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:muut rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]))})

(defn pk-rivi-xf
  "Parsitaan yhteenvetodatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? true
   :rivi
   (into []
     (concat
       [[:arvo-ja-yksikko-korostettu {:arvo (:pk1 rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:pk2 rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:pk3 rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:pk-puuttuu rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]))})

(defn- laske-sakko-bonus-arvo [avain data]
  (reduce + (mapv (fn [rivi]
                    (if (= (:laji rivi) avain)
                      (or (:summa rivi) 0)
                      0))
              data)))

(defn koosta-muut-kustannukset [db user urakka-id hoitokauden-alkuvuosi alkupvm loppupvm]
  (let [sankiot-ja-bonukset (laadunseuranta-palvelu/hae-urakan-sanktiot-ja-bonukset db user
                              {:urakka-id urakka-id
                               :alku alkupvm
                               :loppu loppupvm
                               :vain-yllapitokohteettomat? false
                               :hae-sanktiot? true
                               :hae-bonukset? true})
        bonukset (bigdec (laske-sakko-bonus-arvo :yllapidon_bonus sankiot-ja-bonukset))
        sanktiot (bigdec (laske-sakko-bonus-arvo :yllapidon_sakko sankiot-ja-bonukset))

        ;; Paikkauskustannukset
        paikkauskustannukset (hae-kasin-lisatyt-paikkauskustannukset db {:urakkaid urakka-id
                                                                         :vuosi hoitokauden-alkuvuosi})
        sakot-ja-bonukset [{:nimi "Bonukset" :toteutunut-hinta bonukset}
                           {:nimi "Sanktiot" :toteutunut-hinta sanktiot}]
        muut-kustannukset (into [] (remove nil? (concat sakot-ja-bonukset
                                                  (when-not (empty? paikkauskustannukset) paikkauskustannukset))))
        yht-muut-kustannukset {:nimi "Yhteensä"
                               :toteutunut-hinta (apply + (map :toteutunut-hinta muut-kustannukset))
                               :yhteenveto true}
        muut-kustannukset (conj muut-kustannukset yht-muut-kustannukset)]
    muut-kustannukset))

(defn koosta-reikapaikkauskustannukset [db parametrit]
  (let [reikapaikkauskustannukset (into [] (hae-reikapaikkauskustannukset-tyomenetelmittain db parametrit))
        toteutuneet-reikapaikkauskustannukset (apply + (map (fn [rivi]
                                                              (if (and rivi (:toteutunut-hinta rivi))
                                                                (:toteutunut-hinta rivi)
                                                                0))
                                                         reikapaikkauskustannukset))
        reikapaikkauskustannukset-yht {:nimi "Yhteensä"
                                       :toteutunut-hinta toteutuneet-reikapaikkauskustannukset
                                       :yhteenveto true}]
    (conj reikapaikkauskustannukset reikapaikkauskustannukset-yht)))

(defn yhteiset-tiedot [db user urakka-id vuosi mpu?]
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        alkupvm (pvm/luo-pvm vuosi 1 1)
        loppupvm (pvm/luo-pvm vuosi 12 31)
        hoitokauden-alkuvuosi vuosi
        raportin-nimi "Paikkausten yhteenveto"
        parametrit {:urakkaid urakka-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm}

        tyomenetelmakustannukset (into [] (hae-kustannukset-tyomenetelmittain db parametrit))
        tilatut-kustannukset (apply + (map (fn [rivi]
                                             (if (and rivi (:suunniteltu-hinta rivi))
                                               (:suunniteltu-hinta rivi)
                                               0))
                                        tyomenetelmakustannukset))
        toteutuneet-kustannukset (apply + (map (fn [rivi]
                                                 (if (and rivi (:toteutunut-hinta rivi))
                                                   (:toteutunut-hinta rivi)
                                                   0))
                                            tyomenetelmakustannukset))
        yhteenvetorivi {:nimi "Yhteensä"
                        :suunniteltu-hinta tilatut-kustannukset
                        :toteutunut-hinta toteutuneet-kustannukset
                        :yhteenveto true}
        tyomenetelmakustannukset (conj tyomenetelmakustannukset yhteenvetorivi)

        tyomenetelmamaarat (hae-maarat-tyomenetelmittain db parametrit)
        reikapaikkauskustannukset (when mpu? (koosta-reikapaikkauskustannukset db parametrit))
        muut-kustannukset (koosta-muut-kustannukset db user urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)

        muut-kuin-paikkaus-kustannukset (apply + (map :toteutunut-hinta muut-kustannukset))

        ;; PK-luokkia ei ole olemassa paikkauskohteilla
        ; pk1-kustannukset 8M
        ; pk2-kustannukset 9M
        ; pk3-kustannukset 10M
        ; pk-puuttuu-kustannukset 11M

        raportti
        [:raportti {:nimi raportin-nimi
                    :otsikon-koko :keskikoko
                    :tiedot nil
                    :raportin-yleiset-tiedot {:raportin-nimi "Paikkausten yhteeveto"
                                              :urakka (:nimi urakka)
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm}}
         [:teksti (str (:nimi urakka) " " (pvm/vuosi (:alkupvm urakka)) "-" (pvm/vuosi (:loppupvm urakka)))]
         [:teksti (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]

         ;; Yhteenveto -taulukko suunnitellut ja toteutuneet kustannukset
         [:taulukko {:otsikko "Yhteenveto"
                     :oikealle-tasattavat-kentat #{}
                     :sheet-nimi "Yhteenveto suunnitellut ja toteutuneet"}
          [{:leveys 1 :otsikko "Tilatut paikkauskustannukset"}
           {:leveys 1 :otsikko "Toteutuneet paikkauskustannukset"}
           {:leveys 1 :otsikko "Muun, kuin paikkaustyön osuus"}]
          [(yhteenveto-rivi-xf {:tilatut tilatut-kustannukset
                                :toteutuneet toteutuneet-kustannukset
                                :muut muut-kuin-paikkaus-kustannukset})]]

         ;; Toteutuneet paikkauskustannukset PK-luokittain - odottamaan pk-luokkien generointia
         #_[:taulukko {:otsikko "Toteutuneet paikkauskustannukset PK-luokittain"
                       :oikealle-tasattavat-kentat #{}
                       :sheet-nimi "Toteutuneet paikkauskustannukset PK-luokittain"}
            [{:leveys 1 :otsikko "PK1"}
             {:leveys 1 :otsikko "PK2"}
             {:leveys 1 :otsikko "PK3"}
             {:leveys 1 :otsikko "PK-luokka puuttuu"}]
            [(pk-rivi-xf {:pk1 pk1-kustannukset
                          :pk2 pk2-kustannukset
                          :pk3 pk3-kustannukset
                          :pk-puuttuu pk-puuttuu-kustannukset})]]

         ;; Kustannukset tehtäväryhmittäin
         [:taulukko {:otsikko "Kustannukset työmenetelmittäin"
                     :tyhja "Ei työmenetelmiä."
                     :oikealle-tasattavat-kentat #{1 2}
                     :sheet-nimi "Kustannukset työmenetelmittäin"}
          [{:leveys 6 :otsikko "Työmenetelmä"}
           {:leveys 1 :otsikko "Tilattu EUR" :fmt :raha}
           {:leveys 1 :otsikko "Toteutunut EUR" :fmt :raha}]
          (map tyomenetelma-rivi-xf tyomenetelmakustannukset)]

         ;; Määrät tehtävittäin
         [:taulukko {:otsikko "Määrät työmenetelmittäin"
                     :tyhja "Ei tehtäviä."
                     :sheet-nimi "Määrät tehtävittäin"
                     :oikealle-tasattavat-kentat #{2 3}}
          [{:leveys 7 :otsikko "Tehtävät"}
           {:leveys 1 :otsikko "Yksikkö"}
           {:leveys 2 :otsikko "Suunniteltu määrä" :fmt :raha}
           {:leveys 2 :otsikko "Toteutunut määrä" :fmt :raha}]
          (map tyomenetelma-maara-rivi-xf tyomenetelmamaarat)]

         ;; Reikäpaikkausten Kustannukset työmenetelmittäin - Vain MPU raportilla
         (when mpu?
           [:taulukko {:otsikko "Reikäpaikkausten kustannukset"
                       :tyhja "Ei reikäpaikkauksia."
                       :oikealle-tasattavat-kentat #{2 3}
                       :sheet-nimi "Reikäpaikkausten kustannukset"}
            [{:leveys 6 :otsikko "Työmenetelmä"}
             {:leveys 1 :otsikko "Yksikkö"}
             {:leveys 1 :otsikko "Toteutunut määrä"}
             {:leveys 1 :otsikko "Toteutunut (EUR)" :fmt :raha}]
            (map reikapaikkaus-rivi-xf reikapaikkauskustannukset)])

         ;; Muut kustannukset - eli käsin lisätyt kustannukset
         [:taulukko {:otsikko "Muut kustannukset"
                     :tyhja "Ei muita kustannuksia."
                     :oikealle-tasattavat-kentat #{2}
                     :sheet-nimi "Muut kustannukset"}
          [{:leveys 6 :otsikko ""}
           {:leveys 1 :otsikko "Toteutunut (EUR)" :fmt :raha}]
          (map muut-kustannukset-rivi-xf muut-kustannukset)]]]

    ;; Palautetaan raportti
    raportti))

(defn suorita-ppu [db user {:keys [urakka-id vuosi] :as parametrit}]
  (log/debug "Paikkausten yhteenvetoraportti PPU :: suorita urakka_id=" urakka-id " vuosi=" vuosi " parametrit=" parametrit)
  (yhteiset-tiedot db user urakka-id vuosi false))


(defn suorita-mpu [db user {:keys [urakka-id vuosi] :as parametrit}]
  (log/debug "Paikkausten yhteenvetoraportti MPU :: suorita urakka_id=" urakka-id " vuosi=" vuosi " parametrit=" parametrit)
  (yhteiset-tiedot db user urakka-id vuosi true))
