(ns harja.palvelin.palvelut.urakan-tyotunnit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit-d]
            [harja.kyselyt.urakan-tyotunnit :as urakan-tyotunnit-q]))

(defn tallenna-urakan-tyotunnit [db kayttaja {tyotunnit ::urakan-tyotunnit-d/urakan-tyotunnit}]
  (doseq [{urakka-id ::urakan-tyotunnit-d/urakka} tyotunnit]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja urakka-id))
  (urakan-tyotunnit-q/tallenna-urakan-tyotunnit db tyotunnit))

(defn hae-urakan-vuosikolmanneksen-tunnit [db kayttaja hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat kayttaja (::urakan-tyotunnit-d/urakka hakuehdot))
  (urakan-tyotunnit-q/hae-urakan-vuosikolmanneksen-tyotunnit db hakuehdot))

(defrecord UrakanTyotunnit []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           :as this}]

    (julkaise-palvelu http
                      :tallenna-urakan-tyotunnit
                      (fn [kayttaja tiedot]
                        (tallenna-urakan-tyotunnit db kayttaja tiedot))
                      {:kysely-spec ::urakan-tyotunnit-d/urakan-tyotunnit
                       :vastaus-spec ::urakan-tyotunnit-d/urakan-tyotunnit})

    (julkaise-palvelu http
                      :hae-urakan-vuosikolmanneksen-tunnit
                      (fn [kayttaja tiedot]
                        (println "---> " tiedot)
                        (hae-urakan-vuosikolmanneksen-tunnit db kayttaja tiedot))
                      {:kysely-spec ::urakan-tyotunnit-d/urakan-tyotunnit
                       :vastaus-spec ::urakan-tyotunnit-d/tyotunnit})
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :tallenna-urakan-tyotunnit
                     :hae-urakan-vuosikolmanneksen-tunnit)

    this))
