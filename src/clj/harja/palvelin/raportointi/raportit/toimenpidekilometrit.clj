(ns harja.palvelin.raportointi.raportit.toimenpidekilometrit
  "Toimenpidekilometrit-raportti. Näyttää kuinka paljon kutakin kok. hint. työtä on tehty eri urakoissa."
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.hoitoluokat :as hoitoluokat-domain]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpidekilometrit.sql")

(defn alueen-hoitoluokkasarakkeet [alue hoitoluokat tehtava toteumat]
  (mapv
    (fn [hoitoluokka]
      (let [sopivat-rivit (filter
                            (fn [toteuma]
                              (and (= (:toimenpidekoodi-nimi toteuma) tehtava)
                                   (= (:hoitoluokka toteuma) hoitoluokka)
                                   (or (= (:urakka toteuma) (:urakka-id alue))
                                       (= (:hallintayksikko toteuma) (:hallintayksikko-id alue)))))
                            toteumat)
            tulos (reduce + 0 (keep :maara sopivat-rivit))]
        tulos))
    hoitoluokat))

(defn aluesarakkeet [alueet hoitoluokat tehtava toteumat]
  (mapcat
    (fn [alue]
      (alueen-hoitoluokkasarakkeet alue hoitoluokat tehtava toteumat))
    alueet))

(defn muodosta-datarivit [alueet hoitoluokat toteumat]
  (let [tehtava-nimet (into #{} (distinct (map (juxt :toimenpidekoodi-nimi :toimenpidekoodi-yksikko) toteumat)))]
    (mapv
      (fn [[tehtava yksikko]]
        (concat
          [(str tehtava " (" yksikko ")")]
          (aluesarakkeet alueet hoitoluokat tehtava toteumat)))
      tehtava-nimet)))

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        hoitoluokat (apply sorted-set
                           (or hoitoluokat
                               ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                               (into #{} (map :numero) hoitoluokat-domain/talvihoitoluokat)))
        talvihoitoluokat (filter #(hoitoluokat (:numero %)) hoitoluokat-domain/talvihoitoluokat)
        naytettavat-alueet (yleinen/naytettavat-alueet db konteksti
                                                       {:urakka urakka-id
                                                        :hallintayksikko hallintayksikko-id
                                                        :urakkatyyppi #{"hoito" "teiden-hoito"}
                                                        :alku alkupvm
                                                        :loppu loppupvm})
        toteumat (hae-kokonaishintaiset-toteumat db {:urakka urakka-id
                                                     :hallintayksikko hallintayksikko-id
                                                     :urakkatyyppi #{"hoito" "teiden-hoito"}
                                                     :alku alkupvm
                                                     :loppu loppupvm})
        raportin-nimi "Toimenpidekilometrit"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        otsikkorivit (into [] (concat
                                [{:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}]
                                (mapcat
                                  (fn [_]
                                    (map (fn [{:keys [nimi]}]
                                           {:otsikko nimi :tasaa :keskita :fmt :numero})
                                         talvihoitoluokat))
                                  naytettavat-alueet)))
        datarivit (muodosta-datarivit naytettavat-alueet hoitoluokat toteumat)]
    [:raportti {:nimi "Toimenpidekilometrit"
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? toteumat) "Ei raportoitavia tehtäviä.")
                 :rivi-ennen (concat
                               [{:teksti "Alue" :sarakkeita 1}]
                               (mapv
                                 (fn [{:keys [nimi] :as alue}]
                                   {:teksti nimi
                                    :sarakkeita (count talvihoitoluokat)})
                                 naytettavat-alueet))
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))
