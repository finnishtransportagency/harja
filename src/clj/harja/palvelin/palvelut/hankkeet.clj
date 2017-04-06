(ns harja.palvelin.palvelut.hankkeet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.hankkeet :as q]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]))

(defn hae-paattymattomat-vesivaylahankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    #_(oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylaurakoiden-luonti user)
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user)
    (q/hae-paattymattomat-vesivaylahankkeet db)))

(defrecord Hankkeet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelut
      http
      :hae-paattymattomat-vesivaylahankkeet
      (fn [user _]
        (hae-paattymattomat-vesivaylahankkeet db user)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-paattymattomat-vesivaylahankkeet)

    this))