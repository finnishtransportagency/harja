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

(defn hae-toimenpiteet-kokoislukumaarat [db parametrit]
  (->> parametrit
       (hae-toimenpidepaivien-lukumaarat db)))

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
                  _ (log/debug "Sopivat rivit: " (pr-str sopivat-rivit))
                  tulos (reduce + 0 (map :lkm sopivat-rivit))]
              tulos))
          hoitoluokat)]
    (map #(fmt/desimaaliluku-opt % 1) (conj hoitoluokkasummat (reduce + hoitoluokkasummat)))))

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
                    :hoitoluokat     hoitoluokat}
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-alueet (yleinen/naytettavat-alueet
                             db
                             konteksti
                             {:urakka          urakka-id
                              :hallintayksikko hallintayksikko-id
                              :urakkatyyppi    (when urakkatyyppi (name urakkatyyppi))
                              :alku            alkupvm
                              :loppu           loppupvm})
        toimenpidepaivat (hae-toimenpiteet-kokoislukumaarat db parametrit)
        paivia-aikavalilla (pvm/aikavali-paivina alkupvm loppupvm)
        datarivit (muodosta-datarivit naytettavat-alueet talvihoitoluokat toimenpidepaivat)]
    [:raportti {:nimi        "Monenako päivänä toimenpidettä on tehty aikavälillä"
                :orientaatio :landscape}

     [:taulukko {:otsikko    (str "Toimenpidepäivät aikavälillä "
                                  (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) " (" paivia-aikavalilla " päivää)")
                 :rivi-ennen (concat [{:teksti "Alueet" :sarakkeita 1}]
                                     (map
                                       (fn [alue]
                                         {:teksti (:nimi alue) :sarakkeita (+ 1 (count talvihoitoluokat))})
                                       naytettavat-alueet))}

      (into []
            (concat
              [{:otsikko "Teh\u00ADtä\u00ADvä"}]
              (flatten (conj
                         (repeatedly
                           ;; Jokaiselle alueelle..
                           (count naytettavat-alueet)
                           ;; Tehdään sarakkeet hoitoluokille
                           #(conj
                             (mapv (fn [{nimi :nimi}] {:otsikko nimi :tasaa :oikea}) talvihoitoluokat)
                             {:otsikko "Lkm" :tasaa :oikea}))))))
      datarivit]]))