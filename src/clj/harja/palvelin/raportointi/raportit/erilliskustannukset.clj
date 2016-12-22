(ns harja.palvelin.raportointi.raportit.erilliskustannukset
  "Erilliskustannusten raportti"
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                                                                 pylvaat-kuukausittain ei-osumia-aikavalilla-teksti rivi]]
            [harja.domain.raportointi :refer [info-solu]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/erilliskustannukset.sql"
  {:positional? true})


(defn erilliskustannuksen-nimi
  [tyyppi]
  (case tyyppi
    "asiakastyytyvaisyysbonus" "As.tyyt.\u00ADbonus"
    "muu" "Muu"))

(defn hae-erilliskustannukset-aikavalille
  [db user urakka-annettu? urakka-id
   urakkatyyppi hallintayksikko-annettu? hallintayksikko-id
   toimenpide-id alkupvm loppupvm]
  (let [kustannukset (hae-erilliskustannukset
                       db
                       urakka-annettu? urakka-id
                       urakkatyyppi
                       hallintayksikko-annettu? hallintayksikko-id
                       toimenpide-id
                       alkupvm loppupvm)]
    (into []
          (comp
            (map #(if (= (:tyyppi %) "asiakastyytyvaisyysbonus")
                   (assoc % :indeksikorotus (:bonusindeksikorotus %))
                   (assoc % :indeksikorotus (if-not (:indeksin_nimi %)
                                              0
                                              (if (and (:indeksikorjattuna %)
                                                       (:rahasumma %))
                                                (- (:indeksikorjattuna %) (:rahasumma %))
                                                0)))))
            (map konv/alaviiva->rakenne))
          kustannukset)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id toimenpide-id alkupvm loppupvm urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        urakka-annettu? (boolean urakka-id)
        hallintayksikko-annettu? (boolean hallintayksikko-id)
        erilliskustannukset (reverse (sort-by (juxt (comp :id :urakka) :pvm)
                                              (hae-erilliskustannukset-aikavalille db user
                                                                                   urakka-annettu? urakka-id
                                                                                   (when urakkatyyppi (name urakkatyyppi))
                                                                                   hallintayksikko-annettu? hallintayksikko-id
                                                                                   toimenpide-id
                                                                                   alkupvm loppupvm)))
        raportin-nimi "Erilliskustannusten raportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        ;; odotellaan tarviiko asiakas ryhmittelyä hallintayksiköiden mukaan..
        #_ erilliskustannukset-hyn-mukaan #_(sort-by #(or (:id (first %)) 100000)
                                                (seq (group-by :hallintayksikko
                                                               erilliskustannukset)))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:oikealle-tasattavat-kentat (if (not= konteksti :urakka)
                                               #{5 6}
                                               #{4 5})
                 :otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi raportin-nimi}
      (keep identity [(when-not (= konteksti :urakka) {:leveys 10 :otsikko "Urakka"})
                      {:leveys 7 :otsikko "Pvm"}
                      {:leveys 7 :otsikko "Sop. nro"}
                      {:leveys 12 :otsikko "Toimenpide"}
                      {:leveys 7 :otsikko "Tyyppi"}
                      {:leveys 6 :otsikko "Summa €" :fmt :raha}
                      {:leveys 6 :otsikko "Ind.korotus €" :fmt :raha}])

      (keep identity
            (conj (mapv #(rivi (when-not (= konteksti :urakka) (get-in % [:urakka :nimi]))
                               (pvm/pvm (:pvm %))
                               (get-in % [:sopimus :sampoid])
                               (:tpinimi %)
                               (erilliskustannuksen-nimi (:tyyppi %))
                               (or (:rahasumma %)
                                   (info-solu "Ei rahasummaa"))
                               (or (:indeksikorotus %)
                                   (info-solu "Ei indeksikorotusta")))
                        erilliskustannukset)
                  (when (not (empty? erilliskustannukset))
                    (keep identity (flatten [(if (not= konteksti :urakka) ["Yhteensä" ""]
                                                                          ["Yhteensä"])
                                             "" "" ""
                                             (reduce + (keep :rahasumma erilliskustannukset))
                                             (reduce + (keep :indeksikorotus erilliskustannukset))])))))]]))
