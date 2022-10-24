(ns harja.palvelin.palvelut.valitavoitteet.urakkakohtaiset-valitavoitteet
  "Palvelu urakkakohtaisten välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-valitavoitteet
  "Hakee urakan välitavoitteet sekä valtakunnalliset välitavoitteet"
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (let [vastaus (into []
                      (map konv/alaviiva->rakenne)
                      (q/hae-urakan-valitavoitteet db urakka-id))]
    vastaus))

(defn- poista-poistetut-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (q/poista-urakan-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- merkitse-valitavoite-valmiiksi! [db user urakka-id
                                        {:keys [nimi id valmispvm valmis-kommentti] :as tiedot}]
  (when valmispvm
    (log/debug "Merkitään välitavoite valmiiksi: " nimi)
    (q/merkitse-valmiiksi! db
                           (when valmispvm
                             (konv/sql-date valmispvm))
                           (when valmispvm
                             valmis-kommentti)
                           (:id user) urakka-id id)))

(defn- luo-uudet-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [aloituspvm takaraja nimi yllapitokohde-id] :as valitavoite} (filter
                                                    #(and (not (id-olemassa? (:id %)))
                                                          (not (:poistettu %)))
                                                    valitavoitteet)]
    (log/debug "Luodaan uusi välitavoite: " nimi)
    (let [lisatty-vt-id (:id (q/lisaa-urakan-valitavoite<! db {:urakka urakka-id
                                                               :aloituspvm (konv/sql-date aloituspvm)
                                                               :takaraja (konv/sql-date takaraja)
                                                               :nimi nimi
                                                               :yllapitokohde yllapitokohde-id
                                                               :valtakunnallinen_valitavoite nil
                                                               :luoja (:id user)}))]
      (when (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet urakka-id user)
        (merkitse-valitavoite-valmiiksi! db user urakka-id (assoc valitavoite :id lisatty-vt-id))))))

(defn- paivita-urakan-valitavoitteet! [db user valitavoitteet urakka-id]
  (let [valitavoitteet (filter (comp not :valtakunnallinen-id) valitavoitteet)]
    (doseq [{:keys [id takaraja nimi aloituspvm yllapitokohde-id] :as valitavoite}
            (filter #(and (id-olemassa? (:id %))
                          (not (:poistettu %)))
                    valitavoitteet)]
      (log/debug "Päivitetään välitavoite: " nimi)
      (q/paivita-urakan-valitavoite! db
                                     {:nimi nimi
                                      :takaraja (konv/sql-date takaraja)
                                      :aloituspvm (konv/sql-date aloituspvm)
                                      :muokkaaja (:id user)
                                      :yllapitokohde yllapitokohde-id
                                      :urakka urakka-id
                                      :id id})
      (when (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet urakka-id user)
        (merkitse-valitavoite-valmiiksi! db user urakka-id valitavoite)))))

(defn- paivita-urakan-valtakunnalliset-valitavoitteet! [db user valitavoitteet urakka-id]
  (let [valitavoitteet (filter :valtakunnallinen-id valitavoitteet)]
    (doseq [{:keys [id takaraja nimi] :as valitavoite}
            (filter #(and (id-olemassa? (:id %))
                          (not (:poistettu %)))
                    valitavoitteet)]

      (q/paivita-urakan-valitavoite! db
                                     {:nimi nimi
                                      :takaraja (konv/sql-date takaraja)
                                      :aloituspvm nil
                                      :yllapitokohde nil
                                      :muokkaaja (:id user)
                                      :urakka urakka-id
                                      :id id})
      (when (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet urakka-id user)
        (merkitse-valitavoite-valmiiksi! db user urakka-id valitavoite)))))

(defn tallenna-urakan-valitavoitteet! [db user {:keys [urakka-id valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (doseq [valitavoite valitavoitteet]
    (when-let [yllapitokohde-id (:yllapitokohde-id valitavoite)]
      (ypk-yleiset/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka-id yllapitokohde-id)))
  (log/debug "Tallenna urakan välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (luo-uudet-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (paivita-urakan-valitavoitteet! db user valitavoitteet urakka-id)
    (paivita-urakan-valtakunnalliset-valitavoitteet! db user valitavoitteet urakka-id)
    (hae-urakan-valitavoitteet db user urakka-id)))
