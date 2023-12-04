(ns harja.palvelin.integraatiot.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
    [compojure.core :refer [POST GET DELETE]]
    [harja.palvelin.integraatiot.api.tyokalut.json :as json]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.pvm :as pvm]
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

(defn- varmista-etta-ulkoinen-id-on-uniikki
  "Varmistaa että ulkoinen id on uniikki urakkaa, tarkastustyyppiä ja partitiota kohden"
  [db tyyppi urakka-id data]
  ;; Urakoitsjoilta on toisinaan tullut rajapintaan tarkastuksia, joiden ulkoista id:tä on jo aiemmin käytetty samassa urakassa,
  ;; mutta ihan eri aikana (jopa eri vuonna), ja muutkin tiedot ovat täysin erit.
  ;; Jos olemassaolevaa tarkastusta halutaan muokata, aika ei saa muuttua niin paljon,
  ;; että tarkastus siirtyy eri tarkastus-partitioon. Tästä aiheutuu tästä Harjassa tietokantapoikkeus (500).
  ;; Tämä tilanne on syytä tunnistaa palauttaa asianmukainen virheviesti (400).
  (doseq [t (:tarkastukset data)
          :let [tarkastus (:tarkastus t)
                ulkoinen-id (get-in tarkastus [:tunniste :id])
                aika (json/aika-string->java-sql-timestamp (get tarkastus :aika))
                jo-kannassa-oleva (first
                                    (q-tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db {:id ulkoinen-id
                                                                                                   :tyyppi (name tyyppi)
                                                                                                   :urakka-id urakka-id}))]]
    (when (and
            jo-kannassa-oleva
            (not (pvm/samassa-kvartaalissa? aika (:aika jo-kannassa-oleva))))
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+duplikaatti-tarkastus-urakassa+
                          :viesti (format "Urakassa id:llä: %s on jo raportoitu tarkastus samalla tunnisteella %s, mutta eri aikana. Tunnisteen on oltava yksilöllinen urakan ja tarkastustyypin sisällä."
                                    urakka-id, ulkoinen-id)}]})
      (throw (IllegalArgumentException.)))))

(defn kirjaa-tarkastus [db liitteiden-hallinta kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)]
    ;(log/debug (format "Kirjataan tarkastus tyyppiä: %s käyttäjän: %s toimesta. Data: %s" tyyppi (:kayttajanimi kayttaja) data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (varmista-etta-ulkoinen-id-on-uniikki db tyyppi urakka-id data)
    (let [varoitukset (tarkastukset/luo-tai-paivita-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data)]
      (tee-onnistunut-vastaus (join ", " varoitukset)))))

(defn poista-tarkastus [db _ kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)
        ulkoiset-idt (-> data :tarkastusten-tunnisteet)
        kayttaja-id (:id kayttaja)
        kayttajanimi (:kayttajanimi kayttaja)]
    #_ (log/debug (format "Poistetaan tarkastus ulk.id %s tyyppiä: %s käyttäjän: %s toimesta. Data: %s"
                       ulkoiset-idt
                       (name tyyppi)
                       kayttajanimi
                       data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [poistettujen-maara (q-tarkastukset/poista-tarkastus! db kayttaja-id urakka-id (name tyyppi) ulkoiset-idt)
          poistettujen-liitteiden-maara (q-tarkastukset/poista-poistetut-liitteet! db {:urakka-id urakka-id})]
      (let [ilmoitukset (if (pos? poistettujen-maara)
                          (format "Tarkastukset poistettu onnistuneesti. Poistettiin: %s tarkastusta." poistettujen-maara)
                          "Tunnisteita vastaavia tarkastuksia ei löytynyt käyttäjän kirjaamista tarkastuksista.")]
        (tee-kirjausvastauksen-body {:ilmoitukset ilmoitukset})))))

(def palvelut
  [{:palvelu :lisaa-tiestotarkastus
    :api-oikeus :kirjoitus
    :polku "/api/urakat/:id/tarkastus/tiestotarkastus"
    :pyynto-skeema json-skeemat/tiestotarkastuksen-kirjaus
    :tyyppi :tiesto
    :metodi :post}
   {:palvelu :poista-tiestotarkastus
    :polku "/api/urakat/:id/tarkastus/tiestotarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/tiestotarkastuksen-poisto
    :tyyppi :tiesto
    :metodi :delete}
   {:palvelu :lisaa-talvihoitotarkastus
    :polku "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/talvihoitotarkastuksen-kirjaus
    :tyyppi :talvihoito
    :metodi :post}
   {:palvelu :poista-talvihoitotarkastus
    :polku "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/talvihoitotarkastuksen-poisto
    :tyyppi :talvihoito
    :metodi :delete}
   {:palvelu :lisaa-tieturvallisuustarkastus
    :polku "/api/urakat/:id/tarkastus/tieturvallisuustarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/tieturvallisuustarkastuksen-kirjaus
    :tyyppi :tieturvallisuus
    :metodi :post}
   {:palvelu :poista-tieturvallisuustarkastus
    :polku "/api/urakat/:id/tarkastus/tieturvallisuustarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/tieturvallisuustarkastuksen-poisto
    :tyyppi :tieturvallisuus
    :metodi :delete}
   {:palvelu :lisaa-soratietarkastus
    :polku "/api/urakat/:id/tarkastus/soratietarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/soratietarkastuksen-kirjaus
    :tyyppi :soratie
    :metodi :post}
   {:palvelu :poista-soratietarkastus
    :polku "/api/urakat/:id/tarkastus/soratietarkastus"
    :api-oikeus :kirjoitus
    :pyynto-skeema json-skeemat/soratietarkastuksen-poisto
    :tyyppi :soratie
    :metodi :delete}])

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku pyynto-skeema tyyppi metodi api-oikeus]} palvelut]
      (let [kasittele (fn [kasittele-tarkastus-fn request]
                        (kasittele-kutsu db integraatioloki palvelu request
                                         pyynto-skeema json-skeemat/kirjausvastaus
                                         (fn [parametrit data kayttaja db]
                                           (kasittele-tarkastus-fn db liitteiden-hallinta kayttaja tyyppi parametrit data))
                          api-oikeus))]
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
