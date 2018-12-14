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

(defn rivi-xf [rivi]
  [(str (:tunnus rivi) " - " (:nimi rivi))
   (str (:tie rivi))
   (format "%.1f" (:yhteensa rivi))
   (format "%.1f" (:maara_t_per_km rivi))
   (if (nil? (:kayttoraja rivi))
     "-"
     (:kayttoraja rivi))])

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (log/debug "urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm)
  (let [tulos (hae-urakan-pohjavesialueiden-suolatoteumat db {:urakkaid urakka-id
                                                              :alkupvm alkupvm
                                                              :loppupvm loppupvm})]
    (log/debug "löytyi " (count tulos) " toteumaa")
    [:raportti {:nimi "Pohjavesialueiden suolatoteumat"
                :orientaatio :landscape}
     [:taulukko {:otsikko "Suolatoteumat"}
      [{:leveys 7 :otsikko "Pohjavesialue"}
       {:leveys 5 :otsikko "Tie"}
       {:leveys 5 :otsikko "Toteutunut talvisuola yhteensä t"}
       {:leveys 5 :otsikko "Toteutunut talvisuola t/km"}
       {:leveys 5 :otsikko "Käyttöraja t/km"}]
      (into [] (map rivi-xf tulos))]]))
