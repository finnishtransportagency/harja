(ns harja.palvelin.integraatiot.api.talvihoitoreitit-api
  "Talvihoitoreittien lisäys API:n kautta"
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [PUT DELETE]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]))

(defn paivita-talvihoitoreitti [db data kayttaja_id urakka_id]
  (let [_ (println "paivita-talvihoitoreitti :: data" data)
        ;; Haetaan talvihoitoreitin perustiedot ulkoisen id:n perusteella
        talvihoitoreitti (first (talvihoitoreitit-q/hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka_id
                                                                                              :ulkoinen_id (:tunniste data)}))

        ;; Jos talvihoitoreitti löytyy, niin deletoidaan kaikki kalusto ja reitit, ja tallennetaan ne uudestaan.
        _ (when talvihoitoreitti
            (talvihoitoreitit-q/poista-talvihoitoreitin-sijainnit! db {:talvihoitoreitti_id (:id talvihoitoreitti)})
            ;; Päivitä talvihoitoreitin perustiedot
            (talvihoitoreitit-q/paivita-talvihoitoreitti<! db {:talvihoitoreitti_id (:id talvihoitoreitti)
                                                               :nimi (:reittinimi data)
                                                               :kayttaja_id kayttaja_id})
            ;; Lisää kalustot ja reitit
            (talvihoitoreitit-q/lisaa-kalustot-ja-reitit db (:id talvihoitoreitti) data))
        vastaus (tee-kirjausvastauksen-body {:ilmoitukset "Talvihoitoreitit päivitetty onnistuneesti."})]
    vastaus))

(defn tallenna-talvihoitoreitti [db data kayttaja_id urakka_id]
  (let [;; Tallenna talvihoitoreitin perustiedot
        talvihoitoreitti-id (:id (talvihoitoreitit-q/lisaa-talvihoitoreitti-tietokantaan db data urakka_id kayttaja_id))
        ;; Lisää kalustot ja reitit
        _ (talvihoitoreitit-q/lisaa-kalustot-ja-reitit db talvihoitoreitti-id data)
        _ (println "lisaa-talvihoitoreitti :: lisätty onnistuneesti" data)
        vastaus (tee-kirjausvastauksen-body {:muut-tiedot {:huomiot [{:tunniste {:id talvihoitoreitti-id}}]}})]
    vastaus))

(defn lisaa-talvihoitoreitti
  "Otetaan lisäys ja päivitys vastaan ja päätellään, että kumpi toimenpide tehdään."
  [db data kayttaja parametrit]
  (validointi/tarkista-urakka-ja-kayttaja db (konv/konvertoi->int (:id parametrit)) kayttaja)
  (jdbc/with-db-transaction [db db]
    (let [_ (println "lisaa-talvihoitoreitti :: data" data)
          _ (println "lisaa-talvihoitoreitti :: parametrit" parametrit)
          urakka_id (konv/konvertoi->int (:id parametrit))
          _ (println "lisaa-talvihoitoreitti :: urakka_id" urakka_id)
          kayttaja_id (konv/konvertoi->int (:id kayttaja))

          ;; Varmista, että talvihoitoreittiä ei ole jo olemassa
          talvihoitoreitti (talvihoitoreitit-q/hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka_id
                                                                                         :ulkoinen_id (:tunniste data)})]
      (if (and
            (not (nil? talvihoitoreitti))
            (not (empty? talvihoitoreitti)))
        (paivita-talvihoitoreitti db data kayttaja_id urakka_id)
        (tallenna-talvihoitoreitti db data kayttaja_id urakka_id)))))

(defn poista-talvihoitoreitit [db data kayttaja parametrit]
  (validointi/tarkista-urakka-ja-kayttaja db (konv/konvertoi->int (:id parametrit)) kayttaja)
  (jdbc/with-db-transaction [db db]
    (let [urakka_id (konv/konvertoi->int (:id parametrit))
          ulkoiset_idt (:talvihoitoreittien-tunnisteet data)
          ;; Poista talvihoitoreitit
          _ (doseq [uid ulkoiset_idt]
              (talvihoitoreitit-q/poista-talvihoitoreitti! db {:ulkoinen_id uid
                                                              :urakka_id urakka_id}))
          vastaus (tee-kirjausvastauksen-body {:ilmoitukset "Talvihoitoreitit poistettu onnistuneesti."})]
      vastaus)))

(defrecord TalvihoitoreittiAPI []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-talvihoitoreitti
      (PUT "/api/urakat/:id/talvihoitoreitti" request
        (kasittele-kutsu db integraatioloki :lisaa-talvihoitoreitti request
          json-skeemat/talvihoitoreitti-kirjaus-request
          json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (lisaa-talvihoitoreitti db data kayttaja parametrit))
          :kirjoitus)))

    (julkaise-reitti
      http :poista-talvihoitoreitti
      (DELETE "/api/urakat/:id/talvihoitoreitti" request
        (kasittele-kutsu db integraatioloki :poista-talvihoitoreitti request
          json-skeemat/talvihoitoreitti-poisto-request
          json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (poista-talvihoitoreitit db data kayttaja parametrit))
          :kirjoitus)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :lisaa-talvihoitoreitti
      :poista-talvihoitoreitti)
    this))
