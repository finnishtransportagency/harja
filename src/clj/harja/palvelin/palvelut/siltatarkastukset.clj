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
  ;(oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-sillat db urakka-id)))

(defn hae-sillan-tarkastukset
  "Hakee annetun sillan siltatarkastukset"
  [db user silta-id]
  (into []
        (q/hae-sillan-tarkastukset db silta-id)))

(defn hae-siltatarkastuksen-kohteet
  "Hakee annetun siltatarkustauksn kohteet ja niiden tulokset"
  [db user siltatarkastus-id]
  (into []
        (q/hae-siltatarkastuksen-kohteet db siltatarkastus-id)))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-urakan-sillat
                        (fn [user urakka-id]
                          (hae-urakan-sillat db user urakka-id)))
      (julkaise-palvelu http :hae-sillan-tarkastukset
                        (fn [user silta-id]
                          (hae-sillan-tarkastukset db user silta-id)))
      (julkaise-palvelu http :hae-siltatarkastuksen-kohteet
                        (fn [user siltatarkastus-id]
                          (hae-siltatarkastuksen-kohteet db user siltatarkastus-id)))

      this))

  (stop [this]
    (poista-palvelut (:http this) :hae-urakan-sillat)
    (poista-palvelut (:http this) :hae-sillan-tarkastukset)
    (poista-palvelut (:http this) :hae-siltatarkastuksen-kohteet)))
