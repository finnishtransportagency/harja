(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-kanavan-toimenpide]))

(defn hae-kanavatoimenpiteet [db user {urakka ::urakka/id
                                       sopimus ::sopimus/id
                                       alkupvm ::kanavan-toimenpide/alkupvm
                                       loppupvm ::kanavan-toimenpide/loppupvm
                                       toimenpidekoodi ::toimenpidekoodi/id
                                       tyyppi ::kanavan-toimenpide/kanava-toimenpidetyyppi
                                       :as hakuehdot}]
  (let [tyyppi (when tyyppi (name tyyppi))]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user urakka)
    (q-kanavan-toimenpide/hae-sopimuksen-toimenpiteet-aikavalilta
      db
      {:urakka urakka
       :sopimus sopimus
       :alkupvm alkupvm
       :loppupvm loppupvm
       :toimenpidekoodi toimenpidekoodi
       :tyyppi tyyppi})))

(defrecord Kanavatoimenpiteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kanavatoimenpiteet
      (fn [user hakuehdot]
        (hae-kanavatoimenpiteet db user hakuehdot))
      {:kysely-spec ::kanavan-toimenpide/hae-kanavatoimenpiteet-kutsu
       :vastaus-spec ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet)
    this))