(ns harja.palvelin.palvelut.pois-kytketyt-ominaisuudet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]))

(defonce pois-kytketyt-ominaisuudet (atom nil))

(defn ominaisuus-kaytossa? [k]
  (let [pko @pois-kytketyt-ominaisuudet]
    (if (nil? pko)
      (do
        (log/warn "ominaisuus-kaytossa? " k " kutsuttu ennen ko asetusten lukemista")
        false) ;; ei alustetu vielä -> väitetään kaikkea pois kytketyksi
      (not (contains? pko k)))))

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
