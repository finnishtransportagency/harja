(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defn hae-urakan-valitavoitteet [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (into []
        (map konv/alaviiva->rakenne)
        (q/hae-urakan-valitavoitteet db urakka-id)))

(defn hae-valtakunnalliset-valitavoitteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-valitavoitteet user)
  (into []
        (map #(konv/string->keyword % :urakkatyyppi :tyyppi))
        (q/hae-valtakunnalliset-valitavoitteet db)))

(defn merkitse-valmiiksi! [db user {:keys [urakka-id valitavoite-id valmis-pvm kommentti] :as tiedot}]
  (log/info "merkitse valmiiksi: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (jdbc/with-db-transaction [c db]
    (and (= 1 (q/merkitse-valmiiksi! db (konv/sql-date valmis-pvm) kommentti
                                     (:id user) urakka-id valitavoite-id))
         (hae-urakan-valitavoitteet db user urakka-id))))

(defn- poista-poistetut-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (q/poista-urakan-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- luo-uudet-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [takaraja nimi valtakunnallinen-valitavoite-id]} (filter
                                    #(and (< (:id %) 0)
                                          (not (:poistettu %)))
                                    valitavoitteet)]
    (q/lisaa-urakan-valitavoite<! db {:urakka urakka-id
                                      :takaraja (konv/sql-date takaraja)
                                      :nimi nimi
                                      :valtakunnallinen_valitavoite valtakunnallinen-valitavoite-id
                                      :luoja (:id user)})))

(defn- paivita-urakan-valitavoitteet [db user valitavoitteet urakka-id]
  (doseq [{:keys [id takaraja nimi]} (filter #(> (:id %) 0) valitavoitteet)]
    (q/paivita-urakan-valitavoite! db nimi (konv/sql-date takaraja) (:id user) urakka-id id)))

(defn tallenna-urakan-valitavoitteet! [db user {:keys [urakka-id valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (log/debug "Tallenna urakan välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (luo-uudet-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (paivita-urakan-valitavoitteet db user valitavoitteet urakka-id)
    (hae-urakan-valitavoitteet db user urakka-id)))

(defn- poista-poistetut-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  ;; TODO Pitää selventää mitä tämän poistamisesta seuraa
  #_(doseq [poistettava (filter :poistettu valitavoitteet)]
      (q/poista-valtakunnallinen-valitavoite! db (:id user) urakka-id (:id poistettava))))

(defn- kopioi-valtakunnallinen-valitavoite-urakoihin [db user valitavoite valitavoite-kannassa-id urakat]
  (doseq [urakka urakat]
    (luo-uudet-urakan-valitavoitteet db user
                                     (merge valitavoite
                                            {:valtakunnallinen-valitavoite-id valitavoite-kannassa-id})
                                     (:id urakka))))

(defn- luo-uudet-valtakunnalliset-kertaluontoiset-valitavoitteet [db user valitavoitteet]
  (let [urakat (hae-kertaluontoisten-valitavoitteiden-kohdeurakat)]
    (doseq [{:keys [takaraja nimi urakkatyyppi] :as valitavoite} valitavoitteet]
     (let [id (:id (q/lisaa-valtakunnallinen-kertaluontoinen-valitavoite<!
                     db
                     {:takaraja (konv/sql-date takaraja)
                      :nimi nimi
                      :urakkatyyppi (name urakkatyyppi)
                      :tyyppi "kertaluontoinen"
                      :luoja (:id user)}))
           kohdeurakat (filter
                         (fn [urakka]
                           (pvm/valissa? takaraja
                                         (c/from-date (:alkupvm urakka))
                                         (c/from-date (:loppupvm urakka))))
                         urakat)]
       (kopioi-valtakunnallinen-valitavoite-urakoihin db user valitavoite id kohdeurakat)))))

(defn- luo-uudet-valtakunnalliset-toistuvat-valitavoitteet [db user valitavoitteet]
  (doseq [{:keys [takaraja nimi urakkatyyppi takaraja-toistopaiva takaraja-toistokuukausi]} valitavoitteet]
    (q/lisaa-valtakunnallinen-toistuva-valitavoite<! db {:takaraja (konv/sql-date takaraja)
                                                         :nimi nimi
                                                         :urakkatyyppi (name urakkatyyppi)
                                                         :takaraja_toistopaiva takaraja-toistopaiva
                                                         :takaraja_toistokuukausi takaraja-toistokuukausi
                                                         :tyyppi "toistuva"
                                                         :luoja (:id user)})))

(defn- luo-uudet-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  (luo-uudet-valtakunnalliset-kertaluontoiset-valitavoitteet db
                                                             user
                                                             (filter #(and (= (:tyyppi %) :kertaluontoinen)
                                                                           (< (:id %) 0)
                                                                           (not (:poistettu %)))
                                                                     valitavoitteet))
  (luo-uudet-valtakunnalliset-toistuvat-valitavoitteet db
                                                       user
                                                       (filter #(and (= (:tyyppi %) :toistuva)
                                                                     (< (:id %) 0)
                                                                     (not (:poistettu %)))
                                                               valitavoitteet)))

(defn- paivita-valtakunnalliset-valitavoitteet [db user valitavoitteet]
  ;; TODO Mahdollisesti halutaan, että tämän päivittäminen vaikuttaa vain uusiin urakoihin?
  #_(doseq [{:keys [id takaraja nimi]} (filter #(> (:id %) 0) valitavoitteet)]
      (q/paivita-valtakunnallinen-valitavoite! db nimi (konv/sql-date takaraja) (:id user) urakka-id id)))


(defn tallenna-valtakunnalliset-valitavoitteet! [db user {:keys [valitavoitteet]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-valitavoitteet user)
  (log/debug "Tallenna valtakunnalliset välitavoitteet " (pr-str valitavoitteet))
  (jdbc/with-db-transaction [db db]
    (poista-poistetut-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (luo-uudet-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (paivita-valtakunnalliset-valitavoitteet db user valitavoitteet)
    (hae-valtakunnalliset-valitavoitteet db user)))

(defrecord Valitavoitteet []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-urakan-valitavoitteet
                      (fn [user urakka-id]
                        (hae-urakan-valitavoitteet (:db this) user urakka-id)))
    (julkaise-palvelu (:http-palvelin this) :hae-valtakunnalliset-valitavoitteet
                      (fn [user _]
                        (hae-valtakunnalliset-valitavoitteet (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :merkitse-valitavoite-valmiiksi
                      (fn [user tiedot]
                        (merkitse-valmiiksi! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-urakan-valitavoitteet
                      (fn [user tiedot]
                        (tallenna-urakan-valitavoitteet! (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this) :tallenna-valtakunnalliset-valitavoitteet
                      (fn [user tiedot]
                        (tallenna-valtakunnalliset-valitavoitteet! (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-valtakunnalliset-valitavoitteet
                     :hae-urakan-valitavoitteet
                     :merkitse-valitavoite-valmiiksi
                     :tallenna-urakan-valitavoitteet
                     :tallenna-valtakunnalliset-valitavoitteet)
    this))
