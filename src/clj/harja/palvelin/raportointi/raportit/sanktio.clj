(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.string :as str]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn- rivi-kuuluu-talvihoitoon? [rivi]
  (= (str/lower-case (:toimenpidekoodi_taso2 rivi)) "talvihoito"))

(defn- rivien-urakat [rivit]
  (-> (map (fn [rivi]
             {:id (:urakka_id rivi)
              :nimi (:urakka_nimi rivi)})
           rivit)
      distinct))

(defn- urakan-rivit [rivit urakka-id]
  (filter
    (fn [rivi]
      (= (:urakka_id rivi) urakka-id))
    rivit))

(defn- suodata-sakot [rivit {:keys [urakka-id sakkoryhma talvihoito?] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (sanktiot-domain/sakko? rivi)
        (or (nil? sakkoryhma) (= sakkoryhma (:sakkoryhma rivi)))
        (or (nil? urakka-id) (= urakka-id (:urakka_id rivi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- suodata-muistutukset [rivit {:keys [urakka-id talvihoito?] :as suodattimet}]
  (filter
    (fn [rivi]
      (and
        (not (sanktiot-domain/sakko? rivi))
        (or (nil? urakka-id) (= urakka-id (:urakka_id rivi)))
        (or (nil? talvihoito?) (= talvihoito? (rivi-kuuluu-talvihoitoon? rivi)))))
    rivit))

(defn- sakkojen-summa
  ([rivit] (sakkojen-summa rivit {}))
  ([rivit suodattimet]
   (let [laskettavat (suodata-sakot rivit suodattimet)]
     (reduce + (map
                 #(or (:summa %) 0)
                 laskettavat)))))

(defn muistutusten-maara
  ([rivit] (muistutusten-maara rivit {}))
  ([rivit suodattimet]
   (count (suodata-muistutukset rivit suodattimet))))

(defn- luo-rivi-sakkojen-summa
  ([otsikko rivit]
   (luo-rivi-sakkojen-summa otsikko rivit {}))
  ([otsikko rivit suodattimet]
   (apply conj [otsikko "€"] (mapv (fn [urakka]
                                     (sakkojen-summa rivit (merge suodattimet
                                                                  {:urakka-id (:id urakka)})))
                                   (rivien-urakat rivit)))))

(defn- luo-rivi-muistutusten-maara
  ([otsikko rivit]
   (luo-rivi-muistutusten-maara otsikko rivit {}))
  ([otsikko rivit suodattimet]
   (apply conj [otsikko "kpl"] (mapv (fn [urakka]
                                     (muistutusten-maara rivit (merge suodattimet
                                                                  {:urakka-id (:id urakka)})))
                                   (rivien-urakat rivit)))))

(defn- raporttirivit-talvihoito [rivit]
  [{:otsikko "Talvihoito"}
   (luo-rivi-muistutusten-maara "Muistutukset" rivit {:talvihoito? true})
   (luo-rivi-sakkojen-summa "Sakko A" rivit {:sakkoryhma :A :talvihoito? true})
   ["- Päätiet" "€" "?"]                                    ; TODO
   ["- Muut tiet" "€" "?"]                                  ; TODO
   (luo-rivi-sakkojen-summa "Sakko B" rivit {:sakkoryhma :B :talvihoito? true})
   ["- Päätiet" "€" "?"]                                    ; TODO
   ["- Muut tiet" "€" "?"]                                  ; TODO
   (luo-rivi-sakkojen-summa "Talvihoito, sakot yht." rivit {:talvihoito? true})
   ["- Talvihoito, indeksit yht." "€" "?"]])             ; TODO

(defn- raporttirivit-muut-tuotteet [rivit]
  [{:otsikko "Muut tuotteet"}
   (luo-rivi-muistutusten-maara "Muistutukset" rivit {:talvihoito? false})
   (luo-rivi-sakkojen-summa "Sakko A" rivit {:sakkoryhma :A :talvihoito? false})
   ["- Liikenneymp. hoito" "€" "?"]                         ; TODO
   ["- Sorateiden hoito" "€" "?"]                           ; TODO
   (luo-rivi-sakkojen-summa "Sakko B" rivit {:sakkoryhma :B :talvihoito? false})
   ["- Liikenneymp. hoito" "€" "?"]                         ; TODO
   ["- Sorateiden hoito" "€" "?"]                           ; TODO
   (luo-rivi-sakkojen-summa "Muut tuotteet, sakot yht." rivit {:talvihoito? false})
   ["- Muut tuotteet, indeksit yht." "€" "?"]])             ; TODO

(defn- raporttirivit-ryhma-c [rivit]
  [{:otsikko "Ryhmä C"}
   (luo-rivi-sakkojen-summa "Ryhmä C, sakot yht." rivit {:sakkoryhma :C})
   ["Ryhmä C, indeksit yht." "€" "?"]])                     ; TODO

(defn- raporttirivit-yhteensa [rivit]
  [{:otsikko "Yhteensä"}
   (luo-rivi-muistutusten-maara "Muistutukset yht." rivit)
   ["Indeksit yht." "€" "?"]                                ; TODO
   (luo-rivi-sakkojen-summa "Kaikki sakot yht." rivit)
   ["Kaikki yht." "€" "?"]])

(defn- raporttirivit [rivit]
  (concat
    (raporttirivit-talvihoito rivit)
    (raporttirivit-muut-tuotteet rivit)
    (raporttirivit-ryhma-c rivit)
    (raporttirivit-yhteensa rivit)))

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kantarivit (into []
                         (comp
                           (map #(konv/string->keyword % :sakkoryhma))
                           (map #(konv/array->set % :sanktiotyyppi_laji keyword)))
                         (hae-sanktiot db
                                       {:urakka urakka-id
                                        :hallintayksikko hallintayksikko-id
                                        :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                        :alku alkupvm
                                        :loppu loppupvm}))
        raportin-otsikot (apply conj
                                [{:otsikko "" :leveys 10}
                                 {:otsikko "Yks." :leveys 3}]
                                (mapv
                                  (fn [urakka]
                                    {:otsikko (:nimi urakka) :leveys 20})
                                  (rivien-urakat kantarivit)))
        raportin-rivit (raporttirivit kantarivit)
        raportin-nimi "Sanktioraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                     db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
      raportin-otsikot
      raportin-rivit]]))
