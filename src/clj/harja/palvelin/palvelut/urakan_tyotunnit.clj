(ns harja.palvelin.palvelut.urakan-tyotunnit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit-d]
            [harja.kyselyt.urakan-tyotunnit :as urakan-tyotunnit-q]))

(defn hae-urakan-tyotunnit [db kayttaja urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja urakka-id)
  (urakan-tyotunnit-q/hae-urakan-tyotunnit db {::urakan-tyotunnit-d/urakka-id urakka-id}))

(defn tallenna-urakan-tyotunnit [db kayttaja {tyotunnit ::urakan-tyotunnit-d/urakan-tyotunnit-vuosikolmanneksittain}]
  (doseq [{urakka-id ::urakan-tyotunnit-d/urakka} tyotunnit]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja urakka-id))
  (urakan-tyotunnit-q/tallenna-urakan-tyotunnit db tyotunnit))

(defn hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit [db kayttaja urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja urakka-id)
  (let [tunnit (urakan-tyotunnit-q/hae-kuluvan-vuosikolmanneksen-tyotunnit db urakka-id)]
    tunnit))

(defrecord UrakanTyotunnit []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           :as this}]

    (julkaise-palvelu http
                      :hae-urakan-tyotunnit
                      (fn [kayttaja {urakka-id ::urakan-tyotunnit-d/urakka-id}]
                        (hae-urakan-tyotunnit db kayttaja urakka-id))
                      {:kysely-spec ::urakan-tyotunnit-d/urakan-tyotuntien-haku
                       :vastaus-spec ::urakan-tyotunnit-d/urakan-tyotunnit-vuosikolmanneksittain})

    (julkaise-palvelu http
                      :tallenna-urakan-tyotunnit
                      (fn [kayttaja tiedot]
                        (tallenna-urakan-tyotunnit db kayttaja tiedot))
                      {:kysely-spec ::urakan-tyotunnit-d/urakan-tyotunnit-vuosikolmanneksittain
                       :vastaus-spec ::urakan-tyotunnit-d/urakan-tyotunnit-vuosikolmanneksittain})

    (julkaise-palvelu http
                      :hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit
                      (fn [kayttaja {urakka-id ::urakan-tyotunnit-d/urakka-id}]
                        (hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit db kayttaja urakka-id))
                      {:kysely-spec ::urakan-tyotunnit-d/urakan-kuluvan-vuosikolmanneksen-tyotuntien-haku
                       :vastaus-spec ::urakan-tyotunnit-d/urakan-tyotunnit})
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-urakan-tyotunnit
                     :tallenna-urakan-tyotunnit
                     :hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit)

    this))
