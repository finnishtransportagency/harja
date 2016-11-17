(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
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
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json])
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
                                (q-yllapitokohteet/hae-kaikki-urakan-yllapitokohteet db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(defn- vaadi-kohde-kuuluu-urakkaan [db urakka-id urakan-tyyppi kohde-id]
  (let [urakan-kohteet (case urakan-tyyppi
                         :paallystys
                         (q-yllapitokohteet/hae-urakkaan-liittyvat-paallystyskohteet db {:urakka urakka-id})
                         :tiemerkinta
                         (q-yllapitokohteet/hae-urakkaan-liittyvat-tiemerkintakohteet db {:urakka urakka-id}))]
    (when-not (some #(= kohde-id %) (map :id urakan-kohteet))
      (virheet/heita-poikkeus virheet/+viallinen-kutsu+
                              {:koodi virheet/+urakkaan-kuulumaton-yllapitokohde+
                               :viesti "Ylläpitokohde ei kuulu urakkaan."}))))

(defn kirjaa-paallystysilmoitus [db kayttaja {:keys [urakka-id kohde-id]} data]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) päällystysilmoitus käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))
  (jdbc/with-db-transaction
    [db db]
    (let [urakka-id (Integer/parseInt urakka-id)
          kohde-id (Integer/parseInt kohde-id)
          urakan-tyyppi (keyword (:tyyppi (first (q-urakat/hae-urakan-tyyppi db urakka-id))))]
      (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
      (vaadi-kohde-kuuluu-urakkaan db urakka-id urakan-tyyppi kohde-id)

      (let [id (ilmoitus/kirjaa-paallystysilmoitus db kayttaja urakka-id kohde-id data)]
        (tee-kirjausvastauksen-body
          {:ilmoitukset (str "Päällystysilmoitus kirjattu onnistuneesti.")
           :id id})))))

(defn- paivita-paallystyksen-aikataulu [db kayttaja kohde-id {:keys [aikataulu] :as data}]
  (let [kohteella-paallystysilmoitus? (q-yllapitokohteet/onko-olemassa-paallystysilmoitus? db kohde-id)]
    (q-yllapitokohteet/paivita-yllapitokohteen-paallystysaikataulu!
      db
      {:kohde_alku (json/aika-string->java-sql-date (:kohde-aloitettu aikataulu))
       :paallystys_alku (json/aika-string->java-sql-date (:paallystys-aloitettu aikataulu))
       :paallystys_loppu (json/aika-string->java-sql-date (:paallystys-valmis aikataulu))
       :valmis_tiemerkintaan (json/pvm-string->java-sql-date (:valmis-tiemerkintaan aikataulu))
       :aikataulu_tiemerkinta_takaraja (json/pvm-string->java-sql-date (:tiemerkinta-takaraja aikataulu))
       :kohde_valmis (json/pvm-string->java-sql-date (:kohde-valmis aikataulu))
       :muokkaaja (:id kayttaja)
       :id kohde-id})
    (if kohteella-paallystysilmoitus?
      (do (q-yllapitokohteet/paivita-yllapitokohteen-paallystysilmoituksen-aikataulu<!
            db
            {:takuupvm (json/pvm-string->java-sql-date (get-in aikataulu [:paallystysilmoitus :takuupvm]))
             :muokkaaja (:id kayttaja)
             :kohde_id kohde-id})
          {})
      {:varoitukset "Kohteella ei ole päällystysilmoitusta, joten sen tietoja ei päivitetä."})))

(defn- paivita-tiemerkinnan-aikataulu [db kayttaja kohde-id {:keys [aikataulu] :as data}]
  (q-yllapitokohteet/paivita-yllapitokohteen-tiemerkintaaikataulu!
    db
    {:tiemerkinta_alku (json/pvm-string->java-sql-date (:tiemerkinta-aloitettu aikataulu))
     :tiemerkinta_loppu (json/pvm-string->java-sql-date (:tiemerkinta-valmis aikataulu))
     :aikataulu_tiemerkinta_takaraja (json/pvm-string->java-sql-date (:tiemerkinta-takaraja aikataulu))
     :muokkaaja (:id kayttaja)
     :id kohde-id})
  {})

(defn- paivita-yllapitokohteen-aikataulu
  "Päivittää ylläpitokohteen aikataulutiedot.
   Palauttaa mapin mahdollisista varoituksista"
  [db kayttaja urakan-tyyppi kohde-id data]
  (log/debug "Kirjataan aikataulu urakalle: " urakan-tyyppi)
  (case urakan-tyyppi
    :paallystys
    (paivita-paallystyksen-aikataulu db kayttaja kohde-id data)
    :tiemerkinta
    (paivita-tiemerkinnan-aikataulu db kayttaja kohde-id data)
    (virheet/heita-poikkeus virheet/+viallinen-kutsu+
                            {:koodi virheet/+viallinen-kutsu+
                             :viesti (str "Urakka ei ole päällystys- tai tiemerkintäurakka, vaan "
                                          (name urakan-tyyppi))})))

(defn- vaadi-urakka-oikeaa-tyyppia [urakka-tyyppi endpoint-urakkatyyppi]
  (when (not= urakka-tyyppi endpoint-urakkatyyppi)
    (virheet/heita-poikkeus virheet/+viallinen-kutsu+
                            {:koodi virheet/+viallinen-kutsu+
                             :viesti (format "Yritettiin kirjata aikataulu urakkatyypille %s, mutta urakan tyyppi on %s.
                             Tiemerkinnän aikataulu voidaan kirjata vain tiemerkintäurakalle ja vastaavasti
                             päällystyksen aikataulu voidaan kirjata vain päällystysurakalle."
                                             (name urakka-tyyppi) (name endpoint-urakkatyyppi))})))

(defn kirjaa-aikataulu [db kayttaja {:keys [urakka-id kohde-id]} data endpoint-urakkatyyppi]
  (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) aikataulu käyttäjän: %s toimesta"
                     urakka-id
                     kohde-id
                     kayttaja))
  (jdbc/with-db-transaction
    [db db]
    (let [urakka-id (Integer/parseInt urakka-id)
          urakan-tyyppi (keyword (:tyyppi (first (q-urakat/hae-urakan-tyyppi db urakka-id))))
          kohde-id (Integer/parseInt kohde-id)]
      (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
      (vaadi-urakka-oikeaa-tyyppia urakan-tyyppi endpoint-urakkatyyppi)
      (vaadi-kohde-kuuluu-urakkaan db urakka-id urakan-tyyppi kohde-id)
      (let [paivitys-vastaus (paivita-yllapitokohteen-aikataulu db kayttaja urakan-tyyppi kohde-id data)]
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
    :kasittely-fn (fn [parametit _ kayttaja db]
                    (hae-yllapitokohteet db parametit kayttaja))}
   {:palvelu :kirjaa-paallystysilmoitus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/paallystysilmoitus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystysilmoituksen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-paallystysilmoitus db kayttaja parametrit data))}
   {:palvelu :kirjaa-paallystyksen-aikataulu
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/aikataulu-paallystys"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystyksen-aikataulun-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-aikataulu db kayttaja parametrit data :paallystys))}
   {:palvelu :kirjaa-tiemerkinnan-aikataulu
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/aikataulu-tiemerkinta"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/tiemerkinnan-aikataulun-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-aikataulu db kayttaja parametrit data :tiemerkinta))}
   {:palvelu :kirjaa-suljettu-tieosuus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/suljettu-tieosuus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/suljetun-tieosuuden-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-suljettu-tieosuus db kayttaja parametrit data))}
   {:palvelu :poista-suljettu-tieosuus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/suljettu-tieosuus"
    :tyyppi :DELETE
    :kutsu-skeema json-skeemat/suljetun-tieosuuden-poisto
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (poista-suljettu-tieosuus db kayttaja parametrit data))}])

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
