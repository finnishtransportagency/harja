(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot
  (:require [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn- yhdista-suunnittelurivit-hoitokausiksi
  "Ottaa vectorin hoitokausien syksyn ja kevään osuutta kuvaavia rivejä.
  Yhdistää syksy-kevät parit yhdeksi riviksi, joka kuvaa kokonaista hoitokautta.
  Palauttaa ainoastaan ne rivit, jotka voitiin yhdistää."
  [suunnittelurivit]
  (let [syksyrivi? (fn [rivi]
                     (and (= (t/month (c/from-sql-date (:alkupvm rivi))) 9)
                          (= (t/day (c/from-sql-date (:alkupvm rivi))) 30)))
        syksyrivit (filter syksyrivi? suunnittelurivit)
        syksya-vastaava-kevatrivi (fn [syksyrivi]
                                    (first (filter
                                             (fn [suunnittelurivi]
                                               (and (= (t/day (c/from-sql-date (:alkupvm suunnittelurivi))) 31)
                                                    (= (t/month (c/from-sql-date (:alkupvm suunnittelurivi))) 12)
                                                    (= (t/year (c/from-sql-date (:alkupvm suunnittelurivi)))
                                                       (t/year (c/from-sql-date (:alkupvm syksyrivi))))
                                                    (= (:tehtava syksyrivi) (:tehtava suunnittelurivi))))
                                             suunnittelurivit)))]
    (keep (fn [syksyrivi]
            (let [kevatrivi (syksya-vastaava-kevatrivi syksyrivi)]
              (when kevatrivi
                (-> syksyrivi
                    (assoc :loppupvm (:loppupvm kevatrivi))
                    (assoc :maara (+ (or (:maara syksyrivi)
                                         0)
                                     (or (:maara kevatrivi)
                                         0)))))))
          syksyrivit)))

(defn suunnitellut-tehtavat [db urakka-id]
  (yhdista-suunnittelurivit-hoitokausiksi
    (q/listaa-urakan-yksikkohintaiset-tyot db {:urakka urakka-id})))

(defn liita-toteumiin-suunnittelutiedot
  "Ottaa aikavälin, urakan toteumat ja suunnittelutiedot.
   Aikavälin tulee olla osa jotain hoitokautta tai kokonainen hoitokausi.
   Liittää toteumiin niiden suunnittelutiedot aikaväliin osuvalta hoitokaudelta, jos sellainen löytyy."
  [alkupvm loppupvm toteumat hoitokaudet]
  (map
    (fn [toteuma]
      (let [suunnittelutieto (first (filter
                                      (fn [hoitokausi]
                                        (let [alkupvm (pvm/paivan-alussa alkupvm)
                                              loppupvm (pvm/paivan-alussa loppupvm)
                                              hoitokausi-alku (pvm/paivan-alussa (:alkupvm hoitokausi))
                                              hoitokausi-loppu (pvm/paivan-alussa (:loppupvm hoitokausi))]
                                          (and (pvm/valissa?
                                                (c/from-date alkupvm)
                                                (c/from-sql-date hoitokausi-alku)
                                                (c/from-sql-date hoitokausi-loppu))
                                              (pvm/valissa?
                                                (c/from-date loppupvm)
                                                (c/from-sql-date hoitokausi-alku)
                                                (c/from-sql-date hoitokausi-loppu))
                                              (= (:tehtava hoitokausi) (:tehtava_id toteuma)))))
                                      hoitokaudet))]
        (if suunnittelutieto
          (-> toteuma
              (assoc :yksikko (:yksikko suunnittelutieto))
              (assoc :yksikkohinta (:yksikkohinta suunnittelutieto))
              (assoc :suunniteltu_maara (:maara suunnittelutieto))
              (assoc :suunnitellut_kustannukset (when (and (:maara suunnittelutieto) (:yksikkohinta suunnittelutieto))
                                                  (* (:maara suunnittelutieto)
                                                     (:yksikkohinta suunnittelutieto))))
              (assoc :toteutuneet_kustannukset (when (and (:toteutunut_maara toteuma) (:yksikkohinta suunnittelutieto))
                                                 (* (:toteutunut_maara toteuma)
                                                    (:yksikkohinta suunnittelutieto)))))
          toteuma)))
    toteumat))

(defn aikavali-hoitokaudella? [alkupvm loppupvm hoitokaudet]
  (some
    (fn [hoitokausi]
      (and (pvm/valissa?
             (c/from-date alkupvm)
             (c/from-sql-date (:alkupvm hoitokausi))
             (c/from-sql-date (:loppupvm hoitokausi)))
           (pvm/valissa?
             (c/from-date loppupvm)
             (c/from-sql-date (:alkupvm hoitokausi))
             (c/from-sql-date (:loppupvm hoitokausi)))))
    hoitokaudet))


(defn suunnitelutietojen-nayttamisilmoitus [konteksti alkupvm loppupvm hoitokaudet]
  (when (and (not (aikavali-hoitokaudella? alkupvm loppupvm hoitokaudet))
             (= konteksti :urakka))
    [:teksti "Suunnittelutiedot näytetään vain haettaessa urakan tiedot hoitokaudelta tai sen osalta."]))
