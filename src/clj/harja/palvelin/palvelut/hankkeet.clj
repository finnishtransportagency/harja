(ns harja.palvelin.palvelut.hankkeet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.hankkeet :as q]
            [harja.domain.hanke :as hanke]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]))

(defn hae-paattymattomat-vesivaylahankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (q/hae-paattymattomat-vesivaylahankkeet db)))

(defn hae-harjassa-luodut-hankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (q/hae-harjassa-luodut-hankkeet db)))

(defrecord Hankkeet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelut
      http
      :hae-paattymattomat-vesivaylahankkeet
      (fn [user _]
        (hae-paattymattomat-vesivaylahankkeet db user))
      :hae-harjassa-luodut-hankkeet
      (fn [user _]
        (hae-harjassa-luodut-hankkeet db user))
      {:vastaus-spec ::hanke/hae-harjassa-luodut-hankkeet-vastaus})

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-paattymattomat-vesivaylahankkeet)

    this))