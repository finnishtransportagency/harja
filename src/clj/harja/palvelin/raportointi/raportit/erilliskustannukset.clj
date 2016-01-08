(ns harja.palvelin.raportointi.raportit.erilliskustannukset
  "Erilliskustannusten raportti"
  (:require [taoensso.timbre :as log]
            [yesql.core :refer [defqueries]]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                                                                 pylvaat ei-osumia-aikavalilla-teksti]]
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
   alkupvm loppupvm]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (map konv/alaviiva->rakenne)
        (hae-erilliskustannukset
          db
          urakka-annettu? urakka-id
          hallintayksikko-annettu? hallintayksikko-id
          alkupvm loppupvm)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        hoitokauden-alkupvm (first (pvm/paivamaaran-hoitokausi alkupvm))
        urakka-annettu? (if urakka-id true false)
        hallintayksikko-annettu? (if hallintayksikko-id true false)
        erilliskustannukset (hae-erilliskustannukset-aikavalille db user
                                                                 urakka-annettu? urakka-id
                                                                 hallintayksikko-annettu? hallintayksikko-id
                                                                 alkupvm loppupvm)
        _ (log/debug "erilliskustannukset: " erilliskustannukset)
        _ (log/debug "urakka " urakka-id)
        raportin-nimi "Erilliskustannusten raportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        erilliskustannukset-hyn-mukaan (sort-by #(or (:id (first %)) 100000)
                                        (seq (group-by :hallintayksikko
                                                       erilliskustannukset)))
        nayta-pylvaat? (and (> (count erilliskustannukset) 0)
                            (not= (vuosi-ja-kk alkupvm) (vuosi-ja-kk loppupvm)))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true}
      [{:leveys "10" :otsikko "Urakka"}
       {:leveys "7" :otsikko "Pvm"}
       {:leveys "5" :otsikko "Sop. nro"}
       {:leveys "10" :otsikko "Toimenpide"}
       {:leveys "10" :otsikko "Tyyppi"}
       {:leveys "10" :otsikko "Summa"}
       {:leveys "15" :otsikko "Ind.korotus"}]

      (conj (mapv (juxt (comp :nimi :urakka)
                        (comp pvm/pvm :pvm)
                        :sopimus
                        :toimenpideinstanssi
                        (comp erilliskustannuksen-nimi :tyyppi)
                        (comp fmt/euro-opt :rahasumma)
                        :indeksikorjattuna)
                  erilliskustannukset)
            [nil "Yhteensä" nil nil nil
             (fmt/euro-opt (reduce + (keep :rahasumma erilliskustannukset)))
             (fmt/euro-opt (reduce + (keep :indeksikorjattuna erilliskustannukset)))])

      #_(into
        []
        (concat
          (apply concat
                 ;; Tehdään rivi jokaiselle urakalle, ja näytetään niiden erityyppistem kustannusten määrä
                 (for [[hy erilliskustannukset] erilliskustannukset-hyn-mukaan]
                   (concat
                     [{:otsikko (or (:nimi hy) "Kustannukset ilman urakkaa")}]
                     (for [[urakka hyn-erilliskustannukset] (group-by :urakka erilliskustannukset)
                           :let [urakan-nimi (or (:nimi urakka) "Ei urakkaa")
                                 bonus (:rahasumma (filter #(= "asiakastyytyvaisyysbonus" (:tyyppi %)) hyn-erilliskustannukset))
                                 muu (:rahasumma (filter #(= "muu" (:tyyppi %)) hyn-erilliskustannukset))]]
                       [urakan-nimi bonus muu])
                     ;; lasketaan myös hallintayksiköiden summarivi
                     (when (= :koko-maa konteksti)
                       (let [bonus-yht (:rahasumma (filter #(= "asiakastyytyvaisyysbonus" (:tyyppi %)) erilliskustannukset))
                             muu-yht (:rahasumma (filter #(= "muu" (:tyyppi %)) erilliskustannukset))]
                         (when (:nimi hy)
                           [(seq [(str (:nimi hy) " yhteensä") bonus-yht muu-yht])]))))))

          ;; Tehdään yhteensä rivi, jossa kaikki erilliskustannukset lasketaan yhteen materiaalin perusteella
          (when-not (= :urakka konteksti)
            (let [bonus-yht (count (filter #(= "asiakastyytyvaisyysbonus" (:tyyppi %)) erilliskustannukset))
                  muu-yht (count (filter #(= "muu" (:tyyppi %)) erilliskustannukset))]
              [(concat ["Yhteensä"]
                       [bonus-yht muu-yht])]))))]

     #_(when nayta-pylvaat?
       (if-not (empty? erilliskustannukset-kuukausittain-tyyppiryhmiteltyna)
         (pylvaat {:otsikko              (str "Kustannukset kuukausittain" hoitokaudella-tahan-asti-opt)
                   :alkupvm              graafin-alkupvm :loppupvm loppupvm
                   :kuukausittainen-data erilliskustannukset-kuukausittain-tyyppiryhmiteltyna :piilota-arvo? #{0}
                   :legend               ["Bonus" "Muu"]})
         (ei-osumia-aikavalilla-teksti "kustannuksia" graafin-alkupvm loppupvm)))
     ]))


