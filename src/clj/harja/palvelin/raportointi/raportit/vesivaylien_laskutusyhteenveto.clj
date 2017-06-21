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
                                                                      :vaylatyyppi "muu"}))}})

(defn- yhteensa-rivi
  ([otsikko tiedot] (yhteensa-rivi otsikko tiedot nil))
  ([otsikko tiedot vaylatyyppi]
   [otsikko
    ""
    (reduce + 0
            (concat
              (map :summa (if vaylatyyppi
                            (filter #((:vaylatyyppi %) vaylatyyppi)
                                    (:yksikkohintaiset tiedot))
                            (:yksikkohintaiset tiedot)))
              (if vaylatyyppi
                [(:toteutunut-maara ((keyword vaylatyyppi) (:kokonaishintaiset tiedot)))]
                (map :toteutunut-maara (vals (:kokonaishintaiset tiedot))))))
    ""]))

(defn- kaikki-yhteensa [tiedot]
  [(yhteensa-rivi "Kauppamerenkulku" tiedot "kauppamerenkulku")
   (yhteensa-rivi "Muu vesiliikenne" tiedot "muu")
   (yhteensa-rivi "Yhteensä" tiedot)])

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        kauppamerenkulku-kok-hint (kok-hint-hinnoittelurivit
                                    (:kauppamerenkulku (:kokonaishintaiset raportin-tiedot)))
        kauppamerenkulku-yks-hint (yks-hint-hinnoittelurivit
                                    (:yksikkohintaiset raportin-tiedot)
                                    "kauppamerenkulku")
        kauppamerenkulku-yht (yhteensa-rivi "Yhteensä" raportin-tiedot "kauppamerenkulku")
        muu-vesi-kok-hint (kok-hint-hinnoittelurivit
                            (:muu (:kokonaishintaiset raportin-tiedot)))
        muu-vesi-yks-hint (yks-hint-hinnoittelurivit
                            (:yksikkohintaiset raportin-tiedot)
                            "muu")
        muu-vesi-yht (yhteensa-rivi "Yhteensä" raportin-tiedot "muu")
        kaikki-yht-rivit (kaikki-yhteensa raportin-tiedot)
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}

     [:taulukko {:otsikko "Kauppamerenkulku"
                 :tyhja (if (empty? kauppamerenkulku-kok-hint) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      hinnoittelusarakkeet
      (concat kauppamerenkulku-kok-hint
              kauppamerenkulku-yks-hint
              [kauppamerenkulku-yht])]

     [:taulukko {:otsikko "Muu vesiliikenne"
                 :tyhja (if (empty? kauppamerenkulku-kok-hint) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      hinnoittelusarakkeet
      (concat muu-vesi-kok-hint
              muu-vesi-yks-hint
              [muu-vesi-yht])]

     [:taulukko {:otsikko "Yhteenveto"
                 :tyhja (if (empty? kauppamerenkulku-kok-hint) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      hinnoittelusarakkeet
      kaikki-yht-rivit]]))
