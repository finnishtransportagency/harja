(ns harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]))

(defqueries "harja/palvelin/raportointi/raportit/vesivaylien_laskutusyhteenveto.sql")

(def hinnoittelusarakkeet
  [{:leveys 3 :otsikko "Hinnoit\u00ADtelu"}
   {:leveys 1 :otsikko "Suunni\u00ADtellut" :fmt :raha}
   {:leveys 1 :otsikko "Toteutunut" :fmt :raha}
   {:leveys 1 :otsikko "Jäljellä" :fmt :raha}])

(def yhteenveto-sarakkeet
  [{:leveys 1 :otsikko "Kustan\u00ADnus\u00ADlaji"}
   {:leveys 1 :otsikko "Toteutunut" :fmt :raha}])

(defn- kok-hint-hinnoittelurivit [tiedot]
  [["Kokonaishintaiset toimenpiteet"
    (:suunniteltu-maara tiedot)
    (:toteutunut-maara tiedot)
    (- (:suunniteltu-maara tiedot)
       (:toteutunut-maara tiedot))]])

(defn- yks-hint-hinnoittelurivi [tiedot]
  [(:hinnoittelu tiedot)
   ""
   (:summa tiedot)
   ""])

(defn- yks-hint-hinnoittelurivit [tiedot vaylatyyppi]
  (mapv yks-hint-hinnoittelurivi (filter #((:vaylatyyppi %) vaylatyyppi) tiedot)))

(defn- hinnoittelutiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  {:yksikkohintaiset (into []
                           (map #(konv/array->set % :vaylatyyppi))
                           (hae-yksikkohintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm}))
   :kokonaishintaiset {:kauppamerenkulku
                       (first (hae-kokonaishintaiset-toimenpiteet db
                                                                  {:urakkaid urakka-id
                                                                   :alkupvm alkupvm
                                                                   :loppupvm loppupvm
                                                                   :vaylatyyppi "kauppamerenkulku"}))
                       :muu
                       (first (hae-kokonaishintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm
                                                                      :vaylatyyppi "muu"}))}
   :sanktiot (:summa (first (hae-sanktiot db {:urakkaid urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})))
   :erilliskustannukset (:summa (first (hae-erilliskustannukset db {:urakkaid urakka-id
                                                                    :alkupvm alkupvm
                                                                    :loppupvm loppupvm})))})



(defn- vaylatyypin-summa [tiedot vaylatyyppi]
  (reduce + 0
          (concat
            (map :summa (filter #((:vaylatyyppi %) vaylatyyppi) (:yksikkohintaiset tiedot)))
            [(:toteutunut-maara ((keyword vaylatyyppi) (:kokonaishintaiset tiedot)))])))

(defn- hinnoittelu-yhteensa-rivi [summa]
  ["Yhteensä" "" summa ""])

(defn- kaikki-yhteensa-rivi [otsikko summa]
  [otsikko summa])

(defn- kaikki-yhteensa-rivit [tiedot]
  [(kaikki-yhteensa-rivi "Toimenpiteet" (+ (vaylatyypin-summa tiedot "kauppamerenkulku")
                                           (vaylatyypin-summa tiedot "muu")))
   (kaikki-yhteensa-rivi "Sanktiot" (:sanktiot tiedot))
   (kaikki-yhteensa-rivi "Erilliskustannukset" (:erilliskustannukset tiedot))
   (kaikki-yhteensa-rivi "Kaikki yhteensä"
                         (reduce + 0
                                 (concat
                                   (map :summa (:yksikkohintaiset tiedot))
                                   [(:sanktiot tiedot)]
                                   (map :toteutunut-maara (vals (:kokonaishintaiset tiedot))))))])

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        raportin-nimi "Laskutusyhteenveto"]

    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}

     (let [kauppamerenkulku-kok-hint-rivit (kok-hint-hinnoittelurivit
                                             (:kauppamerenkulku (:kokonaishintaiset raportin-tiedot)))
           kauppamerenkulku-yks-hint-rivit (yks-hint-hinnoittelurivit
                                             (:yksikkohintaiset raportin-tiedot)
                                             "kauppamerenkulku")
           kauppamerenkulku-yht-rivit (hinnoittelu-yhteensa-rivi
                                        (vaylatyypin-summa raportin-tiedot "kauppamerenkulku"))]
       [:taulukko {:otsikko "Kauppamerenkulku"
                   :tyhja (if (empty? kauppamerenkulku-kok-hint-rivit) "Ei raportoitavaa.")
                   :sheet-nimi raportin-nimi
                   :viimeinen-rivi-yhteenveto? true}
        hinnoittelusarakkeet
        (concat kauppamerenkulku-kok-hint-rivit
                kauppamerenkulku-yks-hint-rivit
                [kauppamerenkulku-yht-rivit])])

     (let [muu-vesi-kok-hint-rivit (kok-hint-hinnoittelurivit
                                     (:muu (:kokonaishintaiset raportin-tiedot)))
           muu-vesi-yks-hint-rivit (yks-hint-hinnoittelurivit
                                     (:yksikkohintaiset raportin-tiedot)
                                     "muu")
           muu-vesi-yht-rivit (hinnoittelu-yhteensa-rivi
                                (vaylatyypin-summa raportin-tiedot "muu"))]
       [:taulukko {:otsikko "Muu vesiliikenne"
                   :tyhja (if (empty? muu-vesi-yht-rivit) "Ei raportoitavaa.")
                   :sheet-nimi raportin-nimi
                   :viimeinen-rivi-yhteenveto? true}
        hinnoittelusarakkeet
        (concat muu-vesi-kok-hint-rivit
                muu-vesi-yks-hint-rivit
                [muu-vesi-yht-rivit])])

     (let [kaikki-yht-rivit (kaikki-yhteensa-rivit raportin-tiedot)]
       [:taulukko {:otsikko "Yhteenveto"
                   :tyhja (if (empty? kaikki-yht-rivit) "Ei raportoitavaa.")
                   :sheet-nimi raportin-nimi
                   :viimeinen-rivi-yhteenveto? true}
        yhteenveto-sarakkeet
        kaikki-yht-rivit])]))
