(ns harja.palvelin.palvelut.kulut.kulut-laskut
  "Kulut välilehden laskut näkymän palvelu"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.kulut-laskut :as kyselyt]))

(defn hae-kulut-laskut 
  [db user ]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kulut-laskunkirjoitus user)
  (println "\n \n hae-kulut-laskut")
 #_ (kyselyt/hae-kulut-laskut db))

(defrecord KulutLaskut []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-kulut-laskut
        (fn [user]
          (hae-kulut-laskut db user)))
      this))
  (stop [this]
    (poista-palvelu (:http-palvelin this)
      :hae-kulut-laskut)
    this))
