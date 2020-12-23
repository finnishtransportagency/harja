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
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
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

(defn- yks-hint-hinnoittelurivit [tiedot pred]
  (mapv yks-hint-hinnoittelurivi (filter pred tiedot)))

(defn yhdista-yks-hint-rivit
  [hintaryhmat omat-hinnoittelut]
  (let [omat-hinnoittelut-kauppamerenkulku (filter #(= (:vaylatyyppi %) #{"kauppamerenkulku"}) omat-hinnoittelut)
        omat-hinnoittelut-muu-vesi (filter #(= (:vaylatyyppi %) #{"muu"}) omat-hinnoittelut)
        omat-hinnoittelut-molemmat (filter #(or (empty? (:vaylatyyppi %))
                                                (= (:vaylatyyppi %) #{"muu" "kauppamerenkulku"})) omat-hinnoittelut)
        vaylatyypin-omat-hinnoittelut-summattuna (fn [hinnoittelut]
                                                   {:hinnoittelu "Yksittäiset toimenpiteet ilman tilausta"
                                                    :summa (->> hinnoittelut
                                                                (map :summa)
                                                                (reduce + 0))
                                                    :vaylatyyppi (:vaylatyyppi (first hinnoittelut))})]
    (remove nil? (concat
                   hintaryhmat
                   (when-not (empty? omat-hinnoittelut-kauppamerenkulku)
                     [(vaylatyypin-omat-hinnoittelut-summattuna omat-hinnoittelut-kauppamerenkulku)])
                   (when-not (empty? omat-hinnoittelut-muu-vesi)
                     [(vaylatyypin-omat-hinnoittelut-summattuna omat-hinnoittelut-muu-vesi)])
                   (when-not (empty? omat-hinnoittelut-molemmat)
                     [(vaylatyypin-omat-hinnoittelut-summattuna omat-hinnoittelut-molemmat)])))))

(defn- hinnoittelutiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  (let [hintaryhmat (into []
                          (map #(konv/array->set % :vaylatyyppi))
                          (hae-yksikkohintaisten-toimenpiteiden-hintaryhmat db
                                                                            {:urakkaid urakka-id
                                                                             :alkupvm alkupvm
                                                                             :loppupvm loppupvm}))
        omat-hinnoittelut (into []
                                ;; Väylätyyppi setiksi, vaikka onkin vain yksi.
                                ;; Helpompi käsitellä, kun kaikilla yks. hint. riveillä sama.
                                (map #(assoc % :vaylatyyppi #{(:vaylatyyppi %)}))
                                (hae-yksikkohintaisten-toimenpiteiden-omat-hinnoittelut-ilman-hintaryhmaa
                                  db
                                  {:urakkaid urakka-id
                                   :alkupvm alkupvm
                                   :loppupvm loppupvm}))
        kauppamerenkulku-kok (hae-kokonaishintaiset-toimenpiteet db
                                                                 {:urakkaid urakka-id
                                                                  :alkupvm alkupvm
                                                                  :loppupvm loppupvm
                                                                  :vaylatyyppi "kauppamerenkulku"})
        muu-vesiliikenne-kok (hae-kokonaishintaiset-toimenpiteet db {:urakkaid urakka-id
                                                                     :alkupvm alkupvm
                                                                     :loppupvm loppupvm
                                                                     :vaylatyyppi "muu"})]
    {:yksikkohintaiset (yhdista-yks-hint-rivit hintaryhmat omat-hinnoittelut)
     :kokonaishintaiset {:kauppamerenkulku
                         (first kauppamerenkulku-kok)
                         :muu
                         (first muu-vesiliikenne-kok)}
     :sanktiot (:summa (first (hae-sanktiot db {:urakkaid urakka-id
                                                :alkupvm alkupvm
                                                :loppupvm loppupvm})))
     :erilliskustannukset (:summa (first (hae-erilliskustannukset db {:urakkaid urakka-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm})))}))



(defn- vaylatyypin-summa
  ([tiedot vaylatyyppi]
   (vaylatyypin-summa tiedot #((:vaylatyyppi %) vaylatyyppi) vaylatyyppi))
  ([tiedot pred vaylatyyppi]
   (reduce + 0
           (concat
             (map :summa (filter pred (:yksikkohintaiset tiedot)))
             (when vaylatyyppi [(:toteutunut-maara ((keyword vaylatyyppi) (:kokonaishintaiset tiedot)))])))))

(defn- hinnoittelu-yhteensa-rivi [summa]
  ["Yhteensä" "" summa ""])

(defn- kaikki-yhteensa-rivi [otsikko summa]
  [otsikko summa])

(defn- kaikki-yhteensa-rivit [tiedot]
  [(kaikki-yhteensa-rivi "Toimenpiteet" (+ (vaylatyypin-summa tiedot #(= (:vaylatyyppi %) #{"kauppamerenkulku"}) "kauppamerenkulku")
                                           (vaylatyypin-summa tiedot #(= (:vaylatyyppi %) #{"muu"}) "muu")
                                           (vaylatyypin-summa tiedot #(or (empty? (:vaylatyyppi %))
                                                                          (= (:vaylatyyppi %) #{"muu" "kauppamerenkulku"})) nil)))
   (kaikki-yhteensa-rivi "Sanktiot" (:sanktiot tiedot))
   (kaikki-yhteensa-rivi "Erilliskustannukset" (:erilliskustannukset tiedot))
   (kaikki-yhteensa-rivi "Kaikki yhteensä"
                         (reduce + 0
                                 (concat
                                   (map :summa (:yksikkohintaiset tiedot))
                                   [(:sanktiot tiedot)]
                                   [(:erilliskustannukset tiedot)]
                                   (map :toteutunut-maara (vals (:kokonaishintaiset tiedot))))))])

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (log/debug "suorita urakka id " urakka-id " hallintayksikkö id " hallintayksikko-id)
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :urakka)
        {alueen-nimi :nimi} (first (if (= konteksti :hallintayksikko)
                                     (hallintayksikko-q/hae-organisaatio db hallintayksikko-id)
                                     (urakat-q/hae-urakka db urakka-id)))

        urakat (urakat-q/hae-urakkatiedot-laskutusyhteenvetoon
                 db {:alkupvm alkupvm :loppupvm loppupvm
                     :hallintayksikkoid hallintayksikko-id :urakkaid urakka-id
                     :urakkatyyppi "vesivayla-hoito"})
        urakoiden-parametrit (mapv #(assoc parametrit :urakka-id (:id %)
                                                      :urakka-nimi (:nimi %)
                                                      :indeksi (:indeksi %)) urakat)

        ;; Datan nostaminen tietokannasta urakoittain, hyödyntää cachea
        urakoiden-tiedot (mapv (fn [urakan-parametrit]
                                 (hinnoittelutiedot {:db db
                                                     :urakka-id (:urakka-id urakan-parametrit)
                                                     :alkupvm alkupvm
                                                     :loppupvm loppupvm}))
                               urakoiden-parametrit)

        ;; Summaillaan ja liitetään eri urakoiden tiedot yhteen
        yksikkohintaiset (apply concat (reduce conj [] (keep #(:yksikkohintaiset %) urakoiden-tiedot)))
        kokonaishintaiset (reduce (fn [a b]
                                    (merge-with
                                      (partial merge-with +)
                                      a b))
                                  {:kauppamerenkulku {:suunniteltu-maara 0M
                                                      :toteutunut-maara 0M}
                                   :muu {:suunniteltu-maara 0M
                                         :toteutunut-maara 0M}}
                                  (keep #(:kokonaishintaiset %) urakoiden-tiedot))
        sanktiot (reduce (fnil + 0 0) 0M (keep #(:sanktiot %) urakoiden-tiedot))
        erilliskustannukset (reduce (fnil + 0 0) 0M (keep #(:erilliskustannukset %) urakoiden-tiedot))

        ;; Luodaan raportin ymmärtämä mäppi
        raportin-tiedot {:yksikkohintaiset yksikkohintaiset
                         :kokonaishintaiset kokonaishintaiset
                         :sanktiot sanktiot
                         :erilliskustannukset erilliskustannukset}
        raportin-nimi "Laskutusyhteenveto"
        raportin-otsikko (raportin-otsikko alueen-nimi raportin-nimi alkupvm loppupvm)]

    (vec
      (keep identity
            [:raportti {:orientaatio :landscape
                        :nimi raportin-otsikko}

             (let [kauppamerenkulku-kok-hint-rivit (kok-hint-hinnoittelurivit
                                                     (:kauppamerenkulku (:kokonaishintaiset raportin-tiedot)))
                   kauppamerenkulku-yks-hint-rivit (yks-hint-hinnoittelurivit
                                                     (:yksikkohintaiset raportin-tiedot)
                                                     #(= (:vaylatyyppi %) #{"kauppamerenkulku"}))
                   kauppamerenkulku-yht-rivit (hinnoittelu-yhteensa-rivi
                                                (vaylatyypin-summa raportin-tiedot #(= (:vaylatyyppi %) #{"kauppamerenkulku"}) "kauppamerenkulku"))]
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
                                             #(= (:vaylatyyppi %) #{"muu"}))
                   muu-vesi-yht-rivit (hinnoittelu-yhteensa-rivi
                                        (vaylatyypin-summa raportin-tiedot #(= (:vaylatyyppi %) #{"muu"}) "muu"))]
               [:taulukko {:otsikko "Muu vesiliikenne"
                           :tyhja (if (empty? muu-vesi-yht-rivit) "Ei raportoitavaa.")
                           :sheet-nimi raportin-nimi
                           :viimeinen-rivi-yhteenveto? true}
                hinnoittelusarakkeet
                (concat muu-vesi-kok-hint-rivit
                        muu-vesi-yks-hint-rivit
                        [muu-vesi-yht-rivit])])

             ;; Joihinkin tilauksiin voi kuulua toimenpiteitä molemmista väylätyypeistä
             (let [molemmat-yks-hint-rivit (yks-hint-hinnoittelurivit
                                             (:yksikkohintaiset raportin-tiedot)
                                             #(or (empty? (:vaylatyyppi %))
                                                  (= (:vaylatyyppi %) #{"kauppamerenkulku" "muu"})))
                   molemmat-summa (vaylatyypin-summa raportin-tiedot #(or (empty? (:vaylatyyppi %))
                                                                          (= (:vaylatyyppi %) #{"kauppamerenkulku" "muu"})) nil)
                   molemmat-yht-rivit (hinnoittelu-yhteensa-rivi molemmat-summa)]
               (when-not (= 0M molemmat-summa)
                 [:taulukko {:otsikko "Molemmat väylätyypit, tai ei väylätyyppiä"
                             :tyhja (if (empty? molemmat-yht-rivit) "Ei raportoitavaa.")
                             :sheet-nimi raportin-nimi
                             :viimeinen-rivi-yhteenveto? true}
                  hinnoittelusarakkeet
                  (concat molemmat-yks-hint-rivit
                          [molemmat-yht-rivit])]))

             (let [kaikki-yht-rivit (kaikki-yhteensa-rivit raportin-tiedot)]
               [:taulukko {:otsikko "Yhteenveto"
                           :tyhja (if (empty? kaikki-yht-rivit) "Ei raportoitavaa.")
                           :sheet-nimi raportin-nimi
                           :viimeinen-rivi-yhteenveto? true}
                yhteenveto-sarakkeet
                kaikki-yht-rivit])

             ;; Listataan lopuksi mitkä urakat ovat mukana raportilla
             (when (and hallintayksikko-id (< 0 (count urakat)))
               [:otsikko "Raportti sisältää seuraavien urakoiden tiedot: "])
             (when (and hallintayksikko-id (< 0 (count urakat)))
               (for [u (sort-by :nimi urakat)]
                 [:teksti (str (:nimi u))]))]))))
