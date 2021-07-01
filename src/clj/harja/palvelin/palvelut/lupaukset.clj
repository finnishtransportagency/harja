(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-urakan-lupaustiedot [db user tiedot]
  (println "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (first
    (lupaukset-q/hae-urakan-lupaustiedot db {:urakkaid (:urakka-id tiedot)})))

(defn- tallenna-urakan-luvatut-pisteet
  [db user tiedot]
  (println "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (let [params {:id (:id tiedot)
                :urakkaid (:urakka-id tiedot)
                :pisteet (:pisteet tiedot)
                :kayttaja (:id user)}
        vastaus (if (:id tiedot)
                  (lupaukset-q/paivita-urakan-luvatut-pisteet<! db params)
                  (lupaukset-q/lisaa-urakan-luvatut-pisteet<! db params))]
    vastaus))

(defrecord Lupaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-lupaustiedot
                      (fn [user tiedot]
                        (hae-urakan-lupaustiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-luvatut-pisteet
                      (fn [user tiedot]
                        (tallenna-urakan-luvatut-pisteet (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot
                     :tallenna-luvatut-pisteet)
    this))