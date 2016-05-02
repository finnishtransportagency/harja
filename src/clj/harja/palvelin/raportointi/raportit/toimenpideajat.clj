(ns harja.palvelin.raportointi.raportit.toimenpideajat
  "Toimenpiteiden ajoittuminen -raportti. Näyttää eri urakoissa tapahtuvien toimenpiteiden jakauman
  eri kellonaikoina."
  (:require [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [taoensso.timbre :as log]
            [harja.tyokalut.functor :refer [fmap]]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpideajat.sql")

(defn- tunnin-jarjestys
  "Antaa kellonajan tunnille (esim. 14) taulukkojärjestyksen."
  [tunti]
  (case tunti
    (2 3 4 5)     0   ;; ennen 6
    (6 7 8 9)     1   ;; 6 - 10
    (10 11 12 13) 2   ;; 10 - 14
    (14 15 16 17) 3   ;; 14 - 18
    (18 19 20 21) 4   ;; 18 - 21
    (22 23 0 1)   5)) ;; 22 - 02

(defn hae-toimenpideajat-luokiteltuna [db parametrit urakoittain?]
  (->> parametrit
       (hae-toimenpideajat db)
       (group-by (if urakoittain? (juxt :nimi :urakka) :nimi))
       (fmap #(fmap (fn [rivi]
                      (assoc rivi :jarjestys (tunnin-jarjestys (:tunti rivi)))) %))))

#_(take 1 (hae-toimenpideajat-luokiteltuna (:db harja.palvelin.main/harja-jarjestelma)
                                       {:urakka nil :hallintayksikko 9
                                        :alkupvm (pvm/luo-pvm 2015 8 30)
                                        :loppupvm (pvm/luo-pvm 2016 9 1)}
                                       false))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakoittain?] :as parametrit}]
  (let [parametrit {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm}
        toimenpideajat (hae-toimenpideajat-luokiteltuna db parametrit urakoittain?)]
    (log/info "TOIMENPIDEAJAT: " toimenpideajat)
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

             [{:otsikko "Tehtävä" :leveys "18%"}]

             (mapcat (fn [luokka]
                       [{:otsikko "< 6" :tasaa :keskita :reunus :vasen :leveys "1.5%"}
                        {:otsikko "6 - 10" :tasaa :keskita  :reunus :ei :leveys "1.5%"}
                        {:otsikko "10 - 14" :tasaa :keskita :reunus :ei :leveys "1.5%"}
                        {:otsikko "14 - 18" :tasaa :keskita :reunus :ei :leveys "1.5%"}
                        {:otsikko "18 - 22" :tasaa :keskita :reunus :ei :leveys "1.5%"}
                        {:otsikko "22 - 02" :tasaa :keskita :reunus :oikea :leveys "1.5%"}])
                     yleinen/talvihoitoluokat)

             [{:otsikko "Yht" :leveys "10%"}]))

      ;; varsinaiset rivit
      (vec
       (for [[tehtava rivit] toimenpideajat
             :let [ajat-luokan-mukaan (->> rivit
                                             (group-by :luokka)
                                             (fmap (partial group-by :jarjestys)))]]
         (concat [tehtava]
                 (mapcat (fn [hoitoluokka]
                           (let [ajat (get ajat-luokan-mukaan hoitoluokka)]
                             (for [aika (range 6)
                                   :let [rivit (get ajat aika)]]
                               (reduce + 0 (keep :lkm rivit)))))
                         yleinen/talvihoitoluokat-numerot)
                 [(reduce + (keep :lkm rivit))])))]]))
