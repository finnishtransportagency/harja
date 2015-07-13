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
  (into []
        (comp (map konv/alaviiva->rakenne)
              (harja.geo/muunna-pg-tulokset :sijainti)
              (map #(konv/array->vec % :tyyppi))
              (map #(assoc % :tyyppi (keyword (:tyyppi %))))
              (map #(assoc-in % [:liite :tyyppi] (keyword (get-in % [:liite :tyyppi])))))
        (q/hae-urakan-turvallisuuspoikkeamat db urakka-id alku loppu)))

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
