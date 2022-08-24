(ns harja.palvelin.raportointi.raportit.toimenpidepaivat
  "Toimenpidepäivätraportti. Näyttää summan, monenako päivänä toimenpidettä on tehty
  valitulla aikavälillä. Jaotellaan hoitoluokittain"
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.hoitoluokat :as hoitoluokat-domain]))

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
                                   (into #{} (map :tpk) rivit)})))
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
                           (group-by (comp toimenpidekoodit :tpk))
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
                        (into #{} (map :numero) hoitoluokat-domain/talvihoitoluokat))
        parametrit {:urakka          urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alku            alkupvm
                    :loppu           loppupvm
                    :hoitoluokat     hoitoluokat
                    :urakkatyyppi    (if (= urakkatyyppi :hoito)
                                       #{"hoito" "teiden-hoito"}
                                       #{(name urakkatyyppi)})}
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        {:keys [alueet toimenpiderivit]} (hae-toimenpiderivit db konteksti parametrit)
        talvihoitoluokattomia-toimenpiteita? (some
                                               (fn [[toimenpide aluemaarat]]
                                                 (some
                                                   (fn [[urakka-alue hoitoluokka-ja-lkm]]
                                                     (some nil? (keys hoitoluokka-ja-lkm)))
                                                   aluemaarat))
                                               toimenpiderivit)
        talvihoitoluokat (cond->>
                           (hoitoluokat-domain/haluttujen-hoitoluokkien-nimet-ja-numerot hoitoluokat)

                           (not talvihoitoluokattomia-toimenpiteita?)
                           (remove (comp nil? :numero)))
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
                         #(mapv (fn [{nimi :nimi}] {:otsikko nimi :tasaa :oikea :fmt :kokonaisluku})
                                talvihoitoluokat))))))
      (map (fn [[toimenpide aluemaarat]]
             (into [toimenpide]
                   (mapcat (fn [alue]
                             (let [maara (get aluemaarat alue {})]
                               (for [thl talvihoitoluokat]
                                 ;; Koska tuntemattomalla talvihoitoluokalla ei ole numeroa,
                                 ;; osataan sellaisten toteumien, joilla talvihoitoluokkaa ei ole,
                                 ;; liittää "tuntematon talvihoitoluokka" sarakkeen alle.
                                 (get maara (:numero thl) 0)))))
                   alueet))
           toimenpiderivit)]]))
