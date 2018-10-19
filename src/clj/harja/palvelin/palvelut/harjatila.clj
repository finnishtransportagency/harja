(ns harja.palvelin.palvelut.harjatila
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.sonja :as sd]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.harjatila :as q]
            [harja.kyselyt.konversio :as konv]
            [slingshot.slingshot :refer [try+]])
  (:import (com.stuartsierra.component Lifecycle)))

(defn hae-sonjan-tila [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-sonjajonot kayttaja)
  (let [sonjan-tila (q/hae-sonjan-tila db)]
    (map #(update % :tila konv/jsonb->clojuremap) sonjan-tila)))

(defrecord Harjatila []
  component/Lifecycle
  (start
    [{db :db
      http :http-palvelin
      :as this}]
    (julkaise-palvelu
      http
      :hae-sonjan-tila
      (fn [kayttaja]
        (hae-sonjan-tila db kayttaja))
      {:kysely-spec ::sd/hae-jonojen-tilat-kysely
       :vastaus-spec ::sd/hae-jonojen-tilat-vastaus})
    this))
