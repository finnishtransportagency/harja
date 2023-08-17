(ns harja.palvelin.palvelut.hairioilmoitukset
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.muokkaustiedot :as muok]
            [harja.domain.hairioilmoitus :as hairio]
            [specql.core :as specql]
            [specql.op :as op]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defn- hae-kaikki-hairioilmoitukset [db user tarkista-oikeus?]
  (when tarkista-oikeus?
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user))
  (reverse (sort-by ::hairio/pvm (specql/fetch db ::hairio/hairioilmoitus
                                               hairio/sarakkeet
                                               {}))))

(defn- hae-voimassaoleva-hairioilmoitus [db user]
  (oikeudet/ei-oikeustarkistusta!) ;; Kuka vaan saa hakea tuoreimman häiriön
  {:hairioilmoitus (-> (hae-kaikki-hairioilmoitukset db user false)
                     (hairio/voimassaoleva-hairio))})

(defn- aseta-kaikki-hairioilmoitukset-pois [db user]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (specql/update! db ::hairio/hairioilmoitus
                  {::hairio/voimassa? false}
                  {::hairio/voimassa? true})
  (hae-kaikki-hairioilmoitukset db user false))

(defn- aseta-hairioilmoitus-pois [db user {::hairio/keys [id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (specql/update! db ::hairio/hairioilmoitus
    {::hairio/voimassa? false}
    {::hairio/id id})
  (log/debug "Asetettiin häiriöilmoitus pois päältä: " id)
  (hae-kaikki-hairioilmoitukset db user false))

(defn- aseta-vanhat-hairioilmoitukset-pois [db]
  (specql/update! db ::hairio/hairioilmoitus
    {::hairio/voimassa? false}
    {::hairio/voimassa? true
     ::hairio/loppuaika (op/< (c/to-sql-time (t/now)))}))

(defn- validoi-ajat [db alkuaika loppuaika]
  (if (= loppuaika alkuaika)
    [{:virhe (str "Alkuaika " alkuaika " ja loppuaika " loppuaika " eivät voi olla samat")}]
    (if (pvm/ennen? loppuaika alkuaika)
    [{:virhe (str "Alkuajan " alkuaika " pitäisi olla ennen loppuaikaa " loppuaika)}]
    (if (hairio/onko-paallekkainen alkuaika loppuaika (specql/fetch db ::hairio/hairioilmoitus
                                                           hairio/sarakkeet
                                                           {::hairio/voimassa? true}))
      [{:virhe (str "Alkuaika " alkuaika " ja loppuaika " loppuaika " leikkaavat olemassaolevaa häiriöilmoitusta")}]))))
(defn- aseta-hairioilmoitus [db user {::hairio/keys [viesti tyyppi alkuaika loppuaika]}]
  (let [validointi-virhe (validoi-ajat db alkuaika loppuaika)]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-hairioilmoitukset user)
  (aseta-vanhat-hairioilmoitukset-pois db) ; TODO: onko hyvä paikka tehdä tämä?
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
      (hae-kaikki-hairioilmoitukset db user false))
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
        (hae-kaikki-hairioilmoitukset db user true)))

    (julkaise-palvelu
      http
      :hae-voimassaoleva-hairioilmoitus
      (fn [user _]
        (hae-voimassaoleva-hairioilmoitus db user)))

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
      :aseta-hairioilmoitus
      :aseta-kaikki-hairioilmoitukset-pois
      :aseta-hairioilmoitus-pois)
    this))
