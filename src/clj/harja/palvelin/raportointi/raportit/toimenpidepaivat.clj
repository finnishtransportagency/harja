(ns harja.palvelin.raportointi.raportit.toimenpidepaivat
  "Toimenpidepäivätraportti. Näyttää summan, monenako päivänä toimenpidettä on tehty
  valitulla aikavälillä. Jaotellaan hoitoluokittain"
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpideajat.sql")


(defn hae-toimenpiderivit [db parametrit]
  (println (pr-str parametrit))
  (let [alue (if (:hallintayksikko parametrit)
               :urakka
               :hallintayksikko)
        rivit (hae-toimenpidepaivien-lukumaarat db parametrit)
        toimenpidekoodit (into {}
                               (map (juxt :id :nimi))
                               (yleinen/hae-toimenpidekoodien-nimet
                                db
                                {:toimenpidekoodi
                                 (into #{} (map :toimenpidekoodi) rivit)}))
        urakat (into {}
                     (map (juxt :id :nimi))
                     (yleinen/hae-urakoiden-nimet db
                                                  {:urakka (into #{} (map :urakka) rivit)}))
        hallintayksikot (into {}
                              (map (juxt :id :nimi))
                              (yleinen/hae-organisaatioiden-nimet
                               db
                               {:organisaatio (into #{} (map :hallintayksikko) rivit)}))
        alue-nimi (if (= alue :urakka)
                    urakat
                    hallintayksikot)]

    {:alueet (sort (vals (if (= alue :urakka) urakat hallintayksikot)))
     :toimenpiderivit (->> rivit
                           (group-by (comp toimenpidekoodit :toimenpidekoodi))
                           (fmap (fn [toimenpiderivit]
                                   ;; Lasketaan transienteilla jokaiselle alueelle
                                   ;; mäppi {hoitoluokka pvm-määrä}
                                   (loop [alueet (transient {})
                                          [rivi & rivit] toimenpiderivit]
                                     (if-not rivi
                                       ;; Muunnetaan {hoitoluokka #{pvm1 pvm2 ... pvmN}} arvo
                                       ;; uniikkien päivämäärien lukumääräksi
                                       (fmap #(fmap count %) (persistent! alueet))

                                       ;; Päivitetään alueen ja luokan mukaan päivämäärä
                                       ;; oikeaan settiin
                                       (let [nimi (alue-nimi (alue rivi))
                                             luokka (:luokka rivi)
                                             aluemaarat (get alueet nimi {})
                                             pvmt (get aluemaarat luokka #{})]
                                         (recur (assoc! alueet
                                                        nimi (assoc aluemaarat luokka
                                                                    (conj pvmt (:pvm rivi))))
                                                rivit))))))
                           (sort-by first))
     :toimenpidekoodit toimenpidekoodit
     :urakat urakat
     :hallintayksikot hallintayksikot}))

(defn alueen-hoitoluokkasarakkeet [alue hoitoluokat tpi-nimi toimenpiteet]
  (let [hoitoluokkasummat
        (mapv
          (fn [hoitoluokka]
            (let [sopivat-rivit (filter
                                  (fn [tpi]
                                    (and (= (:nimi tpi) tpi-nimi)
                                         (= (:luokka tpi) (:numero hoitoluokka))
                                         (or (= (:urakka-id tpi) (:urakka-id alue))
                                             (= (:hallintayksikko-id tpi) (:hallintayksikko-id alue)))))
                                  toimenpiteet)
                  tulos (reduce + 0 (map :lkm sopivat-rivit))]
              tulos))
          hoitoluokat)]
    (map #(fmt/desimaaliluku-opt % 1) hoitoluokkasummat)))

(defn aluesarakkeet [alueet hoitoluokat tpi-nimi toimenpiteet]
  (conj
    (vec (mapcat
           (fn [alue]
             (alueen-hoitoluokkasarakkeet alue hoitoluokat tpi-nimi toimenpiteet))
           alueet))))

(defn muodosta-datarivit [alueet hoitoluokat toimenpiteet]
  (let [toimenpiteiden-nimet (set (map :nimi toimenpiteet))]
    (mapv
      (fn [tpi-nimi]
        (concat
          [tpi-nimi]
          (aluesarakkeet alueet hoitoluokat tpi-nimi toimenpiteet)))
      toimenpiteiden-nimet)))

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakkatyyppi]}]
  (let [hoitoluokat (or hoitoluokat
                        ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                        (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        talvihoitoluokat (filter #(hoitoluokat (:numero %)) hoitoluokat/talvihoitoluokat)
        parametrit {:urakka          urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm         alkupvm
                    :loppupvm        loppupvm
                    :hoitoluokat     hoitoluokat
                    :urakkatyyppi    (name urakkatyyppi)}
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        {:keys [alueet toimenpiderivit]} (hae-toimenpiderivit db parametrit)
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
                         #(mapv (fn [{nimi :nimi}] {:otsikko nimi :tasaa :oikea})
                                talvihoitoluokat))))))
      (map (fn [[toimenpide aluemaarat]]
             (into [toimenpide]
                   (mapcat (fn [alue]
                             (let [maara (get aluemaarat alue {})]
                               (for [thl talvihoitoluokat]
                                 (get maara (:numero thl) 0)))))
                   alueet))
           toimenpiderivit)]]))
