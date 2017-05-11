(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset
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

(defn hae-yksikkohintaiset-toimenpiteet [db user tiedot]
  nil)

(defrecord YksikkohintaisetToimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db}]
    (julkaise-palvelut
      http
      :hae-yksikkohintaiset-toimenpiteet
      (fn [user tiedot]
        (hae-yksikkohintaiset-toimenpiteet db user tiedot))))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-yksikkohintaiset-toimenpiteet)))