(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]))

(defn hae-valitavoitteet [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)

  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))

(defn merkitse-valmiiksi! [db user {:keys [urakka-id valitavoite-id valmis-pvm kommentti] :as tiedot}]
  (log/info "merkitse valmiiksi: " tiedot)
  (oik/vaadi-rooli-urakassa user roolit/urakoitsijan-urakkaroolit-kirjoitus
                            urakka-id)
  (jdbc/with-db-transaction [c db]
    (and (= 1 (q/merkitse-valmiiksi! db (konv/sql-date valmis-pvm) kommentti
                                     (:id user) urakka-id valitavoite-id))
         (hae-valitavoitteet db user urakka-id))))
  

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (hae-valitavoitteet (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this) :merkitse-valitavoite-valmiiksi
                      (fn [user tiedot]
                        (merkitse-valmiiksi! (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-urakan-valitavoitteet :merkitse-valitavoite-valmiiksi)
    this))
