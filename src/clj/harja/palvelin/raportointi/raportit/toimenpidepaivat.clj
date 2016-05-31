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

(defn hae-toimenpiteet-kokoislukumaarat [db parametrit urakoittain?]
  (->> parametrit
       (hae-toimenpidepaivien-lukumaarat db)
       (group-by (if urakoittain? (juxt :nimi :urakka) :nimi))))

(defn- toimenpidepaivat-urakalle [toimenpideajat urakka]
  (reduce-kv (fn [m [tehtava ur] v]
               (if (= urakka ur)
                 (assoc m tehtava v)
                 m)) {} toimenpideajat))

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakoittain?]}]
  (let [hoitoluokat (or hoitoluokat
                        ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                        (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        parametrit {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm
                    :hoitoluokat hoitoluokat}
        toimenpidepaivat (hae-toimenpiteet-kokoislukumaarat db parametrit urakoittain?)
        talvihoitoluokat (filter #(hoitoluokat (:numero %)) hoitoluokat/talvihoitoluokat)
        tehtava-leveys 10
        yhteensa-leveys 10
        paivia-aikavalilla (pvm/aikavali-paivina alkupvm loppupvm)]
    [:raportti {:nimi "Monenako päivänä toimenpidettä on tehty aikavälillä"
                :orientaatio :landscape}
     (for [urakka (if urakoittain?
                    (distinct (keep second (keys toimenpidepaivat)))
                    [:kaikki])
           :let [otsikko (str "Toimenpidepäivät aikavälillä "
                              (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)
                              (when-not (= :kaikki urakka)
                                (str ": " urakka)))
                 toimenpidepaivat (if (= :kaikki urakka)
                                  toimenpidepaivat
                                  (toimenpidepaivat-urakalle toimenpidepaivat urakka))]]

       [:taulukko {:otsikko otsikko}

        (into []
              (concat
                [{:otsikko "Teh\u00ADtä\u00ADvä"}]
                (mapv (fn [{nimi :nimi}] {:otsikko nimi :tasaa :oikea}) talvihoitoluokat)
                [{:otsikko "Lkm" :tasaa :oikea}]))

        ;; varsinaiset rivit
        (vec (for [[tehtava rivit] (sort-by first toimenpidepaivat)
                   :let [luokittain (group-by :luokka rivit)]]
               (concat [tehtava]
                       (mapv (fn [hoitoluokka]
                               (let [hoitaluokan-tapahtumat (get luokittain (:numero hoitoluokka))]
                                 (str (reduce + 0 (keep :lkm hoitaluokan-tapahtumat)) "/" paivia-aikavalilla)))
                             talvihoitoluokat)
                       [(str (reduce + 0 (keep :lkm rivit)) "/" paivia-aikavalilla)])))])]))