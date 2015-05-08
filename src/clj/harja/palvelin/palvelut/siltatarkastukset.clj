(ns harja.palvelin.palvelut.siltatarkastukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.kyselyt.siltatarkastukset :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.domain.roolit :as roolit]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn hae-urakan-sillat
  "Hakee annetun urakan alueen sillat sekä niiden viimeisimmän tarkastuspäivän ja tarkastajan."
  [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-sillat db urakka-id)))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-urakan-sillat
                        (fn [user urakka-id]
                          (hae-urakan-sillat db user urakka-id)))
      this))

  (stop [this]
    (poista-palvelut (:http this) :hae-urakan-sillat)))
