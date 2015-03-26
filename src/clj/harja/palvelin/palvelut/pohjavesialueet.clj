(ns harja.palvelin.palvelut.pohjavesialueet
  "Palvelu pohjavesialueiden hakemiseksi, palauttaa alueet tietokannan pohjavesialue taulusta."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.geo :as geo]
            [harja.kyselyt.pohjavesialueet :as q]
            ))


(defn hae-pohjavesialueet [db user hallintayksikko]
  (into []
        (geo/muunna-pg-tulokset :alue)
        (q/hae-pohjavesialueet db hallintayksikko)))


(defrecord Pohjavesialueet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-pohjavesialueet
                      (fn [user hallintayksikko]
                        (hae-pohjavesialueet (:db this) user hallintayksikko)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-pohjavesialueet)
    this))

                    

  
                                        
