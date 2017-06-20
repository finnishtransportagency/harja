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

(defn- kok-hint-hinnoittelurivit [otsikko tiedot vaylatyyppi]
  ;; TODO mites väylätyyppi saadaan tähän!?
  [{:otsikko otsikko}
   ["Kokonaishintaiset toimenpiteet"
    (:suunniteltu-maara (first tiedot))
    (:toteutunut-maara (first tiedot))
    (- (:suunniteltu-maara (first tiedot))
       (:toteutunut-maara (first tiedot)))]])

(defn- yks-hint-hinnoittelurivi [tiedot]
  [(:hinnoittelu tiedot)
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   (:summa tiedot)
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]])

(defn- yks-hint-hinnoittelurivit [otsikko tiedot vaylatyyppi]
  (apply concat
         [[{:otsikko otsikko}]
          (mapv yks-hint-hinnoittelurivi (filter #((:vaylatyyppi %) vaylatyyppi) tiedot))]))

(defn- hinnoittelutiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  {:yksikkohintaiset (into []
                           (map #(konv/array->set % :vaylatyyppi))
                           (hae-yksikkohintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm}))
   :kokonaishintaiset (vec (hae-kokonaishintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                   :alkupvm alkupvm
                                                                   :loppupvm loppupvm}))})

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        kauppamerenkulku-kok-hint (kok-hint-hinnoittelurivit
                                    "Kauppamerenkulku: kokonaishintaiset"
                                    (:kokonaishintaiset raportin-tiedot)
                                    "kauppamerenkulku")
        kauppamerenkulku-yks-hint (yks-hint-hinnoittelurivit
                                    "Kauppamerenkulku: yksikköhintaiset"
                                    (:yksikkohintaiset raportin-tiedot)
                                    "kauppamerenkulku")
        muu-vesi-kok-hint (kok-hint-hinnoittelurivit
                            "Muu vesiliikenne: kokonaishintaiset"
                            (:kokonaishintaiset raportin-tiedot)
                            "muu")
        muu-vesi-yks-hint (yks-hint-hinnoittelurivit
                            "Muu vesiliikenne: yksikköhintaiset"
                            (:yksikkohintaiset raportin-tiedot)
                            "muu")
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}

     [:taulukko {:otsikko "Kauppamerenkulku"
                 :tyhja (if (empty? kauppamerenkulku-kok-hint) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      hinnoittelusarakkeet
      (concat kauppamerenkulku-kok-hint
              kauppamerenkulku-yks-hint)]

     [:taulukko {:otsikko "Muu vesiliikenne"
                 :tyhja (if (empty? kauppamerenkulku-kok-hint) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      hinnoittelusarakkeet
      (concat muu-vesi-kok-hint
              muu-vesi-yks-hint)]]))
