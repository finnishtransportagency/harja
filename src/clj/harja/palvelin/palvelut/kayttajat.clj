(ns harja.palvelin.palvelut.kayttajat
  "Palvelu, jolla voi hakea ja tallentaa käyttäjätietoja"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]

            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.kayttajat :as q]
            [harja.kyselyt.konversio :as konv]

            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

(declare hae-kayttajat
         hae-kayttajan-tiedot
         tallenna-kayttajan-tiedot)

(defrecord Kayttajat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-kayttajat (fn [user params]
                                       (apply hae-kayttajat (:db this) user params)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-kayttajan-tiedot
                      (fn [user kayttaja-id]
                        (hae-kayttajan-tiedot (:db this) user kayttaja-id)))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-kayttajan-tiedot
                      (fn [user tiedot]
                           (tallenna-kayttajan-tiedot (:db this) user tiedot)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-kayttajat)
    (poista-palvelu (:http-palvelin this) :hae-kayttajan-tiedot)
    (poista-palvelu (:http-palvelin this) :tallenna-kayttajan-tiedot)
    this))


(defn hae-kayttajat
  "Hae käyttäjät tiedot frontille varten"
  [db user hakuehto alku maara]
  
  (let [kayttajat (into []
                        (comp (map konv/organisaatio)
                              (map #(konv/array->vec % :roolit)))
                        (q/hae-kayttajat db (:id user) hakuehto alku maara))
        lkm (:lkm (first (q/hae-kayttajat-lkm db (:id user) hakuehto)))]
    
    [lkm kayttajat]))

(defn hae-kayttajan-tiedot
  "Hakee käyttäjän tarkemmat tiedot muokkausnäkymää varten."
  [db user kayttaja-id]
  {:urakka-roolit (into []
                        (map konv/alaviiva->rakenne)
                        (q/hae-kayttajan-urakka-roolit db kayttaja-id))})
(defn tallenna-kayttajan-tiedot
  "Tallentaa käyttäjän uudet käyttäjäoikeustiedot. Palauttaa lopuksi käyttäjän tiedot."
  [db user {:keys [kayttaja-id tiedot]}]
  (log/info "Tallennetaan käyttäjälle " kayttaja-id " tiedot: " tiedot)
  (jdbc/with-db-transaction [c db]
                                 
    (hae-kayttajan-tiedot c user kayttaja-id)))

  
