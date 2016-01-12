(ns harja.palvelin.raportointi.raportit.erilliskustannukset
  "Erilliskustannusten raportti"
  (:require [taoensso.timbre :as log]
            [yesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                                                                 pylvaat ei-osumia-aikavalilla-teksti rivi]]
            [harja.domain.roolit :as roolit]
            [clj-time.coerce :as tc]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/erilliskustannukset.sql")


(defn erilliskustannuksen-nimi
  [tyyppi]
  (case tyyppi
    "asiakastyytyvaisyysbonus" "Asiakas\u00ADtyytyväisyys\u00ADbonus"
    "muu" "Muu"))

(defn hae-erilliskustannukset-aikavalille
  [db user urakka-annettu? urakka-id
   hallintayksikko-annettu? hallintayksikko-id
   toimenpide-id alkupvm loppupvm]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [kustannukset (hae-erilliskustannukset
                       db
                       urakka-annettu? urakka-id
                       hallintayksikko-annettu? hallintayksikko-id
                       toimenpide-id
                       alkupvm loppupvm)]
    (into []
          (map konv/alaviiva->rakenne)
          kustannukset)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id toimenpide-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        urakka-annettu? (if urakka-id true false)
        hallintayksikko-annettu? (if hallintayksikko-id true false)
        erilliskustannukset (reverse (sort-by (juxt (comp :id :urakka) :pvm)
                                              (hae-erilliskustannukset-aikavalille db user
                                                                                   urakka-annettu? urakka-id
                                                                                   hallintayksikko-annettu? hallintayksikko-id
                                                                                   toimenpide-id
                                                                                   alkupvm loppupvm)))
        _ (log/debug "erilliskustannukset: " erilliskustannukset)
        raportin-nimi "Erilliskustannusten raportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        erilliskustannukset-hyn-mukaan (sort-by #(or (:id (first %)) 100000)
                                                (seq (group-by :hallintayksikko
                                                               erilliskustannukset)))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true}
      (keep identity [(when-not (= konteksti :urakka) {:leveys "10" :otsikko "Urakka"})
                      {:leveys 7 :otsikko "Pvm"}
                      {:leveys 8 :otsikko "Sop. nro"}
                      {:leveys 10 :otsikko "Toimenpide"}
                      {:leveys 10 :otsikko "Tyyppi"}
                      {:leveys 7 :otsikko "Summa"}
                      {:leveys 7 :otsikko "Ind.korotus"}])

      (conj (mapv #(rivi (when-not (= konteksti :urakka) (get-in % [:urakka :nimi]))
                         (pvm/pvm (:pvm %))
                         (get-in % [:sopimus :sampoid])
                         (:tpinimi %)
                         (erilliskustannuksen-nimi (:tyyppi %))
                         (fmt/euro-opt (:rahasumma %))
                         (or (fmt/euro-opt (:indeksikorjattuna %)) ""))
                  erilliskustannukset)
            (keep identity [(when-not (= konteksti :urakka) "") "Yhteensä" "" "" ""
                            (fmt/euro-opt (reduce + (keep :rahasumma erilliskustannukset)))
                            (fmt/euro-opt (reduce + (keep :indeksikorjattuna erilliskustannukset)))]))]]))


