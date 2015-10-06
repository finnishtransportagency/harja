(ns harja.palvelin.raportointi.raportit.materiaali
  "Materiaaliraportti"
  (:require [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.konversio :as konv]))

(defn muodosta-materiaaliraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteutuneet materiaalit raporttia varten: " urakka-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-materiaalit (into []
                                      (materiaalit-q/hae-urakan-toteutuneet-materiaalit-raportille db
                                                                                                   urakka-id
                                                                                                   (konv/sql-timestamp alkupvm)
                                                                                                   (konv/sql-timestamp loppupvm)))
        suunnitellut-materiaalit (into []
                                       (materiaalit-q/hae-urakan-suunnitellut-materiaalit-raportille db
                                                                                                    urakka-id
                                                                                                    (konv/sql-timestamp alkupvm)
                                                                                                    (konv/sql-timestamp loppupvm)))
        suunnitellut-materiaalit-ilman-toteumia (filter
                                                  (fn [materiaali]
                                                    (not-any?
                                                      (fn [toteuma] (= (:materiaali_nimi toteuma) (:materiaali_nimi materiaali)))
                                                      toteutuneet-materiaalit))
                                                  suunnitellut-materiaalit)
        lopullinen-tulos (mapv
                           (fn [materiaalitoteuma]
                             (if (nil? (:kokonaismaara materiaalitoteuma))
                               (assoc materiaalitoteuma :kokonaismaara 0)
                               materiaalitoteuma))
                           (reduce conj toteutuneet-materiaalit suunnitellut-materiaalit-ilman-toteumia))]
    lopullinen-tulos))

(defn muodosta-materiaaliraportti-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "Haetaan hallintayksikon toteutuneet materiaalit raporttia varten: " hallintayksikko-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-materiaalit (into []
                                      (materiaalit-q/hae-hallintayksikon-toteutuneet-materiaalit-raportille db
                                                                                                            (konv/sql-timestamp alkupvm)
                                                                                                            (konv/sql-timestamp loppupvm)
                                                                                                            hallintayksikko-id))]
    toteutuneet-materiaalit))

(defn muodosta-materiaaliraportti-koko-maalle [db user {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan koko maan toteutuneet materiaalit raporttia varten: " alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [toteutuneet-materiaalit (into []
                                      (materiaalit-q/hae-koko-maan-toteutuneet-materiaalit-raportille db
                                                                                                      (konv/sql-timestamp alkupvm)
                                                                                                      (konv/sql-timestamp loppupvm)))]
    toteutuneet-materiaalit))

(defn muodosta-materiaalisarakkeet
  "Käy läpi materiaalitoteumat ja muodostaa toteumissa esiintyvistä materiaaleista yhden sarakkeen kustakin."
  [materiaalitoteumat]
  (let [materiaalit (distinct (mapv (fn [materiaali]
                                      (select-keys materiaali [:materiaali_nimi :materiaali_nimi_lyhenne]))
                                    materiaalitoteumat))]
    (mapv (fn [materiaali]
            {:otsikko     (:materiaali_nimi materiaali)
             :nimi        (keyword (:materiaali_nimi materiaali))
             :muokattava? (constantly false)
             :tyyppi
                          :string
             :leveys      "33%"})
          materiaalit)))

(defn muodosta-materiaaliraportin-rivit
  "Yhdistää saman urakan materiaalitoteumat yhdeksi grid-komponentin riviksi."
  [materiaalitoteumat]
  (let [materiaali-nimet (distinct (mapv :materiaali_nimi materiaalitoteumat))
        urakka-nimet (distinct (mapv :urakka_nimi materiaalitoteumat))
        urakkarivit (vec (map-indexed (fn [index urakka]
                                        (reduce ; Lisää urakkaan liittyvien materiaalien kokonaismäärät avain-arvo pareina tälle riville
                                          (fn [eka toka]
                                            (assoc eka (keyword (:materiaali_nimi toka)) (:kokonaismaara toka)))
                                          (reduce (fn [eka toka] ; Lähtöarvona rivi, jossa urakan nimi ja kaikki materiaalit nollana
                                                    (assoc eka (keyword toka) 0))
                                                  {:id index :urakka_nimi urakka}
                                                  materiaali-nimet)
                                          (filter
                                            #(= (:urakka_nimi %) urakka)
                                            materiaalitoteumat)))
                                      urakka-nimet))]
    urakkarivit))

(defn muodosta-materiaaliraportin-yhteensa-rivi
  "Palauttaa rivin, jossa eri materiaalien määrät on summattu yhteen"
  [materiaalitoteumat]
  (let [materiaalinimet (distinct (mapv :materiaali_nimi materiaalitoteumat))]
    (reduce (fn [eka toka]
              (assoc eka (keyword toka) (reduce + (mapv
                                                    :kokonaismaara
                                                    (filter
                                                      #(= (:materiaali_nimi %) toka)
                                                      materiaalitoteumat)))))
            {:id -1 :urakka_nimi "Yhteensä" :yhteenveto true}
            materiaalinimet)))


(defn suorita [db user {:keys [urakka-id hk-alku hk-loppu
                               hallintayksikko-id alkupvm loppupvm] :as parametrit}]

  (let [toteumat (cond
                   (and urakka-id hk-alku hk-loppu)
                   (muodosta-materiaaliraportti-urakalle db user {:urakka-id urakka-id
                                                                  :alkupvm hk-alku
                                                                  :loppupvm hk-loppu})

                   (and hallintayksikko-id alkupvm loppupvm)
                   (muodosta-materiaaliraportti-hallintayksikolle db user {:hallintayksikko-id hallintayksikko-id
                                                                           :alkupvm alkupvm
                                                                           :loppupvm loppupvm})
                   
                   (and alkupvm loppupvm)
                   (muodosta-materiaaliraportti-koko-maalle db user {:alkupvm alkupvm :loppupvm loppupvm})

                   :default
                   ;; Pitäisikö tässä heittää jotain, tänne ei pitäisi päästä, jos parametrit ovat oikein?
                   nil)]

    [:raportti {:nimi "Materiaaliraportti"}
     [:taulukko {:otsikko "FIXME: Urakan nimi alkuvuosi-loppuvuosi - Materiaaliraportti hkalku - hkloppu"}
      (muodosta-materiaalisarakkeet toteumat)
      (concat (muodosta-materiaaliraportin-rivit toteumat)
              (muodosta-materiaaliraportin-yhteensa-rivi toteumat))]]))
    
