(ns harja.palvelin.integraatiot.api.talvihoitoreitit
  "Talvihoitoreittien lisäys API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]))

(defn lisaa-talvihoitoreitti [db data kayttaja parametrit]
  (validointi/tarkista-urakka-ja-kayttaja db (konv/konvertoi->int (:id parametrit)) kayttaja)
  (let [urakka_id (konv/konvertoi->int (:id parametrit))
        kayttaja_id (konv/konvertoi->int (:id kayttaja))
        ;; Tallenna talvihoitoreitin perustiedot
        talvihoitoreitti-id (:id (talvihoitoreitit-q/lisaa-talvihoitoreitti<! db {:nimi (:reittinimi data)
                                                                                  :urakka_id urakka_id
                                                                                  :kayttaja_id kayttaja_id}))
        ;; Lisää kalustot
        _ (doseq [kalusto (:kalusto data)]
          (talvihoitoreitit-q/lisaa-kalusto-talvihoitoreitille<! db
            {:talvihoitoreitti_id talvihoitoreitti-id
             :maara (:kalusto-lkm kalusto)
             :kalustotyyppi (:kalustotyyppi kalusto)}))
        ;; Lisää reitit
        _ (doseq [reitti (:reitti data)]
          (talvihoitoreitit-q/lisaa-reitti-talvihoitoreitille<! db
            {:talvihoitoreitti_id talvihoitoreitti-id
             :tie (:tie reitti)
             :alkuosa (:aosa reitti)
             :alkuetaisyys (:aet reitti)
             :loppuosa (:losa reitti)
             :loppuetaisyys (:let reitti)
             :pituus (:pituus reitti)
             :hoitoluokka (:hoitoluokka reitti)}))

        vastaus (tee-kirjausvastauksen-body {:muut-tiedot {:huomiot [{:tunniste {:id talvihoitoreitti-id}}]}})]
    vastaus))

(defrecord TalvihoitoreittiAPI []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-talvihoitoreitti
      (POST "/api/urakat/:id/talvihoitoreitti" request
        (kasittele-kutsu db integraatioloki :lisaa-talvihoitoreitti request
          json-skeemat/talvihoitoreitti-kirjaus-request
          json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (lisaa-talvihoitoreitti db data kayttaja parametrit))
          :kirjoitus)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-talvihoitoreitti)
    this))
