(ns harja.palvelin.palvelut.urakan-tyotunnit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakan-tyotunnit :as ut]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.tyokalut.spec-apurit :as spec-apurit]))

(defn hae-urakan-tyotunnit [db kayttaja urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset kayttaja urakka-id)
  (let [tunnit (q/hae-urakan-tyotunnit db {::ut/urakka-id urakka-id})]
    tunnit))

(defn tallenna-urakan-tyotunnit [db
                                 kayttaja
                                 {urakka-id ::ut/urakka-id
                                  urakan-tyotunnit ::ut/urakan-tyotunnit-vuosikolmanneksittain}]
  (jdbc/with-db-transaction [db db]
    (doseq [{urakka-id ::ut/urakka-id :as tyotunnit} urakan-tyotunnit]
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset kayttaja urakka-id)
      (q/tallenna-urakan-tyotunnit db tyotunnit)))

  (hae-urakan-tyotunnit db kayttaja urakka-id))

(defn hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit [db kayttaja urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset kayttaja urakka-id)
  (let [tunnit (q/hae-kuluvan-vuosikolmanneksen-tyotunnit db urakka-id)]
    (spec-apurit/poista-nil-avaimet {::ut/urakan-tyotunnit tunnit} false)))

(defrecord UrakanTyotunnit []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           :as this}]

    (julkaise-palvelu http
                      :hae-urakan-tyotunnit
                      (fn [kayttaja {urakka-id ::ut/urakka-id}]
                        (hae-urakan-tyotunnit db kayttaja urakka-id))
                      {:kysely-spec ::ut/urakan-tyotuntien-haku
                       :vastaus-spec ::ut/urakan-tyotunnit-vuosikolmanneksittain})

    (julkaise-palvelu http
                      :tallenna-urakan-tyotunnit
                      (fn [kayttaja tiedot]
                        (tallenna-urakan-tyotunnit db kayttaja tiedot))
                      {:kysely-spec ::ut/urakan-tyotuntien-tallennus
                       :vastaus-spec ::ut/urakan-tyotunnit-vuosikolmanneksittain})

    (julkaise-palvelu http
                      :hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit
                      (fn [kayttaja {urakka-id ::ut/urakka-id}]
                        (hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit db kayttaja urakka-id))
                      {:kysely-spec ::ut/urakan-kuluvan-vuosikolmanneksen-tyotuntien-haku
                       :vastaus-spec ::ut/urakan-kuluvan-vuosikolmanneksen-tyotuntien-hakuvastaus})
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-urakan-tyotunnit
                     :tallenna-urakan-tyotunnit
                     :hae-urakan-kuluvan-vuosikolmanneksen-tyotunnit)

    this))
