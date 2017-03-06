(ns harja.palvelin.integraatiot.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.kyselyt.tarkastukset :as q-tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [clojure.string :as str]))

(defn tee-onnistunut-vastaus [varoitukset]
  (tee-kirjausvastauksen-body {:ilmoitukset "Tarkastukset kirjattu onnistuneesti"
                               :varoitukset (when-not (empty? varoitukset) varoitukset)}))

(defn tallenna-mittaustulokset-tarkastukselle [db id tyyppi uusi? mittaus]
  (case tyyppi
    :talvihoito (q-tarkastukset/luo-tai-paivita-talvihoitomittaus db id uusi?
                                                                  (-> mittaus
                                                                      (assoc :lampotila-tie (:lampotilaTie mittaus))
                                                                      (assoc :lampotila-ilma (:lampotilaIlma mittaus))))
    :soratie (q-tarkastukset/luo-tai-paivita-soratiemittaus db id uusi? mittaus)
    nil))

(defn kasittele-tarkastukset
  "Käsittelee annetut tarkastukset ja palautta listan string-varoituksia."
  [db liitteiden-hallinta kayttaja tyyppi urakka-id data]
  (keep
    (fn [rivi]
      (let [tarkastus (:tarkastus rivi)
            ulkoinen-id (-> tarkastus :tunniste :id)]
        (try
          (jdbc/with-db-transaction [db db]
            (let [{tarkastus-id :id}
                  (first
                    (q-tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db ulkoinen-id (name tyyppi) (:id kayttaja)))
                  uusi? (nil? tarkastus-id)]

              (let [aika (json/aika-string->java-sql-date (:aika tarkastus))
                    tr-osoite (sijainnit/hae-tierekisteriosoite db (:alkusijainti tarkastus) (:loppusijainti tarkastus))
                    geometria (if tr-osoite (:geometria tr-osoite)
                                            (sijainnit/tee-geometria (:alkusijainti tarkastus) (:loppusijainti tarkastus)))
                    id (q-tarkastukset/luo-tai-paivita-tarkastus
                         db kayttaja urakka-id
                         {:id tarkastus-id
                          :lahde "harja-api"
                          :ulkoinen-id ulkoinen-id
                          :tyyppi tyyppi
                          :aika aika
                          :tarkastaja (json/henkilo->nimi (:tarkastaja tarkastus))
                          :sijainti geometria
                          :tr {:numero (:tie tr-osoite)
                               :alkuosa (:aosa tr-osoite)
                               :alkuetaisyys (:aet tr-osoite)
                               :loppuosa (:losa tr-osoite)
                               :loppuetaisyys (:let tr-osoite)}
                          :havainnot (:havainnot tarkastus)
                          :laadunalitus (let [alitus (:laadunalitus tarkastus)]
                                          (if (nil? alitus)
                                            (not (str/blank? (:havainnot tarkastus)))
                                            alitus))
                          :nayta-urakoitsijalle true})
                    liitteet (:liitteet tarkastus)]

                (tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id id kayttaja liitteet)
                (tallenna-mittaustulokset-tarkastukselle db id tyyppi uusi? (:mittaus rivi))
                (when-not tr-osoite
                  (format "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla (alku: %s, loppu %s)."
                          (:alkusijainti tarkastus) (:loppusijainti tarkastus))))))
          (catch Throwable t
            (log/warn t "Virhe tarkastuksen lisäämisessä")
            (throw t)))))
    (:tarkastukset data)))

(defn kirjaa-tarkastus [db liitteiden-hallinta kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)]
    (log/debug (format "Kirjataan tarkastus tyyppiä: %s käyttäjän: %s toimesta. Data: %s" tyyppi (:kayttajanimi kayttaja) data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [varoitukset (kasittele-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data)]
      (tee-onnistunut-vastaus (join ", " varoitukset)))))

(defn poista-tarkastus [db _ kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)
        ulkoiset-idt (-> data :tarkastusten-tunnisteet)
        kayttaja-id (:id kayttaja)
        kayttajanimi (:kayttajanimi kayttaja)]
    (log/debug (format "Poistetaan tarkastus ulk.id %s tyyppiä: %s käyttäjän: %s toimesta. Data: %s"
                       ulkoiset-idt
                       tyyppi
                       kayttajanimi
                       data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [poistettujen-maara (q-tarkastukset/poista-tarkastus! db kayttaja-id ulkoiset-idt)]
      (let [ilmoitukset (if (pos? poistettujen-maara)
                          (format "Tarkastukset poistettu onnistuneesti. Poistettiin: %s tarkastusta." poistettujen-maara)
                          "Tunnisteita vastaavia tarkastuksia ei löytynyt käyttäjän kirjaamista tarkastuksista.")]
        (tee-kirjausvastauksen-body {:ilmoitukset ilmoitukset})))))

(def palvelut
  [{:palvelu :lisaa-tiestotarkastus
    :polku "/api/urakat/:id/tarkastus/tiestotarkastus"
    :pyynto-skeema json-skeemat/tiestotarkastuksen-kirjaus
    :tyyppi :tiesto
    :metodi :post}
   {:palvelu :poista-tiestotarkastus
    :polku "/api/urakat/:id/tarkastus/tiestotarkastus"
    :pyynto-skeema json-skeemat/tiestotarkastuksen-poisto
    :tyyppi :tiesto
    :metodi :delete}
   {:palvelu :lisaa-talvihoitotarkastus
    :polku "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :pyynto-skeema json-skeemat/talvihoitotarkastuksen-kirjaus
    :tyyppi :talvihoito
    :metodi :post}
   {:palvelu :poista-talvihoitotarkastus
    :polku "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :pyynto-skeema json-skeemat/talvihoitotarkastuksen-poisto
    :tyyppi :talvihoito
    :metodi :delete}
   {:palvelu :lisaa-soratietarkastus
    :polku "/api/urakat/:id/tarkastus/soratietarkastus"
    :pyynto-skeema json-skeemat/soratietarkastuksen-kirjaus
    :tyyppi :soratie
    :metodi :post}
   {:palvelu :poista-soratietarkastus
    :polku "/api/urakat/:id/tarkastus/soratietarkastus"
    :pyynto-skeema json-skeemat/soratietarkastuksen-poisto
    :tyyppi :soratie
    :metodi :delete}])

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku pyynto-skeema tyyppi metodi]} palvelut]
      (let [kasittele (fn [kasittele-tarkastus-fn request]
                        (kasittele-kutsu db integraatioloki palvelu request
                                         pyynto-skeema json-skeemat/kirjausvastaus
                                         (fn [parametrit data kayttaja db]
                                           (kasittele-tarkastus-fn db liitteiden-hallinta kayttaja tyyppi parametrit data))))]
        (julkaise-reitti http palvelu
                         (condp = metodi
                           :post
                           (POST polku request (kasittele kirjaa-tarkastus request))
                           :delete
                           (DELETE polku request (kasittele poista-tarkastus request))))))

    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu palvelut))
    this))
