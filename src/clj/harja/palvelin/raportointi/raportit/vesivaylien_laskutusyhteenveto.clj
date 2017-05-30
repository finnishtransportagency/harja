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
  [{:leveys 3 :otsikko "Hinnoittelu"}
   {:leveys 1 :otsikko "Maksuerät"}
   {:leveys 1 :otsikko "Tunnus"}
   {:leveys 1 :otsikko "Tilausvaltuus [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Suunnitellut [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Toteutunut [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä (S+T) [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Jäljellä [€]" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä jäljellä (hoito ja käyttö)"}])

(def erittelysarakkeet
  [{:leveys 3 :otsikko "Työ"}
   {:leveys 1 :otsikko "Kuvaus / Erä"}
   {:leveys 1 :otsikko "Tarjousp. V-kirje"}
   {:leveys 1 :otsikko "Pvm"}
   {:leveys 1 :otsikko "Suunnitellut kust. [€]" :fmt :raha}
   {:leveys 1 :otsikko "Tilaus V-kirje"}
   {:leveys 1 :otsikko "Pvm"}
   {:leveys 1 :otsikko "Suunniteltu valmistumispvm"}
   {:leveys 1 :otsikko "Valmistusmispvm"}
   {:leveys 1 :otsikko "Laskun n:o"}
   {:leveys 1 :otsikko "Laskutus pvm"}
   {:leveys 1 :otsikko "Maksuerän tunnus"}
   {:leveys 1 :otsikko "Laskut. summa"}])

(defn- hinnoittelurivi [tiedot]
  [(:hinnoittelu tiedot) "" "" "" "" (:summa tiedot)])

(defn- hinnoittelurivit [tiedot]
  (apply concat
         [[{:otsikko "Kokonaishintaiset: kauppamerenkulku"}]
          [["TODO mitä tähän tulee kun kok. hint. ei hinnoitella Harjassa?"]]
          [{:otsikko "Kokonaishintaiset: muut"}]
          [["TODO mitä tähän tulee kun kok. hint. ei hinnoitella Harjassa?"]]
          [{:otsikko "Yksikköhintaiset: kauppamerenkulku"}]
          ;; Hintaryhmättömät. jotka ovat kauppamerenkulkua sekä hintaryhmälliset, joissa
          ;; tehty kauppamerenkulkua
          (concat (mapv hinnoittelurivi (filter #(= (:vaylatyyppi %) "kauppamerenkulku")
                                                (:yksikkohintaiset-hintaryhmattomat tiedot)))
                  (mapv hinnoittelurivi (filter #(not (empty? (set/intersection #{"kauppamerenkulku"}
                                                                                (:vaylatyyppi %))))
                                                (:yksikkohintaiset-hintaryhmalliset tiedot))))
          [{:otsikko "Yksikköhintaiset: muut"}]
          ;; Hintaryhmättömät. jotka ovat väylätyyppiä "muu" sekä hintaryhmälliset, joissa
          ;; työstetty väylätyyppiä "muu"
          (concat (mapv hinnoittelurivi (filter #(= (:vaylatyyppi %) "muu")
                                                (:yksikkohintaiset-hintaryhmattomat tiedot)))
                  (mapv hinnoittelurivi (filter #(not (empty? (set/intersection #{"muu"}
                                                                                (:vaylatyyppi %))))
                                                (:yksikkohintaiset-hintaryhmalliset tiedot))))]))

(defn- hinnoittelutiedot [{:keys [db urakka-id alkupvm loppupvm]}]
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

(defn- kk-kasittelyrivi [tiedot]
  ["" "" "" "" "" "" (:kk tiedot)])

(defn- kk-erittelyrivit [alkupvm loppupvm]
  (let [kk-valit (pvm/aikavalin-kuukausivalit [(c/from-date alkupvm)
                                               (c/from-date loppupvm)])
        kk-valit-formatoitu (mapv
                              #(-> {:kk (str (pvm/kk-fmt (t/month (first %)))
                                             " "
                                             (t/year (first %)))})
                              kk-valit)]
    (mapv kk-kasittelyrivi kk-valit-formatoitu)))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        hinnoittelu (hinnoittelurivit raportin-tiedot)
        kk-erittely (kk-erittelyrivit alkupvm loppupvm)
        toimenpiteiden-erittely []
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}

     [:taulukko {:otsikko "Hinnoittelu"
                 :tyhja (if (empty? hinnoittelu) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      hinnoittelusarakkeet
      hinnoittelu]

     [:taulukko {:otsikko "Kuukausierittely"
                 :tyhja (if (empty? kk-erittely) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      erittelysarakkeet
      kk-erittely]

     [:taulukko {:otsikko "Toimenpiteiden erittely"
                 :tyhja (if (empty? toimenpiteiden-erittely) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      toimenpiteiden-erittely
      []]]))
