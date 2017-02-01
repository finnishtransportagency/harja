(ns harja.palvelin.palvelut.muut-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.muutoshintaiset-tyot :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]))


(def muutoshintaiset-xf
  (comp
    (map konv/alaviiva->rakenne)
    (map #(assoc %
           :yksikkohinta (if (:yksikkohinta %) (double (:yksikkohinta %)))))))


(defn hae-urakan-muutoshintaiset-tyot
  "Palvelu, joka palauttaa urakan muutoshintaiset työt."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka-id)
  (into []
    muutoshintaiset-xf
    (q/listaa-urakan-muutoshintaiset-tyot db urakka-id)))

(defn tallenna-muutoshintaiset-tyot
  "Palvelu joka tallentaa muutoshintaiset tyot."
  [db user {:keys [urakka-id tyot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-muutos-ja-lisatyot user urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")

  (jdbc/with-db-transaction
    [c db]
    (doseq [tyo tyot]
      (let [parametrit [c (:yksikko tyo) (:yksikkohinta tyo) (:id user)
                        urakka-id (:sopimus tyo) (:tehtava tyo)
                        (konv/sql-date (:alkupvm tyo))
                        (konv/sql-date (:loppupvm tyo))]]

        (if (:poistettu tyo)
          (do
            (apply q/poista-muutoshintainen-tyo! parametrit))
          ;; uusien rivien id on negatiivinen
          (if-not (id-olemassa? (:id tyo))
            ;; insert
            (do
              (apply q/lisaa-muutoshintainen-tyo<! parametrit))
            ;;update
            (do
              (apply q/paivita-muutoshintainen-tyo! parametrit))))))
    (hae-urakan-muutoshintaiset-tyot c user urakka-id)))

(defrecord Muut-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :muutoshintaiset-tyot (fn [user urakka-id]
                                 (hae-urakan-muutoshintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-muutoshintaiset-tyot (fn [user tiedot]
                                                 (tallenna-muutoshintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :muutoshintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-muutoshintaiset-tyot)
    this))
