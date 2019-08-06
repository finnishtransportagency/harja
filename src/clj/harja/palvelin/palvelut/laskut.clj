(ns harja.palvelin.palvelut.laskut
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [harja.kyselyt.laskut :as q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.pvm :as pvm]))


(defn hae-urakan-laskut
  [db user hakuehdot]
  (println (:urakka-id hakuehdot) (:alkupvm hakuehdot) (:loppupvm hakuehdot))
  ;;;;(oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/hae-urakan-laskut db {:urakka (:urakka-id hakuehdot)
                           :alkupvm (:alkupvm hakuehdot)
                           :loppupvm (:loppupvm hakuehdot)}))

(defn hae-lasku
  [db user {:keys [urakka-id viite]}]
  ;;(oikeudet/vaadi-lukuoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/hae-lasku db {:urakka urakka-id
                   :viite viite}))


(defn luo-lasku
  [db user lasku]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/luo-lasku<! db {:viite         (:viite lasku)
                   :erapaiva      (konv/sql-date (:erapaiva lasku))
                   :kokonaissumma (:kokonaissumma lasku)
                   :urakka        (:urakka lasku)
                   :tyyppi        (:tyyppi lasku)
                   :kayttaja      (:id user)}))

(defn paivita-lasku
  [db user lasku]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/paivita-lasku! db {:viite         (:viite lasku)
                       :erapaiva      (konv/sql-date (:erapaiva lasku))
                       :kokonaissumma (:kokonaissumma lasku)
                       :urakka        (:urakka lasku)
                       :tyyppi        (:tyyppi lasku)
                       :kayttaja      (:id user)}))

(defn poista-lasku
  [db user {:keys [urakka-id viite]}]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/poista-lasku! db {:urakka-id urakka-id
                      :viite     viite}))

(defn luo-laskun-kohdistus
  [db user urakka-id lasku-id laskurivi]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/luo-laskun-kohdistus<! db {:lasku               lasku-id
                              :summa               (:summa laskurivi)
                              :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                              :tehtavaryhma        (:tehtavaryhma laskurivi)
                              :tehtava             (:tehtava laskurivi)
                              :suorittaja          (:suorittaja laskurivi)
                              :suoritus_alku       (:suoritus_alku laskurivi)
                              :suoritus_loppu      (:suoritus_loppu laskurivi)
                              :kayttaja            (:id user)}))

(defn paivita-laskun-kohdistus
  [db user urakka-id lasku-id laskurivi]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/paivita-laskun-kohdistus! db {:lasku               lasku-id
                                  :summa               (:summa laskurivi)
                                  :toimenpideinstanssi (:toimenpideinstanssi laskurivi)
                                  :tehtavaryhma        (:tehtavaryhma laskurivi)
                                  :tehtava             (:tehtava laskurivi)
                                  :suorittaja          (:suorittaja laskurivi)
                                  :suoritus_alku       (:suoritus_alku laskurivi)
                                  :suoritus_loppu      (:suoritus_loppu laskurivi)
                                  :kayttaja            (:id user)}))

(defn poista-laskun-kohdistus
  [db user urakka-id laskuerittelyn-id]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)
  (q/poista-laskun-kohdistus! db {:urakka-id         urakka-id
                                 :laskuerittelyn-id laskuerittelyn-id}))





(defn tallenna-lasku
  "Funktio tallentaa laskun ja laskuerittelyn (laskun kohdistuksen). Käytetään teiden hoidon urakoissa (MHU)."
  [db user {:keys [urakka-id lasku]}]
  ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laskutus-laskun-kirjoitus user urakka-id)

  (let [nykyiset-arvot (hae-lasku db user lasku)
        laskun-kohdistus-avain (fn [rivi]
                                 [(:viite rivi) (:kohdistus-id rivi)])
        laskuerittely (into #{} (map laskun-kohdistus-avain nykyiset-arvot))
        urakan-toimenpideinstanssit (into #{}
                                          (map :id)
                                          (tpi-q/urakan-toimenpideinstanssi-idt db urakka-id))
        kasiteltavat-toimenpideinstanssit (into #{} (map #(:toimenpideinstanssi %) lasku))]

    ;; Varmistetaan ettei laskua kohdisteta toimenpideinstanssille, joka ei kuulu tähän urakkaan.
    (when-not (empty? (set/difference kasiteltavat-toimenpideinstanssit
                                      urakan-toimenpideinstanssit))
      (throw (roolit/->EiOikeutta "virheellinen toimenpideinstanssi")))

  ;  (doseq [rivi laskuerittely]
  ;    (as-> rivi r
  ;          (update r :summa big/unwrap)
  ;          (assoc r :kayttaja (:id user))
  ;          (if (not (tyot-kannassa (tyo-avain t)))
  ;            (q/lisaa-kustannusarvioitu-tyo<! db t)
  ;            (q/paivita-kustannusarvioitu-tyo! db t)))))
  ;
  ;(hae-urakan-kustannusarvioidut-tyot db user urakka-id)
  ))






(defrecord Laskut []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :laskut
                        (fn [user hakuehdot]
                          (hae-urakan-laskut db user hakuehdot)))
      (julkaise-palvelu http :lasku
                        (fn [user lasku]
                          (hae-lasku db user lasku)))
      (julkaise-palvelu http :tallenna-lasku
                        (fn [user lasku]
                          (tallenna-lasku db user lasku)))
      (julkaise-palvelu http :poista-lasku
                        (fn [user lasku]
                          (poista-lasku db user lasku)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-laskut)
    (poista-palvelut (:http-palvelin this) :hae-lasku)
    (poista-palvelut (:http-palvelin this) :tallenna-lasku)
    (poista-palvelut (:http-palvelin this) :poista-lasku)
    this))
