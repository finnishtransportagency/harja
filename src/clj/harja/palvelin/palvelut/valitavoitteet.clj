(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.valitavoitteet.urakkakohtaiset-valitavoitteet :as urakkakohtaiset]
            [harja.palvelin.palvelut.valitavoitteet.valtakunnalliset-valitavoitteet :as valtakunnalliset]))

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (urakkakohtaiset/hae-urakan-valitavoitteet (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-urakan-valitavoitteet
                      (fn [user tiedot]
                        (urakkakohtaiset/tallenna-urakan-valitavoitteet! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :hae-valtakunnalliset-valitavoitteet
                      (fn [user _]
                        (valtakunnalliset/hae-valtakunnalliset-valitavoitteet (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-valtakunnalliset-valitavoitteet
                      (fn [user tiedot]
                        (valtakunnalliset/tallenna-valtakunnalliset-valitavoitteet! (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-valitavoitteet
                     :merkitse-valitavoite-valmiiksi
                     :tallenna-urakan-valitavoitteet
                     :hae-valtakunnalliset-valitavoitteet
                     :tallenna-valtakunnalliset-valitavoitteet)
    this))
