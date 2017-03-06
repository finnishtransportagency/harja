(ns harja.palvelin.palvelut.organisaatiot
  "Palvelu organisaatioiden perustietojen hakemiseen.
  Ei oikeustarkistusta, koska tiedot ovat julkisia."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.organisaatiot :as q]
            [harja.domain.oikeudet :as oikeudet]))

(declare hae-organisaatiot)

(defn hae-organisaatiot
  "Palvelu, joka palauttaa kaikki organisaatiot."
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (-> (q/listaa-organisaatiot db)
      vec))

(defrecord Organisaatiot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-organisaatiot (fn [user _]
       (hae-organisaatiot (:db this) user)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-organisaatiot)
    this))
