(ns harja.palvelin.palvelut.kayttajat
  "Palvelu, jolla voi hakea ja tallentaa käyttäjätietoja"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]

            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.kayttajat :as q]))

(declare hae-kayttajat)

(defrecord Kayttajat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-kayttajat (fn [user params]
                                       (apply hae-kayttajat (:db this) user params)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-kayttajat)
    this))


(defn hae-kayttajat
  "Hae käyttäjät tiedot frontille varten"
  [db user hakuehto alku maara]
  ;; MUISTA: käyttäjän oikeuksien sallimat käyttäjät vain!
  (let [kayttajat (into [] (q/hae-kayttajat db (:id user) hakuehto alku maara))
        lkm (:lkm (first (q/hae-kayttajat-lkm db (:id user) hakuehto)))]
    
    [lkm kayttajat]))
