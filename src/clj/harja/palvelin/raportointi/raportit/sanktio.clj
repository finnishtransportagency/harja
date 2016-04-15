(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.string :as str]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn talvihoito? [kantarivi]
  (= (str/lower-case (:toimenpidekoodi_taso2 kantarivi)) "talvihoito"))

(defn talvihoidon-muistutukset [kantarivit urakka-id]
  (let [laskettavat (filter
     (fn [rivi]
       (and (talvihoito? rivi)
            (#{:muistutus} (:sanktiotyyppi_laji rivi))
            (= (:urakka_id rivi) urakka-id)))
     kantarivit)]
    (count laskettavat)))

(defn rivien-sisaltamat-urakat [rivit]
  (-> (map (fn [rivi]
             {:id (:urakka_id rivi)
              :nimi (:urakka_nimi rivi)})
           rivit)
      distinct))

(defn sanktiot-raportille [kantarivit]
  [{:otsikko "Talvihoito"}
   (apply conj ["Muistutukset" "kpl"] (mapv (fn [urakka]
                                 (talvihoidon-muistutukset kantarivit (:id urakka)))
                                            (rivien-sisaltamat-urakat kantarivit)))
   ["Sakko A" "€" 0]
   ["- Päätiet" "€" 0]
   ["- Muut tiet" "€" 0]
   ["Sakko B" "€" 0]
   ["- Päätiet" "€" 0]
   ["- Muut tiet" "€" 0]
   ["- Talvihoito, sakot yht." "€" 0]
   ["- Talvihoito, indeksit yht." "€" 0]
   {:otsikko "Muut tuotteet"}
   ["Muistutukset" "kpl" 0]
   ["Sakko A" "€" 0]
   ["- Liikenneymp. hoito" "€" 0]
   ["- Sorateiden hoito" "€" 0]
   ["Sakko B" "€" 0]
   ["- Liikenneymp. hoito" "€" 0]
   ["- Sorateiden hoito" "€" 0]
   ["- Muut tuotteet, sakot yht." "€" 0]
   ["- Muut tuotteet, indeksit yht." "€" 0]
   {:otsikko "Ryhmä C"}
   ["Ryhmä C, sakot yht." "€" 0]
   ["Ryhmä C, indeksit yht." "€" 0]
   {:otsikko "Yhteensä"}
   ["Muistutukset yht." "kpl" 0]
   ["Indeksit yht." "€" 0]
   ["Kaikki sakot yht." "€" 0]
   ["Kaikki yht." "€" 0]])

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kantarivit (into []
                         (map #(konv/array->set % :sanktiotyyppi_laji keyword))
                         (hae-sanktiot db
                                       {:urakka urakka-id
                                        :hallintayksikko hallintayksikko-id
                                        :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                        :alku alkupvm
                                        :loppu loppupvm}))
        raportin-otsikot (apply conj
                                [{:otsikko "" :leveys 20}
                                 {:otsikko "Yks." :leveys 3}]
                                (mapv
                                  (fn [urakka]
                                    {:otsikko (:nimi urakka) :leveys 20})
                                  (rivien-sisaltamat-urakat kantarivit)))
        raporttidata (sanktiot-raportille kantarivit)
        raportin-nimi "Sanktioraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
      raportin-otsikot
      raporttidata]]))
