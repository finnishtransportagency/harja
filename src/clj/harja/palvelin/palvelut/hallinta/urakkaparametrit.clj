(ns harja.palvelin.palvelut.hallinta.urakkaparametrit
  "Hallinnan näkymä, joka näyttää valitun urakan urakka_parametrit-taulun sisällön vain lukutilassa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.urakat :as urakat-q]))

(defn- hae-urakat [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-urakkahenkilot kayttaja)
  (into []
    (urakat-q/hae-urakat-joilla-voi-olla-parametreja db)))

(defn- hae-urakan-parametrit [db kayttaja {:keys [urakkaid]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-urakkahenkilot kayttaja)
  (when urakkaid
    (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakkaid}))))

(defrecord UrakkaParametritHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakkaparametrit-urakat
      (fn [kayttaja _tiedot]
        (hae-urakat db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-urakan-parametrit
      (fn [kayttaja tiedot]
        (hae-urakan-parametrit db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakkaparametrit-urakat
      :hae-urakan-parametrit)
    this))

