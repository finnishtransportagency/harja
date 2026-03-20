(ns harja.palvelin.palvelut.hairioilmoitukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.hairioilmoitus :as hairio]
            [specql.core :as specql]
            [specql.op :as op]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defn- hae-kaikki-hairioilmoitukset [db user tarkista-oikeus?]
  (when tarkista-oikeus?
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user))
  (specql/fetch db ::hairio/hairioilmoitus
                hairio/sarakkeet
                {}
                {::specql/order-by ::hairio/pvm :specql.core/order-direction :desc
                 ::specql/limit 20}))

(defn- hae-hairioilmoitukset-ryhmiteltyna
  "Hakee kaikki häiriöilmoitukset ja ryhmittelee ne UI:n tarpeiden mukaan:
   - :voimassaolevat-tyypeittain - voimassaolevat häiriöt ja tiedotteet
   - :tulevat - tulevat ilmoitukset
   - :vanhat - päättyneet/poistetut ilmoitukset"
  [db user]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (let [kaikki (hae-kaikki-hairioilmoitukset db user false)]
    {:voimassaolevat-tyypeittain (hairio/voimassaolevat-hairiot-tyypeittain kaikki)
     :tulevat (vec (hairio/tulevat-hairiot kaikki))
     :vanhat (vec (hairio/vanhat-hairiot kaikki))}))

(defn- hae-voimassaoleva-hairioilmoitus [db user]
  (oikeudet/ei-oikeustarkistusta!) ;; Kuka vaan saa hakea tuoreimman häiriön
  (let [kaikki (hae-kaikki-hairioilmoitukset db user false)
        tyypeittain (hairio/voimassaolevat-hairiot-tyypeittain kaikki)]
    {:hairioilmoitus (or (:hairio tyypeittain) (:tiedote tyypeittain))
     :hairioilmoitukset-tyypeittain tyypeittain}))

(defn- aseta-kaikki-hairioilmoitukset-pois [db user]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (specql/update! db ::hairio/hairioilmoitus
                  {::hairio/voimassa? false}
                  {::hairio/voimassa? true})
  (hae-hairioilmoitukset-ryhmiteltyna db user))

(defn- aseta-hairioilmoitus-pois [db user {::hairio/keys [id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (specql/update! db ::hairio/hairioilmoitus
    {::hairio/voimassa? false}
    {::hairio/id id})
  (log/debug "Asetettiin häiriöilmoitus pois päältä: " id)
  (hae-hairioilmoitukset-ryhmiteltyna db user))

(defn- aseta-vanhat-hairioilmoitukset-pois [db]
  (specql/update! db ::hairio/hairioilmoitus
    {::hairio/voimassa? false}
    {::hairio/voimassa? true
     ::hairio/loppuaika (op/< (c/to-sql-time (t/now)))}))

(defn- validoi-ajat
  ([db tyyppi alkuaika loppuaika]
   (validoi-ajat db tyyppi alkuaika loppuaika nil))
  ([db tyyppi alkuaika loppuaika id]
   (let [haku (if-not id
                {::hairio/voimassa? true ::hairio/tyyppi (or tyyppi :hairio)}
                ;; Excluudaa muokattava rivi validoinnista 
                ;; muokattavaa riviä ei tarkisteta ristiriitojen osalta, koska se olisi aina ristiriidassa itsensä kanssa
                {::hairio/voimassa? true ::hairio/id (op/not= id) ::hairio/tyyppi (or tyyppi :hairio)})]
     (cond
       (= loppuaika alkuaika)
       [{:virhe "Alkuaika ja loppuaika eivät voi olla samat."}]

       (pvm/ennen? loppuaika alkuaika)
       [{:virhe "Alkuajan pitäisi olla ennen loppuaikaa."}]

       (hairio/onko-paallekkainen? alkuaika loppuaika (specql/fetch db ::hairio/hairioilmoitus hairio/sarakkeet haku))
       [{:virhe (if (= :hairio tyyppi)
                  "Annettu aikaväli on päällekäinen olemassaolevan häiriöilmoituksen kanssa."
                  "Annettu aikaväli on päällekäinen olemassaolevan ilmoituksen kanssa.")}]

       :else nil))))

(defn- tallenna-hairioilmoitukset [db user {:keys [tiedot]}]
  (log/info "Tallennetaan häiriöilmoitus: tiedot:" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (let [virhe
        (reduce
          (fn [_ rivi]
            (let [{::hairio/keys [viesti tyyppi alkuaika loppuaika id]} rivi
                  poistettu? (:poistettu rivi)
                  validointi-virhe (validoi-ajat db tyyppi alkuaika loppuaika id)]
              (cond
                ;; Lopeta päivitys, jos tapahtuu virhe
                validointi-virhe (reduced validointi-virhe)

                ;; Käyttäjä haluaa poistaa rivin 
                poistettu?
                (do
                  (specql/delete! db ::hairio/hairioilmoitus {::hairio/id id})
                  (log/debug "Poistettiin häiriöilmoitus"))

                ;; Käyttäjä päivittää olemassa olevaa riviä 
                (and id (> id 0))
                (do
                  (specql/update! db ::hairio/hairioilmoitus
                    {::hairio/viesti viesti
                     ::hairio/pvm (c/to-sql-date (t/now))
                     ::hairio/voimassa? true
                     ::hairio/tyyppi (or tyyppi :hairio)
                     ::hairio/alkuaika alkuaika
                     ::hairio/loppuaika loppuaika}
                    {::hairio/id id})
                  (log/debug "Päivitettiin häiriöilmoitus")))))
          nil
          tiedot)]
    ;; Palautetaan vastaus, jos tapahtui virhe, päivitys lopetetaan siihen riviin
    (if virhe virhe (hae-hairioilmoitukset-ryhmiteltyna db user))))


(defn- aseta-hairioilmoitus [db user {::hairio/keys [viesti tyyppi alkuaika loppuaika]}]
  (let [validointi-virhe (validoi-ajat db tyyppi alkuaika loppuaika)]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (aseta-vanhat-hairioilmoitukset-pois db)
  (if (nil? validointi-virhe)
    (do
      (specql/insert! db ::hairio/hairioilmoitus
        {::hairio/viesti viesti
         ::hairio/pvm (c/to-sql-date (t/now))
         ::hairio/voimassa? true
         ::hairio/tyyppi (or tyyppi :hairio)
         ::hairio/alkuaika alkuaika
         ::hairio/loppuaika loppuaika})
      (log/debug "Asetettiin häiriöilmoitus")
      (hae-hairioilmoitukset-ryhmiteltyna db user))
    (do
      (log/debug "Häiriöilmoituksen luonti epäonnistui")
      validointi-virhe))))

(defrecord Hairioilmoitukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-hairioilmoitukset
      (fn [user _]
        (hae-hairioilmoitukset-ryhmiteltyna db user)))

    (julkaise-palvelu
      http
      :hae-voimassaoleva-hairioilmoitus
      (fn [user _]
        (hae-voimassaoleva-hairioilmoitus db user))
      {:lokita-kysely? false})
    
    (julkaise-palvelu
      http
      :tallenna-hairioilmoitukset
      (fn [user tiedot]
        (tallenna-hairioilmoitukset db user tiedot)))

    (julkaise-palvelu
      http
      :aseta-hairioilmoitus
      (fn [user tiedot]
        (aseta-hairioilmoitus db user tiedot)))

    (julkaise-palvelu
      http
      :aseta-kaikki-hairioilmoitukset-pois
      (fn [user _]
        (aseta-kaikki-hairioilmoitukset-pois db user)))

  (julkaise-palvelu
    http
    :aseta-hairioilmoitus-pois
    (fn [user tiedot]
      (aseta-hairioilmoitus-pois db user tiedot)))
  this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hairioilmoitukset
      :hae-voimassaoleva-hairioilmoitus
      :tallenna-hairioilmoitukset
      :aseta-hairioilmoitus
      :aseta-kaikki-hairioilmoitukset-pois
      :aseta-hairioilmoitus-pois)
    this))
