(ns harja.palvelin.palvelut.pois-kytketyt-ominaisuudet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.asetukset :as asetukset]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defrecord PoisKytketytOminaisuudet []
  component/Lifecycle
  (start [this]
    (when-let [http (:http-palvelin this)]
      (julkaise-palvelu http :pois-kytketyt-ominaisuudet
                        (fn [user tiedot]
                          (oikeudet/ei-oikeustarkistusta!)
                          @asetukset/pois-kytketyt-ominaisuudet)))
    this)

  (stop [this]
    (when-let [http (:http-palvelin this)]
      (poista-palvelut http :pois-kytketyt-ominaisuudet))
    this))
