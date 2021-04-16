(ns harja.palvelin.integraatiot.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.tarkastukset :as q-tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.kasittely.tarkastukset :as tarkastukset]))

(defn tee-onnistunut-vastaus [varoitukset]
  (tee-kirjausvastauksen-body {:ilmoitukset "Tarkastukset kirjattu onnistuneesti"
                               :varoitukset (when-not (empty? varoitukset) varoitukset)}))

(defn kirjaa-tarkastus [db liitteiden-hallinta kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)]
    (log/debug (format "Kirjataan tarkastus tyyppiä: %s käyttäjän: %s toimesta. Data: %s" tyyppi (:kayttajanimi kayttaja) data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [varoitukset (tarkastukset/luo-tai-paivita-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data)]
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
    (let [poistettujen-maara (q-tarkastukset/poista-tarkastus! db kayttaja-id urakka-id ulkoiset-idt)]
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
