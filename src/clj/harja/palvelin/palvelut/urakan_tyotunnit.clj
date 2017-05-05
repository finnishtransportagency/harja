(ns harja.palvelin.palvelut.urakan-tyotunnit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit-d]
            [harja.kyselyt.urakan-tyotunnit :as urakan-tyotunnit-q]))

(defn tallenna-urakan-tyotunnit [db kayttaja {tyotunnit ::urakan-tyotunnit-d/urakan-tyotunnit}]
  (doseq [{urakka-id ::urakan-tyotunnit-d/urakka} tyotunnit]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja urakka-id))
  (urakan-tyotunnit-q/tallenna-urakan-tyotunnit db tyotunnit))

(defrecord Urakan-tyotunnit []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :tallenna-urakan-tyotunnit
        (fn [kayttaja tiedot]
          (tallenna-urakan-tyotunnit (:db this) kayttaja tiedot))
        {:kysely-spec ::urakan-tyotunnit-d/urakan-tyotunnit
         :vastaus-spec ::urakan-tyotunnit-d/urakan-tyotunnit}))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-tyotunnit)
    this))
