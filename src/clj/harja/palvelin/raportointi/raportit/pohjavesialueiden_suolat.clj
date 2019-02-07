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
  [{:leveys 3 :otsikko "Tie"}
   {:leveys 2 :otsikko "Alkuosa"}
   {:leveys 2 :otsikko "Alkuetäisyys"}
   {:leveys 2 :otsikko "Loppuosa"}
   {:leveys 2 :otsikko "Loppuetäisyys"}
   {:leveys 3 :otsikko "Pituus"}
   {:leveys 5 :otsikko "Toteutunut talvisuola yhteensä t"}
   {:leveys 5 :otsikko "Toteutunut talvisuola t/km"}
   {:leveys 5 :otsikko "Käyttöraja t/km"}])

(defn pohjavesialueen-taulukko [rivit]
  (let [eka (first rivit)]
    [:taulukko {:otsikko (str (:tunnus eka) "-" (:nimi eka))
                :viimeinen-rivi-yhteenveto? true}
     (sarakkeet)
     (into [] (map rivi-xf (loppusumma rivit)))]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (log/debug "urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm)
  (let [tulos (hae-urakan-pohjavesialueiden-suolatoteumat db {:urakkaid urakka-id
                                                              :alkupvm alkupvm
                                                              :loppupvm loppupvm})]
    (log/debug "löytyi " (count tulos) " toteumaa")
    (vec
     (concat
      [:raportti {:nimi "Pohjavesialueiden suolatoteumat"
                  :orientaatio :landscape}]
      (mapv pohjavesialueen-taulukko (vals (group-by :tunnus tulos)))))))
