(ns harja.palvelin.palvelut.valitavoitteet
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.valitavoitteet :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defn hae-urakan-valitavoitteet
  "Hakee urakan välitavoitteet sekä valtakunnalliset välitavoitteet"
  [db user urakka-id]
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
                                      :valtakunnallinen_valitavoite nil
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

(defn- poista-poistetut-valtakunnalliset-valitavoitteet
  "Poistaa valtakunnallisen välitavoitteen.

  Välitavoitteeseen linkitetyt urakkakohtaiset välitavoitteet poistetaan vain käynnissä olevista
  ja tulevista urakoista ja vain silloin jos välitavoite ei ole valmistunut.
  Toisin sanoen välitavoite jää näkyviin vanhoihin urakoihin tai jos se on ehditty tehdä valmiiksi."
  [db user valitavoitteet]
  (doseq [poistettava (filter :poistettu valitavoitteet)]
    (let [linkitetyt (into []
                           (map konv/alaviiva->rakenne)
                           (q/hae-valitavoitteeseen-linkitetyt-valitavoitteet db (:id poistettava)))
          urakka-kaynnissa-tai-tulossa? (fn [urakka]
                                          (or (pvm/valissa?
                                                (t/now)
                                                (c/from-date (:alkupvm urakka))
                                                (c/from-date (:loppupvm urakka)))
                                              (pvm/jalkeen? (:alkupvm urakka) (t/now))))
          poistettavat-linkitetyt (filter
                                    (fn [valitavoite]
                                      (and (urakka-kaynnissa-tai-tulossa? (:urakka valitavoite))
                                           (nil? (:valmispvm valitavoite))))
                                    linkitetyt)]
      (q/poista-valtakunnallinen-valitavoite! db
                                              (:id user)
                                              (:id poistettava))
      (doseq [poistettava poistettavat-linkitetyt]
        (q/poista-urakan-valitavoite! db
                                      (:id user)
                                      (get-in poistettava [:urakka :id])
                                      (:id poistettava))))))

(defn- kopioi-valtakunnallinen-kertaluontoinen-valitavoite-urakoihin
  [db user valitavoite valtakunnallinen-valitavoite-id urakat]
  (doseq [urakka urakat]
    (q/lisaa-urakan-valitavoite<! db {:urakka (:id urakka)
                                      :takaraja (konv/sql-date (:takaraja valitavoite))
                                      :nimi (:nimi valitavoite)
                                      :valtakunnallinen_valitavoite valtakunnallinen-valitavoite-id
                                      :luoja (:id user)})))

(defn- luo-uudet-valtakunnalliset-kertaluontoiset-valitavoitteet
  "Luo uudet valtakunnalliset kertaluontoisten välitavoitteet
  Jos takaraja on annettu, kopioidaan välitavoite urakkaan vain jos
  se on valittua tyyppiä, takaraja osuu urakan voimassaoloajalle eikä urakka ole päättynyt.
  Jos takarajaa ei ole annettu, kopioidaan välitavoite kaikkiin
  oikeantyyppisiin käynnissä oleviin ja tuleviin urakoihin."
  [db user valitavoitteet]
  (let [urakat (into []
                     (map #(konv/string->keyword % :tyyppi))
                     (urakat-q/hae-kaynnissa-olevat-ja-tulevat-urakat db))]
    (doseq [{:keys [takaraja nimi urakkatyyppi] :as valitavoite} valitavoitteet]
      (let [id (:id (q/lisaa-valtakunnallinen-kertaluontoinen-valitavoite<!
                      db
                      {:takaraja (konv/sql-date takaraja)
                       :nimi nimi
                       :urakkatyyppi (name urakkatyyppi)
                       :tyyppi "kertaluontoinen"
                       :luoja (:id user)}))
            linkitettavat-urakat (if (:takaraja valitavoite)
                                   (filter
                                     (fn [urakka]
                                       (and
                                         (= (:urakkatyyppi valitavoite) (:tyyppi urakka))
                                         (pvm/valissa? (c/from-date takaraja)
                                                       (c/from-date (:alkupvm urakka))
                                                       (c/from-date (:loppupvm urakka)))
                                         (pvm/ennen? (t/now) (c/from-date (:loppupvm urakka)))))
                                     urakat)
                                   (filter
                                     #(= (:urakkatyyppi valitavoite) (:tyyppi %))
                                     urakat))]
        (kopioi-valtakunnallinen-kertaluontoinen-valitavoite-urakoihin db user valitavoite id linkitettavat-urakat)))))

(defn- kopioi-valtakunnallinen-toistuva-valitavoite-urakoihin
  [db user valitavoite valitavoite-kannassa-id urakat]
  (doseq [urakka urakat]
    (let [urakan-jaljella-olevat-vuodet (range (max (t/year (t/now))
                                                    (t/year (c/from-date (:alkupvm urakka))))
                                               (inc (t/year (c/from-date (:loppupvm urakka)))))]
      (doseq [vuosi urakan-jaljella-olevat-vuodet]
        (log/debug "Lisätään välitavoite urakkaan " (:nimi urakka) " takarajalla "
                   vuosi "-" (:takaraja-toistokuukausi valitavoite) "-" (:takaraja-toistopaiva valitavoite))
        (q/lisaa-urakan-valitavoite<! db {:urakka (:id urakka)
                                          :takaraja (konv/sql-date (c/to-date (t/local-date
                                                                                vuosi
                                                                                (:takaraja-toistokuukausi valitavoite)
                                                                                (:takaraja-toistopaiva valitavoite))))
                                          :nimi (:nimi valitavoite)
                                          :valtakunnallinen_valitavoite valitavoite-kannassa-id
                                          :luoja (:id user)})))))

(defn- luo-uudet-valtakunnalliset-toistuvat-valitavoitteet
  "Luo uudet valtakunnalliset toistuvat välitavoitteet.
   Kopioi välitavoitteen käynnissä oleviin ja tuleviin urakoihin kertaalleen per urakkavuosi"
  [db user valitavoitteet]
  (let [urakat (into []
                       (map #(konv/string->keyword % :tyyppi))
                       (urakat-q/hae-kaynnissa-olevat-ja-tulevat-urakat db))]
    (doseq [{:keys [takaraja nimi urakkatyyppi
                    takaraja-toistopaiva takaraja-toistokuukausi] :as valitavoite} valitavoitteet]
      (let [linkitettavat-urakat (filter
                                   #(= (:urakkatyyppi valitavoite) (:tyyppi %))
                                   urakat)
            id (:id (q/lisaa-valtakunnallinen-toistuva-valitavoite<!
                  db
                  {:takaraja (konv/sql-date takaraja)
                   :nimi nimi
                   :urakkatyyppi (name urakkatyyppi)
                   :takaraja_toistopaiva takaraja-toistopaiva
                   :takaraja_toistokuukausi takaraja-toistokuukausi
                   :tyyppi "toistuva"
                   :luoja (:id user)}))]
        (kopioi-valtakunnallinen-toistuva-valitavoite-urakoihin db user valitavoite id linkitettavat-urakat)))))

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
  (doseq [{:keys [id takaraja takaraja-toistopaiva takaraja-toistokuukausi nimi]} (filter #(> (:id %) 0) valitavoitteet)]
    (q/paivita-valtakunnallinen-valitavoite! db nimi
                                             (konv/sql-date takaraja)
                                             takaraja-toistopaiva
                                             takaraja-toistokuukausi
                                             (:id user)
                                             id)))


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
