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
   {:leveys 1 :otsikko "Maksuerät"}
   {:leveys 1 :otsikko "Tunnus"}
   {:leveys 1 :otsikko "Tilaus\u00ADvaltuus [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Suunni\u00ADtellut [t €] HOX PITÄISIKÖ OLLA KPL?" :fmt :raha}
   {:leveys 1 :otsikko "Toteutunut [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä (S+T) [t €]" :fmt :raha}
   {:leveys 1 :otsikko "Jäljellä [€]" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä jäljellä (hoito ja käyttö)"}])

(def erittelysarakkeet
  [{:leveys 3 :otsikko "Työ"}
   {:leveys 1 :otsikko "Kuvaus / Erä"}
   {:leveys 1 :otsikko "Tarjousp. V-kirje"}
   {:leveys 1 :otsikko "Pvm"}
   {:leveys 1 :otsikko "Suunni\u00ADtellut kust. [€]" :fmt :raha}
   {:leveys 1 :otsikko "Tilaus V-kirje"}
   {:leveys 1 :otsikko "Pvm"}
   {:leveys 1 :otsikko "Suunni\u00ADteltu valmis\u00ADtumispvm"}
   {:leveys 1 :otsikko "Valmistu\u00ADmispvm"}
   {:leveys 1 :otsikko "Laskun n:o"}
   {:leveys 1 :otsikko "Laskutus pvm"}
   {:leveys 1 :otsikko "Maksuerän tunnus"}
   {:leveys 1 :otsikko "Laskut. summa" :fmt :raha}])

(defn- kok-hint-hinnoittelurivi [tiedot]
  [(:hinnoittelu tiedot)
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   (:summa tiedot)
   (:summa tiedot)
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]])

(defn- yks-hint-hinnoittelurivi [tiedot]
  [(str (to/reimari-toimenpidetyyppi-fmt
          (get to/reimari-toimenpidetyypit (:koodi tiedot)))
        " ("
        (:maara tiedot)
        "kpl)")
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]])

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
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    kk
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    kauppamerenkulku-hinta]
   ["Muu"
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    kk
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
    [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
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

(defn- kk-erittelyrivit [raportin-tiedot alkupvm loppupvm]
  (let [kk-valit (pvm/aikavalin-kuukausivalit [(pvm/suomen-aikavyohykkeeseen (c/from-date alkupvm))
                                               (pvm/suomen-aikavyohykkeeseen (c/from-date loppupvm))])
        kk-valit (mapv
                   #(-> {:kk-vali %
                         :kk (t/month (first %))
                         :kk-tekstina (str (pvm/kk-fmt (t/month (first %)))
                                           " "
                                           (t/year (first %)))})
                   kk-valit)]
    (apply concat
           (mapv (partial kk-kasittelyrivit raportin-tiedot) kk-valit))))

(defn- hinnoittelun-toimenpiteet [hinnoittelu toimenpiteet]
  (apply concat
         [[{:otsikko (or hinnoittelu "Muut")}]
          (mapv (fn [toimenpide]
                  [(to/reimari-toimenpidetyyppi-fmt
                     (get to/reimari-toimenpidetyypit (:toimenpide toimenpide)))
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   (pvm/pvm-opt (:suoritettu toimenpide))
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]
                   [:varillinen-teksti {:arvo "???" :tyyli :virhe}]])
                toimenpiteet)]))

(defn- toimenpiteiden-erittelyrivit [raportin-tiedot]
  (let [hinnoittelulla-ryhmiteltyna (group-by :hinnoittelu-id
                                              (:toimenpiteet raportin-tiedot))]
    (vec (mapcat (fn [hinnoittelu-id]
                   (let [hinnoittelun-nimi (:hinnoittelu (first (get hinnoittelulla-ryhmiteltyna hinnoittelu-id)))]
                     (hinnoittelun-toimenpiteet hinnoittelun-nimi (get hinnoittelulla-ryhmiteltyna hinnoittelu-id))))
                 (reverse (sort (keys hinnoittelulla-ryhmiteltyna)))))))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [raportin-tiedot (hinnoittelutiedot {:db db
                                            :urakka-id urakka-id
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
        hinnoittelu (hinnoittelurivit raportin-tiedot)
        kk-erittely (kk-erittelyrivit raportin-tiedot alkupvm loppupvm)
        toimenpiteiden-erittely (toimenpiteiden-erittelyrivit raportin-tiedot)
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
      erittelysarakkeet
      toimenpiteiden-erittely]]))
