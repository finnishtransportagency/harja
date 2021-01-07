(ns harja.palvelin.palvelut.jarjestelman-tila
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.sonja :as sd]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.kyselyt.konversio :as konv]
            [slingshot.slingshot :refer [try+]]))

(defn hae-sonjan-tila
  ([db] (hae-sonjan-tila db false))
  ([db kehitysmoodi?]
   (let [sonjan-tila (q/sonjan-tila db kehitysmoodi?)]
     (map #(update % :tila konv/jsonb->clojuremap) sonjan-tila))))

(defn hae-itmfn-tila
  ([db] (hae-itmfn-tila db false))
  ([db kehitysmoodi?]
   (let [itmfn-tila (q/itmfn-tila db kehitysmoodi?)]
     (map #(update % :tila konv/jsonb->clojuremap) itmfn-tila))))

(defrecord JarjestelmanTila [kehitysmoodi?]
  component/Lifecycle
  (start
    [{db :db
      http :http-palvelin
      :as this}]
    (julkaise-palvelu
      http
      :hae-sonjan-tila
      (fn [kayttaja]
        (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-sonjajonot kayttaja)
        (hae-sonjan-tila db kehitysmoodi?))
      {:kysely-spec ::sd/hae-jonojen-tilat-kysely
       :vastaus-spec ::sd/hae-jonojen-tilat-vastaus})
    (julkaise-palvelu
      http
      :hae-itmfn-tila
      (fn [kayttaja]
        (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-sonjajonot kayttaja)
        (hae-itmfn-tila db kehitysmoodi?)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-sonjan-tila)
    (poista-palvelu (:http-palvelin this) :hae-itmfn-tila)
    this))
