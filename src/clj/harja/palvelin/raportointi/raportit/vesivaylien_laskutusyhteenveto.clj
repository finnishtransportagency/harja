(ns harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
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
   {:leveys 1 :otsikko "Tilaus\u00ADvaltuus" :fmt :raha}
   {:leveys 1 :otsikko "Suunni\u00ADtellut" :fmt :raha}
   {:leveys 1 :otsikko "Toteutunut" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä (S+T)" :fmt :raha}
   {:leveys 1 :otsikko "Jäljellä" :fmt :raha}])

(defn- kok-hint-hinnoittelurivi [tiedot]
  [(:hinnoittelu tiedot)
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   (:summa tiedot)
   (:summa tiedot)
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]])

(defn- yks-hint-hinnoittelurivi [tiedot]
  [(str (to/reimari-toimenpidetyyppi-fmt
          (get to/reimari-toimenpidetyypit (:koodi tiedot)))
        " ("
        (:maara tiedot)
        "kpl)")
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "?" :tyyli :virhe}]])

(defn- hinnoittelurivit [tiedot]
  (apply concat
         [[{:otsikko "Kokonaishintaiset: kauppamerenkulku"}]
          (mapv yks-hint-hinnoittelurivi (filter #(= (:vaylatyyppi %) "kauppamerenkulku")
                                                 (:kokonaishintaiset tiedot)))
          [{:otsikko "Kokonaishintaiset: muut"}]
          (mapv yks-hint-hinnoittelurivi (filter #(= (:vaylatyyppi %) "muu")
                                                 (:kokonaishintaiset tiedot)))
          [{:otsikko "Yksikköhintaiset: kauppamerenkulku"}]
          (mapv kok-hint-hinnoittelurivi (filter #(not (empty? (set/intersection #{"kauppamerenkulku"}
                                                                                 (:vaylatyyppi %))))
                                                 (:yksikkohintaiset tiedot)))
          [{:otsikko "Yksikköhintaiset: muut"}]
          (mapv kok-hint-hinnoittelurivi (filter #(not (empty? (set/intersection #{"muu"}
                                                                                 (:vaylatyyppi %))))
                                                 (:yksikkohintaiset tiedot)))]))

(defn- hinnoittelutiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  {:yksikkohintaiset (into []
                           (comp
                             (map #(konv/array->set % :vaylatyyppi))
                             (map #(assoc % :ensimmainen-toimenpide (c/from-date (:ensimmainen-toimenpide %))))
                             (map #(assoc % :viimeinen-toimenpide (c/from-date (:viimeinen-toimenpide %)))))
                           (hae-yksikkohintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm}))
   :kokonaishintaiset (vec (hae-kokonaishintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                   :alkupvm alkupvm
                                                                   :loppupvm loppupvm}))
   :toimenpiteet (vec (hae-toimenpiteet db {:urakkaid urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm}))})

(defn- kk-kasittelyrivi [kk kauppamerenkulku-hinta hinta-muu]
  [["Kauppamerenkulku"
    kk
    [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
    kauppamerenkulku-hinta]
   ["Muu"
    kk
    [:varillinen-teksti {:arvo "?" :tyyli :virhe}]
    hinta-muu]])

(defn- kk-kasittelyrivit [raportin-tiedot kk-vali]
  (let [hinta (fn [hinnoittelut vaylatyyppi kk-vali]
                (let [osuvat-rivit (filter #(and (or (not (empty? (set/intersection #{vaylatyyppi}
                                                                                    (:vaylatyyppi %))))
                                                     (= (:vaylatyyppi %) vaylatyyppi))
                                                 (or (pvm/valissa? (:ensimmainen-toimenpide %)
                                                                   (first (:kk-vali kk-vali))
                                                                   (second (:kk-vali kk-vali)))
                                                     (pvm/valissa? (:viimeinen-toimenpide %)
                                                                   (first (:kk-vali kk-vali))
                                                                   (second (:kk-vali kk-vali)))))
                                           hinnoittelut)]
                  (reduce + 0 (map :summa osuvat-rivit))))
        kauppamerenkulku-hinta (hinta (:yksikkohintaiset raportin-tiedot) "kauppamerenkulku" kk-vali)
        muu-hinta (hinta (:yksikkohintaiset raportin-tiedot) "muu" kk-vali)]
    (kk-kasittelyrivi (:kk-tekstina kk-vali) kauppamerenkulku-hinta muu-hinta)))

(defn- hinnoittelun-toimenpiteet [hinnoittelu toimenpiteet]
  (apply concat
         [[{:otsikko (or hinnoittelu "Muut")}]
          (mapv (fn [toimenpide]
                  [(to/reimari-toimenpidetyyppi-fmt
                     (get to/reimari-toimenpidetyypit (:toimenpide toimenpide)))
                   (pvm/pvm-opt (:suoritettu toimenpide))])
                (sort-by :suoritettu toimenpiteet))]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        hinnoittelu (hinnoittelurivit raportin-tiedot)
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}

     [:taulukko {:otsikko "Hinnoittelu"
                 :tyhja (if (empty? hinnoittelu) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      hinnoittelusarakkeet
      hinnoittelu]]))
