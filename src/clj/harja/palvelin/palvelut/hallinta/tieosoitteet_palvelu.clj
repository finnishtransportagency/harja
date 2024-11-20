(ns harja.palvelin.palvelut.hallinta.tieosoitteet-palvelu
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [clojure.string :as str]))

(defn hae-tieosoitteet-hallintaan [db kayttaja tiedot]
  (let [_ (println "hae-tieosoitteet-hallintaan :: tiedot:" tiedot)]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-toteumatyokalu kayttaja)
    (tieverkko-kyselyt/hae-tieosoitteet db)))

  (defrecord TieosoitteetHallinta []
    component/Lifecycle
    (start [{:keys [http-palvelin db] :as this}]
      (julkaise-palvelu http-palvelin :hae-tieosoitteet-hallintaan
        (fn [kayttaja tiedot]
          (hae-tieosoitteet-hallintaan db kayttaja tiedot)))

      this)
    (stop [{:keys [http-palvelin] :as this}]
      (poista-palvelut http-palvelin
        :hae-tieosoitteet-hallintaan)
      this))
