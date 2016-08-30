(ns harja.palvelin.palvelut.valitavoitteet.urakkakohtaiset-valitavoitteet
  "Palvelu urakkakohtaisten välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-valitavoitteet
  "Hakee urakan välitavoitteet sekä valtakunnalliset välitavoitteet"
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))



(defn- poista-poistetut-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (q/poista-urakan-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- luo-uudet-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [takaraja nimi]} (filter
                                    #(and (< (:id %) 0)
                                          (not (:poistettu %)))
                                    valitavoitteet)]
    (q/lisaa-urakan-valitavoite<! db {:urakka urakka-id
                                      :takaraja (konv/sql-date takaraja)
                                      :nimi nimi
                                      :valtakunnallinen_valitavoite nil
                                      :luoja (:id user)})))

(defn- merkitse-valitavoite-valmiiksi! [db user
                                       {:keys [urakka-id id valmis] :as tiedot}]
  (log/info "Merkitään välitavoite valmiiksi: " tiedot)
  (q/merkitse-valmiiksi! db (konv/sql-date (:pvm valmis)) (:kommentti valmis)
                         (:id user) urakka-id id))

(defn- paivita-urakan-valitavoitteet! [db user valitavoitteet urakka-id]
  (doseq [{:keys [id takaraja nimi] :as valitavoite} (filter #(and (> (:id %) 0)
                                                                   (not (:poistettu %))) valitavoitteet)]
    (q/paivita-urakan-valitavoite! db nimi (konv/sql-date takaraja) (:id user) urakka-id id)
    (when (and (get-in valitavoite [:valmis :pvm])
               (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet urakka-id user))
      (merkitse-valitavoite-valmiiksi! db user valitavoite))))

(defn tallenna-urakan-valitavoitteet! [db user {:keys [urakka-id valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (log/debug "Tallenna urakan välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (luo-uudet-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (paivita-urakan-valitavoitteet! db user valitavoitteet urakka-id)
    (hae-urakan-valitavoitteet db user urakka-id)))
