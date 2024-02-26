(ns harja.palvelin.palvelut.hallinta.tarjoushinnat
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn- hae-tarjoushinnat [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-tarjoushinnat kayttaja)
  (budjettisuunnittelu-q/hae-urakoiden-tarjoushinnat db))

(defn- paivita-tarjoushinnat [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-tarjoushinnat kayttaja)
  (doseq [{:keys [id tarjous-tavoitehinta]} tiedot]
    (budjettisuunnittelu-q/paivita-tarjoushinta<! db {:id id
                                                    :tarjous-tavoitehinta tarjous-tavoitehinta}))
  (budjettisuunnittelu-q/hae-urakoiden-tarjoushinnat db))

(defrecord TarjoushinnatHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-tarjoushinnat
      (fn [kayttaja tiedot]
        (hae-tarjoushinnat db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-tarjoushinnat
      (fn [kayttaja tiedot]
        (paivita-tarjoushinnat db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tarjoushinnat
      :paivita-tarjoushinnat)
    this))
