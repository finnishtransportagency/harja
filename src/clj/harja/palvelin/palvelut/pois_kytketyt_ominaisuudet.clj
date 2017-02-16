(ns harja.palvelin.palvelut.pois-kytketyt-ominaisuudet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defrecord PoisKytketytOminaisuudet [pois-kytketyt-ominaisuudet-vektori]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :pois-kytketyt-ominaisuudet
                        (fn [user tiedot]
                          (oikeudet/ei-oikeustarkistusta!)
                          pois-kytketyt-ominaisuudet-vektori))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :ping)
    this))
