(ns harja.palvelin.raportointi.raportit.pohjavesialueiden-suolat
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

            [harja.domain.raportointi :refer [info-solu]]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [clojure.string :as str]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/palvelin/raportointi/raportit/pohjavesialueiden_suolat.sql")

(defn loppusumma [tulos]
  (vec (concat tulos [{:tie "Yhteensä" :yhteensa (reduce + (map :yhteensa tulos))}])))

(defn rivi-xf [rivi]
  [(str (:tie rivi))
   (str (:alkuosa rivi))
   (str (:alkuet rivi))
   (str (:loppuosa rivi))
   (str (:loppuet rivi))
   (if (:pituus rivi)
     (format "%.1f" (:pituus rivi))
     "")
   (format "%.1f" (:yhteensa rivi))
   (if (:maara_t_per_km rivi)
     (format "%.1f" (:maara_t_per_km rivi))
     "")
   (if (:kayttoraja rivi)
     (:kayttoraja rivi)
     "")])

(defn sarakkeet []
  [{:leveys 3 :fmt :kokonaisluku :otsikko "Tie"}
   {:leveys 2 :fmt :kokonaisluku :otsikko "Alku\u00ADosa"}
   {:leveys 2 :fmt :kokonaisluku :otsikko "Alku\u00ADetäisyys"}
   {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu\u00ADosa"}
   {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu\u00ADetäisyys"}
   {:leveys 3 :fmt :numero :otsikko "Pituus"}
   {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola yhteensä (t)"}
   {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola (t/km)"}
   {:leveys 5 :fmt :numero :otsikko "Käyttö\u00ADraja (t/km)"}])

(defn pohjavesialueen-taulukko [rivit]
  (let [eka (first rivit)]
    [:taulukko {:otsikko (str (:tunnus eka) "-" (:nimi eka))
                :viimeinen-rivi-yhteenveto? true
                :tyhja (if (empty? rivit) "Ei raportoitavia suolatoteumia.")}
     (sarakkeet)
     (into [] (map rivi-xf (loppusumma rivit)))]))

(defn laske [db urakka-id alkupvm loppupvm]
  (let [urakan-pohjavesialueiden-summat (map (fn [summa]
                                               (assoc summa
                                                 :maara_t_per_km (/ (:yhteensa summa) (/ (:pituus summa) 1000))))
                                             (pohjavesialueen-tiekohtaiset-summat db {:urakka urakka-id
                                                                                      :alkupvm alkupvm
                                                                                      :loppupvm loppupvm}))
        urakan-alueet (urakan-pohjavesialueet db {:urakka urakka-id})
        pva-summat-alueittain (map (fn [summan-tiedot]
                                     (assoc summan-tiedot :nimi (some #(when (= (:tunnus summan-tiedot) (:tunnus %))
                                                                         (:nimi %))
                                                                      urakan-alueet)))
                                   urakan-pohjavesialueiden-summat)]
    pva-summat-alueittain))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm hallintayksikko-id] :as parametrit}]
  (log/debug "urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm)
  (let [tulos (laske db urakka-id alkupvm loppupvm)
        raportin-nimi "Pohjavesialueiden suolatoteumat"
        otsikko (raportin-otsikko
                  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                  raportin-nimi alkupvm loppupvm)]
    (vec
     (concat
      [:raportti {:orientaatio :landscape
                  :nimi otsikko}]
      (if (empty? tulos)
        [:teksti yleinen/ei-tuloksia-aikavalilla-str]
        (mapv pohjavesialueen-taulukko (sort-by #(->> % first :nimi)
                                                (map #(sort-by (juxt :tie :alkuosa :alkuet) %)
                                                     (vals (group-by :tunnus tulos))))))))))
