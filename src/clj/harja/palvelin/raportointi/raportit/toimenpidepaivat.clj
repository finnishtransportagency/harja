(ns harja.palvelin.raportointi.raportit.toimenpidepaivat
  "Toimenpidepäivätraportti. Näyttää summan, monenako päivänä toimenpidettä on tehty
  valitulla aikavälillä. Jaotellaan hoitoluokittain"
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.hoitoluokat :as hoitoluokat]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpideajat.sql")


(defn hae-toimenpiderivit [db konteksti parametrit]
  (let [alue (if (#{:urakka :hallintayksikko} konteksti)
               :urakka
               :hallintayksikko)
        rivit (hae-toimenpidepaivien-lukumaarat db parametrit)
        toimenpidekoodit (if (empty? rivit)
                           {}
                           (into {}
                                 (map (juxt :id :nimi))
                                 (yleinen/hae-toimenpidekoodien-nimet
                                  db
                                  {:toimenpidekoodi
                                   (into #{} (map :toimenpidekoodi) rivit)})))
        naytettavat-alueet (yleinen/naytettavat-alueet db konteksti parametrit)
        alueet (sort (map (if (= alue :urakka)
                            :nimi
                            #(str (:elynumero %) " " (:nimi %)))
                          naytettavat-alueet))
        alue-nimi (into {}
                        (map (if (= alue :urakka)
                               (juxt :urakka-id :nimi)
                               (juxt :hallintayksikko-id #(str (:elynumero %) " " (:nimi %)))))
                        naytettavat-alueet)]
    {:alueet alueet
     :toimenpiderivit (->> rivit
                           (group-by (comp toimenpidekoodit :toimenpidekoodi))
                           (fmap (fn [toimenpiderivit]
                                   (let [rivit-alueen-mukaan (group-by (comp alue-nimi alue) toimenpiderivit)]
                                     (into {}
                                           (map (juxt identity #(into {}
                                                                      (map (juxt :luokka :lkm))
                                                                      (get rivit-alueen-mukaan %))))
                                           alueet))))
                           (sort-by first))}))


(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakkatyyppi]}]
  (let [hoitoluokat (or hoitoluokat
                        ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                        (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        talvihoitoluokat (filter #(hoitoluokat (:numero %)) hoitoluokat/talvihoitoluokat)
        parametrit {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alku alkupvm
                    :loppu loppupvm
                    :hoitoluokat hoitoluokat
                    :urakkatyyppi (name urakkatyyppi)}
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        {:keys [alueet toimenpiderivit]} (hae-toimenpiderivit db konteksti parametrit)
        paivia-aikavalilla (pvm/aikavali-paivina alkupvm loppupvm)]
    [:raportti {:nimi        "Monenako päivänä toimenpidettä on tehty aikavälillä"
                :orientaatio :landscape}

     [:taulukko {:otsikko    (str "Toimenpidepäivät aikavälillä "
                                  (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) " (" paivia-aikavalilla " päivää)")
                 :rivi-ennen (into [{:teksti "Alueet" :sarakkeita 1}]
                                   (map
                                    (fn [alue]
                                      {:teksti alue :sarakkeita (count talvihoitoluokat)}))
                                   alueet)}

      (into []
            (concat
              [{:otsikko "Teh\u00ADtä\u00ADvä"}]
              (flatten (conj
                        (repeatedly
                         ;; Jokaiselle alueelle..
                         (count alueet)
                         ;; Tehdään sarakkeet hoitoluokille
                         #(mapv (fn [{nimi :nimi}] {:otsikko nimi :tasaa :oikea :fmt :numero})
                                talvihoitoluokat))))))
      (map (fn [[toimenpide aluemaarat]]
             (into [toimenpide]
                   (mapcat (fn [alue]
                             (let [maara (get aluemaarat alue {})]
                               (for [thl talvihoitoluokat]
                                 (get maara (:numero thl) 0)))))
                   alueet))
           toimenpiderivit)]]))
