(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-timestamp]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat :as yllapitokohdesanomat]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.suljetut-tieosuudet :as q-suljetut-tieosuudet]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.kasittely.paallystysilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import (org.postgresql.util PSQLException)))

(defn hae-yllapitokohteet [db parametit kayttaja]
  (let [urakka-id (Integer/parseInt (:id parametit))]
    (log/debug (format "Haetaan urakan (id: %s) ylläpitokohteet käyttäjälle: %s (id: %s)."
                       urakka-id
                       (:kayttajanimi kayttaja)
                       (:id kayttaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q-yllapitokohteet/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(defn- vaadi-kohde-kuuluu-urakkaan [db urakka-id kohde-id]
  (let [urakan-kohteet (map :urakka (q-yllapitokohteet/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))]
    (log/debug "Tarkistetaan, että annettu ylläpitokohde " kohde-id " kuuluu väitettyyn urakkaan " urakka-id)
    (when (or (empty? urakan-kohteet)
              (not (some #(= urakka-id %) urakan-kohteet)))
      (throw (SecurityException. "Ylläpitokohde ei kuulu väitettyyn urakkaan.")))))

(defn kirjaa-paallystysilmoitus [db kayttaja {:keys [urakka-id kohde-id]} data]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) päällystysilmoitus käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))
  (let [urakka-id (Integer/parseInt urakka-id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction
      [db db]
      (vaadi-kohde-kuuluu-urakkaan db urakka-id kohde-id)
      (let [kohde-id (Integer/parseInt kohde-id)
            id (ilmoitus/kirjaa-paallystysilmoitus db kayttaja urakka-id kohde-id data)]
        (tee-kirjausvastauksen-body
          {:ilmoitukset (str "Päällystysilmoitus kirjattu onnistuneesti.")
           :id id})))))

(defn- paivita-paallystyksen-aikataulu [db kayttaja kohde-id data]
  (let [kohteella-paallystysilmoitus? (q-yllapitokohteet/onko-olemassa-paallystysilmoitus? db kohde-id)]
    (q-yllapitokohteet/paivita-yllapitokohteen-paallystysaikataulu!
        db
        {:paallystys_alku (:paallystys-alku data)
         :paallystys_loppu (:paallystys-loppu data)
         :kohde_valmis (:kohde-valmis data)
         :valmis_tiemerkintaan (:valmis-tiemerkintaan data)
         :muokkaaja (:id kayttaja)
         :id kohde-id})
      (if kohteella-paallystysilmoitus?
        (do (q-yllapitokohteet/paivita-yllapitokohteen-paallystysilmoituksen-aikataulu<!
              db
              {:aloituspvm (:aloituspvm data)
               :valmispvm_paallystys (:valmispvm_paallystys data)
               :valmispvm_kohde (:valmispvm_kohde data)
               :takuupvm (:takuupvm data)
               :muokkaaja (:id kayttaja)
               :kohde_id kohde-id})
            {})
        {:varoitukset "Kohteella ei ole päällystysilmoitusta, joten sen tietoja ei päivitetä."})))

(defn- paivita-tiemerkinnan-aikataulu [db kayttaja kohde-id data]
  (q-yllapitokohteet/paivita-yllapitokohteen-tiemerkintaaikataulu!
    db
    {:tiemerkinta_alku (:tiemerkinta-alku data)
     :tiemerkinta_loppu (:tiemerkinta-loppu data)
     :muokkaaja (:id kayttaja)
     :id kohde-id})
  {})

(defn- paivita-yllapitokohteen-aikataulu
  "Päivittää ylläpitokohteen aikataulutiedot.
   Palauttaa mapin mahdollisista varoituksista"
  [db kayttaja urakka-id kohde-id data]
  (let [urakan-tyyppi (:tyyppi (first (q-urakat/hae-urakan-tyyppi db urakka-id)))]
    (log/debug "Kirjataan aikataulu urakalle: " urakan-tyyppi)
    (case (keyword urakan-tyyppi)
      :paallystys
      (paivita-paallystyksen-aikataulu db kayttaja kohde-id data)
      :tiemerkinta
      (paivita-tiemerkinnan-aikataulu db kayttaja kohde-id data)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+viallinen-kutsu+
                          :viesti (str "Urakka ei ole päällystys- tai tiemerkintäurakka, vaan "
                                       urakan-tyyppi)}]}))))

(defn kirjaa-aikataulu [db kayttaja {:keys [urakka-id kohde-id]} data]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) aikataulu käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))
  (let [urakka-id (Integer/parseInt urakka-id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction
      [db db]
      (vaadi-kohde-kuuluu-urakkaan db urakka-id kohde-id)
      (let [kohde-id (Integer/parseInt kohde-id)
            paivitys-vastaus (paivita-yllapitokohteen-aikataulu db kayttaja urakka-id kohde-id data)]
        (tee-kirjausvastauksen-body
          (merge {:ilmoitukset (str "Aikataulu kirjattu onnistuneesti.")}
                 paivitys-vastaus))))))

(defn hae-tr-osoite [db alkukoordinaatit loppukoordinaatit]
  (try
    (first (q-tieverkko/hae-tr-osoite-valille db
                                              (:x alkukoordinaatit) (:y alkukoordinaatit)
                                              (:x loppukoordinaatit) (:y loppukoordinaatit)
                                              10000))
    (catch PSQLException e
      (log/error e "Virhe hakiessa tierekisteriosoitetta suljetulle tieosuudelle"))))

(defn kirjaa-suljettu-tieosuus [db kayttaja {:keys [urakka-id kohde-id]} {:keys [otsikko suljettu-tieosuus]}]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) suljettu tieosuus käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))

  (let [urakka-id (Integer/parseInt urakka-id)
        kohde-id (Integer/parseInt kohde-id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (validointi/tarkista-urakan-kohde db urakka-id kohde-id)

    (let [jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
          alkukoordinaatit (:koordinaatit (:alkuaidan-sijainti suljettu-tieosuus))
          loppukoordinaatit (:koordinaatit (:loppuaidan-sijainti suljettu-tieosuus))
          tr-osoite (hae-tr-osoite db alkukoordinaatit loppukoordinaatit)
          parametrit {:jarjestelma jarjestelma
                      :osuusid (:id suljettu-tieosuus)
                      :alkux (:x alkukoordinaatit)
                      :alkuy (:y alkukoordinaatit)
                      :loppux (:x loppukoordinaatit)
                      :loppuy (:y loppukoordinaatit)
                      :asetettu (aika-string->java-sql-timestamp (:aika suljettu-tieosuus))
                      :kaistat (konv/seq->array (:kaistat suljettu-tieosuus))
                      :ajoradat (konv/seq->array (:ajoradat suljettu-tieosuus))
                      :yllapitokohde kohde-id
                      :kirjaaja (:id kayttaja)
                      :tr_tie (:tie tr-osoite)
                      :tr_aosa (:aosa tr-osoite)
                      :tr_aet (:aet tr-osoite)
                      :tr_losa (:losa tr-osoite)
                      :tr_let (:let tr-osoite)}]

      (if (q-suljetut-tieosuudet/onko-olemassa? db {:id (:id suljettu-tieosuus) :jarjestelma jarjestelma})
        (q-suljetut-tieosuudet/paivita-suljettu-tieosuus! db parametrit)
        (q-suljetut-tieosuudet/luo-suljettu-tieosuus<! db parametrit))
      (let [vastaus (cond-> {:ilmoitukset (str "Suljettu tieosuus kirjattu onnistuneesti.")}
                            (nil? tr-osoite)
                            (assoc :varoitukset "Annetulle tieosuudelle ei saatu haettua tierekisteriosoitetta."))]
        (tee-kirjausvastauksen-body vastaus)))))

(defn poista-suljettu-tieosuus [db kayttaja {:keys [urakka-id kohde-id]} {:keys [otsikko suljettu-tieosuus]}]
  (log/debug (format "Poistetaan urakan (id: %s) kohteelta (id: %s) suljettu tieosuus käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))

  (let [urakka-id (Integer/parseInt urakka-id)
        kohde-id (Integer/parseInt kohde-id)
        jarjestelma (get-in otsikko [:lahettaja :jarjestelma])
        id (:id suljettu-tieosuus)
        parametrit {:jarjestelma jarjestelma
                    :osuusid id
                    :poistettu (aika-string->java-sql-timestamp (:aika suljettu-tieosuus))
                    :poistaja (:id kayttaja)}]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (validointi/tarkista-urakan-kohde db urakka-id kohde-id)
    (validointi/tarkista-suljettu-tieosuus db id jarjestelma)
    (q-suljetut-tieosuudet/merkitse-suljettu-tieosuus-poistetuksi! db parametrit)
    (tee-kirjausvastauksen-body
      {:ilmoitukset (str "Suljettu tieosuus poistettu onnistuneesti.")})))

(def palvelut
  [{:palvelu :hae-yllapitokohteet
    :polku "/api/urakat/:id/yllapitokohteet"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/urakan-yllapitokohteiden-haku-vastaus
    :kasittely-fn (fn [parametit _ kayttaja db] (hae-yllapitokohteet db parametit kayttaja))}
   {:palvelu :kirjaa-paallystysilmoitus
    ;; FIXME Lisää mahdollisuus ottaa vastaan / päivittää POT-lomakkeen perusosan päivämäärät
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/paallystysilmoitus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystysilmoituksen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-paallystysilmoitus db kayttaja parametrit data))}
   {:palvelu :kirjaa-yllapidon-aikataulu
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/aikataulu"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/yllapidon-aikataulun-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-aikataulu db kayttaja parametrit data))}
   {:palvelu :kirjaa-suljettu-tieosuus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/suljettu-tieosuus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/suljetun-tieosuuden-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-suljettu-tieosuus db kayttaja parametrit data))}
   {:palvelu :poista-suljettu-tieosuus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/suljettu-tieosuus"
    :tyyppi :DELETE
    :kutsu-skeema json-skeemat/suljetun-tieosuuden-poisto
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (poista-suljettu-tieosuus db kayttaja parametrit data))}])

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
