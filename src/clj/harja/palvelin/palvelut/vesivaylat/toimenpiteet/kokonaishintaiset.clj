(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.kokonaishintaiset
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q]))

(defn hae-kokonaishintaiset-toimenpiteet [db user tiedot]
  nil)

(defrecord KokonaishintaisetToimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db}]
    (julkaise-palvelut
      http
      :hae-kokonaishintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-kokonaishintaiset-toimenpiteet db user tiedot))))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kokonaishintaiset-toimenpiteet)))
