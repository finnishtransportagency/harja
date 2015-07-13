(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-sql-time]]

            [harja.kyselyt.turvallisuuspoikkeamat :as q]))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (log/debug "Haetaan turvallisuuspoikkeamia urakasta " urakka-id ", aikaväliltä " alku " - " loppu)
  (let [tulos (into []
                    (comp (map konv/alaviiva->rakenne)
                          (harja.geo/muunna-pg-tulokset :sijainti)
                          (map #(konv/array->vec % :tyyppi))
                          (map #(assoc % :tyyppi (keyword (:tyyppi %))))
                          (map #(assoc-in % [:liite :tyyppi] (keyword (get-in % [:liite :tyyppi])))))
                    (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))]
    (log/debug "Löydettiin turvallisuuspoikkeamat: " (pr-str (mapv :id tulos)))
    tulos))

(defrecord Turvallisuuspoikkeamat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-turvallisuuspoikkeamat
                      (fn [user tiedot]
                        (hae-turvallisuuspoikkeamat (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat)

    this))
