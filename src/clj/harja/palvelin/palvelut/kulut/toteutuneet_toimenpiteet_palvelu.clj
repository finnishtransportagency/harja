(ns harja.palvelin.palvelut.kulut.toteutuneet-toimenpiteet-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.toteutuneet-toimenpiteet-kyselyt :as kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]))

(defn hae-toteutuneet-toimenpiteet
  [db user {:keys [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm]}]
  (oikeudet/vaadi-lukuoikeus
    oikeudet/urakat-toteumat-kokonaishintaisettyot
    user
    urakka-id)
  {:toimenpiteet
   (kyselyt/hae-vhu-toteutuneet-toimenpiteet
     db
     {:urakka urakka-id
      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
      :alkupvm alkupvm
      :loppupvm loppupvm})})

(defrecord ToteutuneetToimenpiteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu
      (:http-palvelin this)
      :hae-vhu-toteutuneet-toimenpiteet
      (fn [user tiedot]
        (hae-toteutuneet-toimenpiteet (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelu
      (:http-palvelin this)
      :hae-vhu-toteutuneet-toimenpiteet)
    this))
