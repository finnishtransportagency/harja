(ns harja.palvelin.raportointi.raportit.toimenpideajat
  "Toimenpiteiden ajoittuminen -raportti. Näyttää eri urakoissa tapahtuvien toimenpiteiden jakauman
  eri kellonaikoina."
  (:require [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpideajat.sql")

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakoittain?] :as parametrit}]
  [:raportti {:otsikko "Toimenpiteiden ajoittuminen"
              :orientaatio :landscape}
   [:taulukko {:otsikko "Toimenpiteiden ajoittuminen"
               :rivi-ennen (concat
                            [{:teksti "Hoitoluokka" :sarakkeita 1}]
                            (map (fn [luokka]
                                   {:teksti luokka :sarakkeita 6 :keskita? true})
                                 yleinen/talvihoitoluokat)
                            [{:teksti "" :sarakkeita 1}])}
    (into []
          (concat
           (when urakoittain?
             [{:otsikko "Urakka" :leveys "10%"}])

           [{:otsikko "Hoitoluokka"}]

           (mapcat (fn [luokka]
                     [{:otsikko "< 6" :keskita? true}
                      {:otsikko "6 - 10" :keskita? true}
                      {:otsikko "10 - 14" :keskita? true}
                      {:otsikko "14 - 18" :keskita? true}
                      {:otsikko "18 - 22" :keskita? true}
                      {:otsikko "22 - 02" :keskita? true}])
                   yleinen/talvihoitoluokat)

           [{:otsikko "Yhteensä"}]))

    ;; varsinaiset rivit
    []]])
