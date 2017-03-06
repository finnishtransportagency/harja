(ns harja.palvelin.palvelut.pois-kytketyt-ominaisuudet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defonce pois-kytketyt-ominaisuudet (atom #{}))

(defn ominaisuus-kaytossa? [k]
  (let [pko @pois-kytketyt-ominaisuudet]
    (not (and (set? pko) (contains? pko k)))))

(defrecord PoisKytketytOminaisuudet [pois-kytketyt-ominaisuudet-joukko]
  component/Lifecycle
  (start [this]
    (reset! pois-kytketyt-ominaisuudet pois-kytketyt-ominaisuudet-joukko)
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :pois-kytketyt-ominaisuudet
                        (fn [user tiedot]
                          (oikeudet/ei-oikeustarkistusta!)
                          pois-kytketyt-ominaisuudet-joukko))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :ping)
    this))
