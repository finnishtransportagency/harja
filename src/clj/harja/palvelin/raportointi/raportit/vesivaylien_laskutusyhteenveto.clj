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
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/vesivaylien_laskutusyhteenveto.sql")

(defn- raportin-rivi [tiedot]
  (log/debug "RIVI: " (pr-str tiedot))
  [(:hinnoittelu tiedot) "" "" "" (:summa tiedot)])

(defn- muodosta-raportin-rivit [tiedot]
  (apply concat
         [
          [{:otsikko "Kokonaishintaiset: kauppamerenkulku"}]
          [["TODO"]]
          [{:otsikko "Kokonaishintaiset: muut"}]
          [["TODO"]]
          [{:otsikko "Yksikköhintaiset: kauppamerenkulku"}]
          ;; Hintaryhmättömät. jotka ovat kauppamerenkulkua sekä hintaryhmälliset, joiden
          ;; kaikki toimenpiteet ovat kauppamerenkulkua
          (concat (mapv raportin-rivi (filter #(= (:vaylatyyppi %) "kauppamerenkulku")
                                              (:yksikkohintaiset-hintaryhmattomat tiedot)))
                  (mapv raportin-rivi (filter #(= #{"kauppamerenkulku"} (:vaylatyyppi %))
                                              (:yksikkohintaiset-hintaryhmalliset tiedot))))
          [{:otsikko "Yksikköhintaiset: muut"}]
          ;; Hintaryhmättömät. jotka ovat väylätyyppiä "muu" sekä hintaryhmälliset, joiden
          ;; kaikki toimenpiteet ovat väylätyyppiä "muu"
          (concat (mapv raportin-rivi (filter #(= (:vaylatyyppi %) "muu")
                                              (:yksikkohintaiset-hintaryhmattomat tiedot)))
                  (mapv raportin-rivi (filter #(= #{"muu"} (:vaylatyyppi %))
                                              (:yksikkohintaiset-hintaryhmalliset tiedot))))
          [{:otsikko "Yksikköhintaiset: useita väylätyyppejä sisältävät"}]
          ;; Hintaryhmälliset, joissa useita eri väylätyyppejä edustettuna
          (mapv raportin-rivi (filter #(> (count (:vaylatyyppi %)) 1)
                                      (:yksikkohintaiset-hintaryhmalliset tiedot)))]))

(defn- raportin-sarakkeet []
  [{:leveys 3 :otsikko "Toimenpide / Maksuerä"}
   {:leveys 1 :otsikko "Maksuerät"}
   {:leveys 1 :otsikko "Tunnus"}
   {:leveys 1 :otsikko "Tilausvaltuus [t €]"}
   {:leveys 1 :otsikko "Suunnitellut [t €]"}
   {:leveys 1 :otsikko "Toteutunut [t €]"}
   {:leveys 1 :otsikko "Yhteensä (S+T) [t €]"}
   {:leveys 1 :otsikko "Jäljellä [€]"}
   {:leveys 1 :otsikko "Yhteensä jäljellä (hoito ja käyttö)"}])

(defn hae-raportin-tiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  {:yksikkohintaiset-hintaryhmattomat
   (hae-yksikkohintaiset-ryhmattomat-toimenpiteet db {:urakkaid urakka-id
                                                      :alkupvm alkupvm
                                                      :loppupvm loppupvm})
   :yksikkohintaiset-hintaryhmalliset
   (into []
         (map #(konv/array->set % :vaylatyyppi))
         (hae-yksikkohintaiset-ryhmalliset-toimenpiteet db {:urakkaid urakka-id
                                                            :alkupvm alkupvm
                                                            :loppupvm loppupvm}))})

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hae-raportin-tiedot {:db db
                                              :urakka-id urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})
        raportin-rivit (muodosta-raportin-rivit raportin-tiedot)
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko "Projekti"
                 :tyhja (if (empty? raportin-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      (raportin-sarakkeet)
      raportin-rivit]]))
