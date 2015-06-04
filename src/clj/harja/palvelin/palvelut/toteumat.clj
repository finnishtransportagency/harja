(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt.toteumat :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]))

(def toteuma-xf
     (comp (map #(-> %
                     (konv/array->vec :tehtavat)
                     (konv/array->vec :materiaalit)))))

(def muunna-desimaaliluvut-xf
     (map #(-> %
               (assoc-in [:maara]
                         (or (some-> % :maara double) 0)))))

(defn urakan-toteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumat: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [rivit (into []
                    toteuma-xf
                    (q/listaa-urakan-toteumat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm)))]
    (map (fn [rivi] (assoc rivi :tehtavat
                                (mapv (fn [tehtava] (let [splitattu (str/split tehtava #"\^")]
                                                      {:tpk-id (Integer/parseInt (first splitattu))
                                                       :nimi (second splitattu)
                                                       :maara (Integer/parseInt (nth splitattu 2))
                                                       }))
                                      (:tehtavat rivi))))
         rivit)))

(defn urakan-tehtavat-toteumittain [db user {:keys [urakka-id sopimus-id toimenpidekoodi alkupvm loppupvm]}]
  (log/debug "Haetaan urakan tehtävät toteumittain: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        muunna-desimaaliluvut-xf
        (q/listaa-urakan-tehtavat-toteumittain db (konv/sql-timestamp alkupvm) (konv/sql-timestamp loppupvm) toimenpidekoodi urakka-id sopimus-id)))

(defn urakan-toteuma-paivat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumapäivän: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into #{}
        (map :paiva)
        (q/hae-urakan-toteuma-paivat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))


(defn hae-urakan-tehtavat [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-tehtavat db urakka-id)))

                          
(defn tallenna-toteuma [db user toteuma]
  (validoi Toteuma toteuma)
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo}
                            (:urakka-id toteuma))
  
  (jdbc/with-db-transaction [c db]
    (let [uusi (q/luo-toteuma<! c (:urakka-id toteuma) (:sopimus-id toteuma)
                                (konv/sql-timestamp (:alkanut toteuma))
                                (konv/sql-timestamp (:paattynyt toteuma))
                                (name (:tyyppi toteuma)))
          id (:id uusi)]
      ;; Luodaan uudelle toteumalle tehtävät ja materiaalit
      (doseq [{:keys [toimenpidekoodi maara]} (:tehtavat toteuma)]
        (q/luo-tehtava<! c id toimenpidekoodi maara))

      (doseq [{:keys [materiaalikoodi maara]} (:materiaalit toteuma)]
        (materiaalit-q/luo-materiaali<! c id materiaalikoodi maara))
      
      true)))


(def erilliskustannus-tyyppi-xf
     (map #(assoc % :tyyppi (keyword (:tyyppi %)))))

(def erilliskustannus-rahasumma-xf
     (map #(if (:rahasumma %)
            (assoc % :rahasumma (double (:rahasumma %)))
            (identity %))))

(def erilliskustannus-xf
     (comp
       erilliskustannus-tyyppi-xf
       erilliskustannus-rahasumma-xf))

(defn hae-urakan-erilliskustannukset [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan erilliskustannukset: " urakka-id " ajalta " alkupvm "-" loppupvm)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
    erilliskustannus-xf
    (q/listaa-urakan-hoitokauden-erilliskustannukset db urakka-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn tallenna-erilliskustannus [db user ek]
  (oik/vaadi-rooli-urakassa user
    #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo}
    (:urakka-id  ek))
  (jdbc/with-db-transaction [c db]
    (if (not (:id ek))
      (q/luo-erilliskustannus<! c (:tyyppi ek) (:sopimus ek) (:toimenpideinstanssi ek)
        (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user))

      (q/paivita-erilliskustannus! c (:tyyppi ek) (:sopimus ek) (:toimenpideinstanssi ek)
        (konv/sql-date (:pvm ek)) (:rahasumma ek) (:indeksin_nimi ek) (:lisatieto ek) (:id user)
        (or (:poistettu ek) false) (:id ek)))

    (hae-urakan-erilliskustannukset c user {:urakka-id (:urakka-id ek)
                                             :alkupvm (:alkupvm ek)
                                             :loppupvm (:loppupvm ek)})))

(defn tallenna-toteuma-ja-toteumamateriaalit
  "Tallentaa toteuman ja toteuma-materiaalin, ja palauttaa lopuksi kaikki urakassa käytetyt materiaalit (yksi rivi per materiaali).
  Tiedon mukana tulee yhteenlaskettu summa materiaalin käytöstä."
  [db user t toteumamateriaalit]
  ;(validoi Toteuma t) ;fixme skeema??
  (oik/vaadi-rooli-urakassa user #{roolit/urakanvalvoja roolit/urakoitsijan-urakan-vastuuhenkilo} ;fixme roolit??
                            (:urakka t))
  (log/info "Tallenna toteuma: "(pr-str t)" ja toteumamateriaalit "(pr-str toteumamateriaalit))
  (jdbc/with-db-transaction [c db]
                            (let [toteuma (if (and (:id t) (pos? (:id t)))
                                            (do
                                              (log/info "Pävitetään toteumaa " (:id t))
                                              (q/paivita-toteuma! c (konv/sql-date (:alkanut t)) (konv/sql-date (:paattynyt t))
                                                                  (:id t) (:urakka t))
                                              t)
                                            (do
                                              (log/info "Luodaan uusi toteuma")
                                              (q/luo-toteuma<! c (:urakka t) (:sopimus t) (konv/sql-date (:alkanut t)) (konv/sql-date (:paattynyt t)) (:tyyppi t))))]
                              (log/info "Toteuman tallentamisen tulos:" (pr-str toteuma))
                              (doall
                                (for [tm toteumamateriaalit]
                                  (if (and (:id tm) (pos? (:id tm)))
                                    (do
                                      (log/info "Päivitä materiaalitoteuma " (:id tm)" ("(:materiaalikoodi tm)", "(:maara tm)"), toteumassa " (:id toteuma))
                                      (materiaalit-q/paivita-toteuma-materiaali! c (:materiaalikoodi tm) (:maara tm) (:toteuma (:id toteuma)) (:id tm)))
                                    (do
                                      (log/info "Luo uusi materiaalitoteuma ("(:materiaalikoodi tm)", "(:maara tm)") toteumalle " (:id toteuma))
                                      (materiaalit-q/luo-toteuma-materiaali<! c (:id toteuma) (:materiaalikoodi tm) (:maara tm))))))
                              (materiaalipalvelut/hae-urakassa-kaytetyt-materiaalit c user (:urakka t)))))

(defrecord Toteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-toteumat
                        (fn [user tiedot]
                          (urakan-toteumat db user tiedot)))
      (julkaise-palvelu http :urakan-tehtavat-toteumittain
                        (fn [user tiedot]
                          (urakan-tehtavat-toteumittain db user tiedot)))
      (julkaise-palvelu http :urakan-toteuma-paivat
                        (fn [user tiedot]
                          (urakan-toteuma-paivat db user tiedot)))
      (julkaise-palvelu http :hae-urakan-tehtavat
                        (fn [user urakka-id]
                          (hae-urakan-tehtavat db user urakka-id)))
      (julkaise-palvelu http :tallenna-urakan-toteuma
                        (fn [user toteuma]
                          (tallenna-toteuma db user toteuma)))
      (julkaise-palvelu http :urakan-erilliskustannukset
        (fn [user tiedot]
          (hae-urakan-erilliskustannukset db user tiedot)))
      (julkaise-palvelu http :tallenna-erilliskustannus
        (fn [user toteuma]
          (tallenna-erilliskustannus db user toteuma)))
      (julkaise-palvelu http :tallenna-toteuma-ja-toteumamateriaalit
                        (fn [user tiedot]
                          (tallenna-toteuma-ja-toteumamateriaalit db user (:toteuma tiedot) (:toteumamateriaalit tiedot))))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-toteumat
      :urakan-toteuma-paivat
      :urakan-tehtavat-toteumittain
      :hae-urakan-tehtavat
      :tallenna-urakan-toteuma
      :urakan-erilliskustannukset
      :tallenna-erilliskustannus
      :tallenna-toteuma-ja-toteumamateriaalit)
    this))
