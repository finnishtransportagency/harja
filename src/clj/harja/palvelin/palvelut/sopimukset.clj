(ns harja.palvelin.palvelut.sopimukset
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.sopimukset :as q]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]))

(defn hae-harjassa-luodut-sopimukset [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (q/hae-harjassa-luodut-sopimukset db)))

(defrecord Sopimukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelut
      http
      :hae-harjassa-luodut-sopimukset
      (fn [user _]
        (hae-harjassa-luodut-sopimukset db user)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-harjassa-luodut-sopimukset)

    this))