(ns harja.palvelin.palvelut.tyomaapaivakirja
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.tyomaapaivakirja :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn tyomaapaivakirja-hae [db user tiedot]
  (oikeudet/voi-lukea? oikeudet/raportit-tyomaapaivakirja (:urakka-id tiedot) user)
  (into [] (q/hae-tiedot db)))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae
      (fn [user tiedot]
        (tyomaapaivakirja-hae db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :tyomaapaivakirja-hae)
    this))
