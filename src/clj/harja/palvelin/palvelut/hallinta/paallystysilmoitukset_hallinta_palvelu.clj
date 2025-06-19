(ns harja.palvelin.palvelut.hallinta.paallystysilmoitukset-hallinta-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.paallystys-kyselyt :as q-paallystys]
            [harja.domain.oikeudet :as oikeudet] 
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn- hae-paallystys-urakat-hallintaan [db kayttaja {:keys [vuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-paallystysilmoitukset kayttaja)
  (log/debug "hae-paallystys-urakat-hallintaan :: vuosi" vuosi)
  {:urakat (q-paallystys/hae-paallystys-urakat-hallintaan db {:vuosi vuosi})})

(defrecord PaallystysilmoituksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-paallystys-urakat-hallintaan
      (fn [kayttaja tiedot]
        (hae-paallystys-urakat-hallintaan db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-paallystys-urakat-hallintaan)
    this))