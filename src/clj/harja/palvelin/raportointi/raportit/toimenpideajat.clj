(ns harja.palvelin.raportointi.raportit.toimenpideajat
  "Toimenpiteiden ajoittuminen -raportti. Näyttää eri urakoissa tapahtuvien toimenpiteiden jakauman
  eri kellonaikoina."
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.hoitoluokat :as hoitoluokat]))

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

(defn- toimenpideajat-urakalle [toimenpideajat urakka]
  (reduce-kv (fn [m [tehtava ur] v]
               (if (= urakka ur)
                 (assoc m tehtava v)
                 m)) {} toimenpideajat))

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokan-numero-set urakka-id
                               hallintayksikko-id urakoittain? urakkatyyppi] :as parametrit}]
  (let [hoitoluokan-numero-set (or hoitoluokan-numero-set
                                   ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                                   (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        parametrit {:urakka          urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm         alkupvm
                    :loppupvm        loppupvm
                    :hoitoluokat     hoitoluokan-numero-set
                    :urakkatyyppi    (name urakkatyyppi)}
        toimenpideajat (hae-toimenpideajat-luokiteltuna db parametrit urakoittain?)
        tehtava-leveys 12
        yhteensa-leveys 5]
    (into
      [:raportti {:nimi        "Toimenpiteiden ajoittuminen"
                  :orientaatio :landscape}]
      (for [urakka (if urakoittain?
                     (distinct (keep second (keys toimenpideajat)))
                     [:kaikki])
            :let [otsikko (str "Toimenpiteiden ajoittuminen"
                               (when-not (= :kaikki urakka)
                                 (str ": " urakka)))
                  toimenpideajat (if (= :kaikki urakka)
                                   toimenpideajat
                                   (toimenpideajat-urakalle toimenpideajat urakka))
                  talvihoitoluokattomia-toimenpiteita?
                  (some
                    (fn [[tehtava lkm-tiedot]]
                      (some (comp nil? :luokka) lkm-tiedot))
                    toimenpideajat)
                  talvihoitoluokat (cond->>
                                     (hoitoluokat/haluttujen-hoitoluokkien-nimet-ja-numerot hoitoluokan-numero-set)

                                     (not talvihoitoluokattomia-toimenpiteita?)
                                     (remove (comp nil? :numero)))
                  aika-leveys (/ (- 100 tehtava-leveys yhteensa-leveys)
                                 (* 6 (count talvihoitoluokat)))]]

        [:taulukko {:otsikko    otsikko
                    :rivi-ennen (concat
                                  [{:teksti "Hoi\u00ADto\u00ADluok\u00ADka" :sarakkeita 1}]
                                  (map (fn [{nimi :nimi}]
                                         {:teksti nimi :sarakkeita 6 :tasaa :keskita})
                                       talvihoitoluokat)
                                  [{:teksti "" :sarakkeita 1}])}

         (into []
               (concat

                 [{:otsikko "Teh\u00ADtä\u00ADvä" :leveys tehtava-leveys}]

                 (mapcat (fn [_]
                           [{:otsikko "< 6" :tasaa :keskita :reunus :vasen :leveys aika-leveys}
                            {:otsikko "6 - 10" :tasaa :keskita :reunus :ei :leveys aika-leveys}
                            {:otsikko "10 - 14" :tasaa :keskita :reunus :ei :leveys aika-leveys}
                            {:otsikko "14 - 18" :tasaa :keskita :reunus :ei :leveys aika-leveys}
                            {:otsikko "18 - 22" :tasaa :keskita :reunus :ei :leveys aika-leveys}
                            {:otsikko "22 - 02" :tasaa :keskita :reunus :oikea :leveys aika-leveys}])
                         talvihoitoluokat)

                 [{:otsikko "Yht." :tasaa :oikea :leveys yhteensa-leveys}]))

         ;; varsinaiset rivit
         (vec
           (for [[tehtava rivit] (sort-by first toimenpideajat)
                 :let [ajat-luokan-mukaan (->> rivit
                                               (group-by :luokka)
                                               (fmap (partial group-by :jarjestys)))]]
             (concat [tehtava]
                     (mapcat (fn [hoitoluokka]
                               (let [ajat (get ajat-luokan-mukaan (:numero hoitoluokka))]
                                 (for [aika (range 6)
                                       :let [rivit (get ajat aika)]]
                                   (reduce + 0 (keep :lkm rivit)))))
                             talvihoitoluokat)
                     [(reduce + (keep :lkm rivit))])))]))))
