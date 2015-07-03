(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]))

(defn hae-valitavoitteet [db user urakka-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)

  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))

(defn merkitse-valmiiksi! [db user {:keys [urakka-id valitavoite-id valmis-pvm kommentti] :as tiedot}]
  (log/info "merkitse valmiiksi: " tiedot)
  (roolit/vaadi-rooli-urakassa user roolit/urakoitsijan-urakkaroolit-kirjoitus
                            urakka-id)
  (jdbc/with-db-transaction [c db]
    (and (= 1 (q/merkitse-valmiiksi! db (konv/sql-date valmis-pvm) kommentti
                                     (:id user) urakka-id valitavoite-id))
         (hae-valitavoitteet db user urakka-id))))

(defn tallenna! [db user {:keys [urakka-id valitavoitteet]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (jdbc/with-db-transaction [c db]
    ;; Poistetaan tietokannasta :poistettu merkityt
    (doseq [poistettava (filter :poistettu valitavoitteet)]
      (q/poista-valitavoite! c (:id user) urakka-id (:id poistettava)))

    ;; Luodaan uudet (FIXME: lisää kentät kun speksi valmis)
    (doseq [{:keys [takaraja nimi]} (filter #(< (:id %) 0) valitavoitteet)]
      (q/lisaa-valitavoite<! c urakka-id (konv/sql-date takaraja) nimi (:id user)))

    ;; Päivitetään olemassaolevat (FIXME: lisää kentät kun speksi valmis)
    (doseq [{:keys [id takaraja nimi]} (filter #(> (:id %) 0) valitavoitteet)]
      (q/paivita-valitavoite! c nimi (konv/sql-date takaraja) (:id user) urakka-id id))

    ;; Lopuksi haetaan uudet tavoitteet
    (hae-valitavoitteet c user urakka-id)))

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (hae-valitavoitteet (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this) :merkitse-valitavoite-valmiiksi
                      (fn [user tiedot]
                        (merkitse-valmiiksi! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-valitavoitteet
                      (fn [user tiedot]
                        (tallenna! (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-valitavoitteet :merkitse-valitavoite-valmiiksi
                     :tallenna-valitavoitteet)
    this))
