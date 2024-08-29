(ns harja.palvelin.palvelut.hallinta.kojelauta
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.kojelauta :as q]))

(defn hae-urakat-kojelautaan [db kayttaja]
  (->(q/hae-urakat-kojelautaan db)
    (konv/vector-mappien-alaviiva->rakenne)
    (konv/sarakkeet-vektoriin {:hoitovuosi :hoitovuodet} :id :alkuvuosi)))

(defrecord KojelautaHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakat-kojelautaan
      (fn [kayttaja _]
        (hae-urakat-kojelautaan db kayttaja)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakat-kojelautaan)
    this))
