(ns harja.palvelin.raportointi.raportit.muutos-ja-lisatyot
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.urakan-toimenpiteet :as toimenpiteet-q]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                     pylvaat-kuukausittain ei-osumia-aikavalilla-teksti rivi]]

            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [clojure.string :as str]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/muutos_ja_lisatyot.sql")



(defn tyon-tyypin-nimi
  [tyyppi]
  (case tyyppi
    "muutostyo" "Muutos\u00ADtyö"
    "lisatyo" "Lisä\u00ADtyö"
    "akillinen-hoitotyo" "Äkil\u00ADlinen hoito\u00ADtyö"
    "vahinkojen-korjaukset" "Vahinko\u00ADjen korjauk\u00ADset"

    :default "Muu"))

(defn hae-muutos-ja-lisatyot-aikavalille
  [db user urakka-annettu? urakka-id
   urakkatyyppi hallintayksikko-annettu? hallintayksikko-id
   toimenpide-id alkupvm loppupvm ryhmiteltyna?]
  (let [parametrit {:urakka_annettu urakka-annettu?
                    :urakka urakka-id
                    :urakkatyyppi urakkatyyppi
                    :hallintayksikko_annettu hallintayksikko-annettu?
                    :hallintayksikko hallintayksikko-id
                    :rajaa_tpi (not (nil? toimenpide-id)) :tpi toimenpide-id
                    :alku alkupvm :loppu loppupvm}
        toteumat (if ryhmiteltyna?
                   (hae-tyypin-mukaan-ryhmitellyt-muutos-ja-lisatyot-raportille db parametrit)
                   (hae-muutos-ja-lisatyot-raportille db parametrit))]
    toteumat))

(defn yksittaiset-tyorivit
  [konteksti tyot]
  (mapv #(rivi (when-not (= konteksti :urakka) (get-in % [:urakka :nimi]))
               (pvm/pvm (:alkanut %))
               (tyon-tyypin-nimi (:tyyppi %))
               (get-in % [:tpi :nimi])
               (get-in % [:tehtava :nimi])
               (if (get-in % [:tehtava :paivanhinta])
                 "Päivän hinta"
                 (get-in % [:tehtava :maara]))
               (or (get-in % [:tehtava :summa]) [:info "Ei rahasummaa"])
               (or (:korotus %) [:info "Indeksi puuttuu"]))
        tyot))

(defn tyyppikohtaiset-rivit
  [tyot]
  (mapv #(rivi (tyon-tyypin-nimi (:tyyppi %))
               (get-in % [:tpi :nimi])
               (or (get-in % [:tehtava :summa]) [:info "Ei rahasummaa"])
               (or (:korotus %) [:info "Indeksi puuttuu"])) tyot))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id toimenpide-id
                               alkupvm loppupvm urakkatyyppi urakoittain?] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        urakka-annettu? (boolean urakka-id)
        hallintayksikko-annettu? (boolean hallintayksikko-id)
        naytettavat-alueet (yleinen/naytettavat-alueet db konteksti {:urakka          urakka-id
                                                                     :hallintayksikko hallintayksikko-id
                                                                     :urakkatyyppi    (when urakkatyyppi (name urakkatyyppi))
                                                                     :alku            alkupvm
                                                                     :loppu           loppupvm})
        kayta-ryhmittelya? (and (not urakoittain?)
                                (not= :urakka konteksti))
        muutos-ja-lisatyot-kannasta (into []
                                          (map konv/alaviiva->rakenne)
                                          (hae-muutos-ja-lisatyot-aikavalille db user
                                                                              urakka-annettu? urakka-id
                                                                              (when urakkatyyppi (name urakkatyyppi))
                                                                              hallintayksikko-annettu? hallintayksikko-id
                                                                              toimenpide-id
                                                                              alkupvm loppupvm kayta-ryhmittelya?))
        muutos-ja-lisatyot (if kayta-ryhmittelya?
                             muutos-ja-lisatyot-kannasta
                             (reverse (sort-by (juxt (comp :id :urakka) :alkanut) muutos-ja-lisatyot-kannasta)))

        muutos-ja-lisatyot-hyn-mukaan (sort-by #(or (:id (first %)) 100000)
                                               (seq (group-by :hallintayksikko
                                                              muutos-ja-lisatyot)))

        raportin-nimi "Muutos- ja lisätöiden raportti"
        tpi-nimi (if toimenpide-id
                   (:nimi (first (toimenpiteet-q/hae-tuote-kolmostason-toimenpidekoodilla db {:id toimenpide-id})))
                   "Kaikki toimenpiteet")
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)

        otsikot (if kayta-ryhmittelya?
                  [{:leveys 7 :otsikko "Tyyppi"}
                   {:leveys 12 :otsikko "Toimenpide"}
                   {:leveys 5 :otsikko "Summa €" :fmt :raha}
                   {:leveys 5 :otsikko "Ind.korotus €" :fmt :raha}]
                  [(when-not (= konteksti :urakka) {:leveys 10 :otsikko "Urakka"})
                   {:leveys 5 :otsikko "Pvm"}
                   {:leveys 7 :otsikko "Tyyppi"}
                   {:leveys 12 :otsikko "Toimenpide"}
                   {:leveys 12 :otsikko "Tehtävä"}
                   {:leveys 5 :otsikko "Määrä"}
                   {:leveys 5 :otsikko "Summa €" :fmt :raha}
                   {:leveys 5 :otsikko "Ind.korotus €" :fmt :raha}])]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko (str otsikko ", " tpi-nimi)
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi raportin-nimi}
      (keep identity otsikot)


      (keep identity
            (into []
                  (concat
                    (apply concat
                           (for [[hy hyn-tyot] muutos-ja-lisatyot-hyn-mukaan]
                             (concat
                               (when (or urakoittain? (not= :urakka konteksti))
                                 [{:otsikko (if (:nimi hy)
                                              (str (:elynumero hy) " " (:nimi hy))
                                              "Ilmoitukset ilman urakkaa")}])

                               (when (or urakoittain? (= :urakka konteksti))
                                 (apply concat
                                        (for [[urakka tyot] (group-by :urakka hyn-tyot)]
                                          (if urakoittain?
                                            (yksittaiset-tyorivit konteksti tyot)
                                            (tyyppikohtaiset-rivit tyot)))))
                               (when-not (empty? hyn-tyot)
                                 (let [summat-yht (reduce + (keep #(get-in % [:tehtava :summa]) hyn-tyot))
                                       korotukset-yht (reduce + (keep :korotus hyn-tyot))
                                       alueen-teksti (if (= :urakka konteksti)
                                                       (str (get-in (first hyn-tyot) [:urakka :nimi]) " yhteensä")
                                                       (str (:nimi hy) " yhteensä"))
                                       kentat (if kayta-ryhmittelya?
                                                (keep identity [alueen-teksti "" summat-yht korotukset-yht])
                                                (if (or (not urakoittain?) (= :urakka konteksti))
                                                 (keep identity [alueen-teksti "" "" "" "" summat-yht korotukset-yht])
                                                 (keep identity [alueen-teksti "" "" "" "" "" summat-yht korotukset-yht])))]
                                   (when (:nimi hy)
                                     [{:lihavoi? true
                                       :rivi     kentat}]))))



                             )))))]
     [:teksti (str "Summat ja indeksit yhteensä "
                   (fmt/euro-opt (+
                                   (reduce + (keep #(get-in % [:tehtava :summa]) muutos-ja-lisatyot))
                                   (reduce + (keep :korotus muutos-ja-lisatyot)))))]]))


