(ns harja.palvelin.raportointi.raportit.soratietarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defn muodosta-raportin-rivit [tarkastukset]
  "Muodostaa annetuista tarkastukset-riveistä raportilla näytettävät rivit eli yhdistää rivit niin,
  että sama tienumero ja sama päiväämärä esiintyy aina yhdellä rivillä.
  Yhdistetyissä rivissä lasketaan yhteen eri tarkastuksista saadut laatuarvot (1-5)."
  (let [ryhmat (group-by
                 (fn [rivi]
                   [(:aika rivi) (get-in rivi [:tr :numero])])
                 tarkastukset)]
    (mapv (fn [ryhma]
            (let [jasenet (get ryhmat ryhma)
                  laske-laatuarvon-summa (fn [rivit arvo]
                                           (reduce
                                             (fn [nykysumma seuraava-rivi]
                                               (let [laatuarvot ((juxt :polyavyys :tasaisuus :kiinteys) seuraava-rivi)]
                                                 (+ nykysumma (count (filter #(= % arvo) laatuarvot)))))
                                             0
                                             rivit))
                  laatuarvot (mapv (fn [arvo]
                                     (laske-laatuarvon-summa jasenet arvo))
                                   (range 1 6))
                  ]
              (-> (first jasenet)
                  (assoc :laatuarvo-1-summa (nth laatuarvot 0))
                  (assoc :laatuarvo-2-summa (nth laatuarvot 1))
                  (assoc :laatuarvo-3-summa (nth laatuarvot 2))
                  (assoc :laatuarvo-4-summa (nth laatuarvot 3))
                  (assoc :laatuarvo-5-summa (nth laatuarvot 4))
                  (assoc :laatuarvot-yhteensa (reduce + laatuarvot))
                  (assoc :laatuarvo-1+2-summa (reduce + [(first laatuarvot)
                                                         (second laatuarvot)])))))
          (keys ryhmat))))

(defn hae-tarkastukset-urakalle [db {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-urakan-soratietarkastukset-raportille db
                                                                       urakka-id
                                                                       alkupvm
                                                                       loppupvm
                                                                       (not (nil? tienumero))
                                                                       tienumero))

(defn hae-tarkastukset-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-hallintayksikon-soratietarkastukset-raportille db
                                                                                hallintayksikko-id
                                                                                alkupvm
                                                                                loppupvm
                                                                                (not (nil? tienumero))
                                                                                tienumero))

(defn hae-tarkastukset-koko-maalle [db {:keys [alkupvm loppupvm tienumero]}]
  (tarkastukset-q/hae-koko-maan-soratietarkastukset-raportille db
                                                                          alkupvm
                                                                          loppupvm
                                                                          (not (nil? tienumero))
                                                                          tienumero))

(defn hae-tarkastukset [db {:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm tienumero]}]
  (case konteksti
    :urakka
    (hae-tarkastukset-urakalle db
                               {:urakka-id urakka-id
                                :alkupvm   alkupvm
                                :loppupvm  loppupvm
                                :tienumero tienumero})
    :hallintayksikko
    (hae-tarkastukset-hallintayksikolle db
                                        {:hallintayksikko-id hallintayksikko-id
                                         :alkupvm            alkupvm
                                         :loppupvm           loppupvm
                                         :tienumero          tienumero})
    :koko-maa
    (hae-tarkastukset-koko-maalle db
                                  {:alkupvm   alkupvm
                                   :loppupvm  loppupvm
                                   :tienumero tienumero})))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        tarkastukset (map konv/alaviiva->rakenne
                               (hae-tarkastukset db {:konteksti          konteksti
                                                     :urakka-id          urakka-id
                                                     :hallintayksikko-id hallintayksikko-id
                                                     :alkupvm            alkupvm
                                                     :loppupvm           loppupvm
                                                     :tienumero          tienumero}))
        naytettavat-rivit (muodosta-raportin-rivit tarkastukset)
        raportin-nimi "Tiestötarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")}
      [{:leveys 10 :otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
       {:leveys 5 :otsikko "Tie"}
       {:leveys 6 :otsikko "Aosa"}
       {:leveys 6 :otsikko "Aet"}
       {:leveys 6 :otsikko "Losa"}
       {:leveys 6 :otsikko "Let"}
       {:leveys 6 :otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
       {:leveys 6 :otsikko "1"}
       {:leveys 6 :otsikko "2"}
       {:leveys 6 :otsikko "3"}
       {:leveys 6 :otsikko "4"}
       {:leveys 6 :otsikko "5"}
       {:leveys 6 :otsikko "Yht"}
       {:leveys 6 :otsikko "1+2"}
       {:leveys 6 :otsikko "Laa\u00ADtu"}]
      (yleinen/ryhmittele-tulokset-raportin-taulukolle
        naytettavat-rivit
        :urakka
        (fn [rivi]
          [(pvm/pvm (:aika rivi))
           (get-in rivi [:tr :numero])
           (get-in rivi [:tr :alkuosa])
           (get-in rivi [:tr :alkuetaisyys])
           (get-in rivi [:tr :loppuosa])
           (get-in rivi [:tr :loppyetaisyys])
           (:hoitoluokka rivi)
           (:laatuarvo-1-summa rivi)
           (:laatuarvo-2-summa rivi)
           (:laatuarvo-3-summa rivi)
           (:laatuarvo-4-summa rivi)
           (:laatuarvo-5-summa rivi)
           (:laatuarvot-yhteensa rivi)
           (:laatuarvo-1+2-summa rivi)
           (:laatu rivi)]))]]))