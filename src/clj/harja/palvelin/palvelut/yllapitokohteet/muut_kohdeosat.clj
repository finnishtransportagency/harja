(ns harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.domain.oikeudet :as oikeudet]))

(defn tallenna-muut-kohdeosat [db user tiedot])

(defn hae-yllapitokohteen-muut-kohdeosat [db user {:keys [urakka-id yllapitokohde-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)
  (yy/hae-yllapitokohteen-muut-kohdeosat db yllapitokohde-id))

(defrecord MuutKohdeosat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-muut-kohdeosat
                        (fn [user tiedot]
                          (hae-yllapitokohteen-muut-kohdeosat db user tiedot)))
      (julkaise-palvelu http :tallenna-muut-kohdeosat
                        (fn [user tiedot]
                          (tallenna-muut-kohdeosat db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-muut-kohdeosat
      :tallenna-muut-kohdeosat)
    this))