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

(defn paivita-urakan-rahavaraus [db kayttaja {:keys [urakka rahavaraus valittu?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja urakka)
  (if valittu?
    (q/lisaa-urakan-rahavaraus<! db {:urakka urakka
                                     :rahavaraus rahavaraus
                                     :kayttaja (:id kayttaja)})
    (q/poista-urakan-rahavaraus<! db {:urakka urakka
                                      :rahavaraus rahavaraus}))
  (q/hae-urakoiden-rahavaraukset db))

(defrecord RahavarauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-rahavaraukset
      (fn [kayttaja _]
        (hae-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-urakoiden-rahavaraukset
      (fn [kayttaja _]
        (hae-urakoiden-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-urakan-rahavaraus
      (fn [kayttaja tiedot]
        (paivita-urakan-rahavaraus db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-rahavaraukset
      :hae-urakoiden-rahavaraukset
      :paivita-urakan-rahavaraus)
    this))
