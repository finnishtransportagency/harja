(ns harja.palvelin.palvelut.jarjestelman-tila
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.sonja :as sd]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.kyselyt.konversio :as konv]
            [slingshot.slingshot :refer [try+]]
            [clj-time.coerce :as tc]
            [harja.pvm :as pvm]))

(defn hae-sonjan-tila
  ([db] (hae-sonjan-tila db false))
  ([db kehitysmoodi?]
   (let [sonjan-tila (q/sonjan-tila db kehitysmoodi?)]
     (map #(update % :tila konv/jsonb->clojuremap) sonjan-tila))))

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
    this)
  (stop [this]
    this))
