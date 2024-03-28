(ns harja.palvelin.palvelut.hallinta.rahavaraukset
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.rahavaraukset :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn hae-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (q/hae-rahavaraukset db))

(defn hae-urakoiden-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (q/hae-urakoiden-rahavaraukset db))

(defn paivita-urakan-rahavaraukset [db kayttaja {:keys [urakka rahavaraukset]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja urakka)
  (q/poista-urakan-rahavaraukset<! db urakka)
  (doseq [rahavaraukset rahavaraukset]
    (q/lisaa-urakan-rahavaraus<! db urakka rahavaraukset)))

(defrecord RahavarauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-rahavaraukset
      (fn [kayttaja _]
        (hae-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-urakoiden-rahavaraukset
      (fn [kayttaja _]
        (hae-urakoiden-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-urakan-rahavaraukset
      (fn [kayttaja tiedot]
        (paivita-urakan-rahavaraukset db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tarjoushinnat
      :paivita-tarjoushinnat)
    this))
