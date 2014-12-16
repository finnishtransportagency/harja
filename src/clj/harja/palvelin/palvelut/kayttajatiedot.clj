(ns harja.palvelin.palvelut.kayttajatiedot
  "Palvelu, jolla voi hakea perustietoja nykyisestä käyttäjästä"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]))

(declare hae-kayttajatiedot)

(defrecord Kayttajatiedot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :kayttajatiedot hae-kayttajatiedot)
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :kayttajatiedot)
    this))


(defn hae-kayttajatiedot
  "Hae tämänhetkisen käyttäjän tiedot frontille varten"
  [user alku]
  ;; FIXME: tämän pitäisi palauttaa myös käyttäjän roolit yms hyödyllistä tietoa,
  ;; jota fronttipuoli voi käyttää, nyt vain käyttäjä sellaisenaan
  user)
  
