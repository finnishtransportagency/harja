(ns harja.palvelin.integraatiot.api.liitteet
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liite-tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.siltatarkastukset :as silta-q]
            [harja.palvelin.integraatiot.api.siltatarkastukset :as siltatarkastukset]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [throw+]]))

(defn- heita-liitevirhe [viesti]
  (throw+ {:type virheet/+viallinen-kutsu+
           :virheet [{:koodi virheet/+virheellinen-liite-koodi+
                      :viesti viesti}]}))

(defn- tallenna-siltatarkastuskohteen-liitteet
  [{id :id} {:keys [liite-taydennys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (str (get-in liite-taydennys [:kategoria-tunniste :id]))
        kohde (get liite-taydennys :siltatarkastuskohde)
        kohde-id (get siltatarkastukset/api-kohde->numero kohde)
        liite (:liite liite-taydennys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (when-not kohde
      (heita-liitevirhe "Siltatarkastuksen kohde puuttuu"))
    (when-not kohde-id
      (heita-liitevirhe (format "Tuntematon siltatarkastuksen kohde: %s" kohde)))
    (let [siltatarkastus (first (silta-q/hae-siltatarkastus-ulkoisella-idlla-ja-urakalla
                                  db ulkoinen-id urakka-id))]
      (when-not siltatarkastus
        (heita-liitevirhe (format "Siltatarkastusta ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-siltatarkastuskohteelle
          db
          liitteiden-hallinta
          kayttaja
          urakka-id
          (:id siltatarkastus)
          kohde-id
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn vastaanota-liite [parametrit data kayttaja db liitteiden-hallinta]
  (let [kategoria (get-in data [:liite-taydennys :kategoria])]
    (if (= "siltatarkastuskohde" kategoria)
      (tallenna-siltatarkastuskohteen-liitteet
        parametrit data kayttaja db liitteiden-hallinta)
      (heita-liitevirhe (format "Liitteen kategoriaa ei vielä tueta: %s" kategoria)))))

(defrecord Liitteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :api-vastaanota-liite
      (POST "/api/urakat/:id/liite" request
        (kasittele-kutsu db integraatioloki :vastaanota-liite request
          json-skeemat/liitteen-vastaanotto json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (vastaanota-liite parametrit data kayttaja db liitteiden-hallinta))
          :kirjoitus)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-vastaanota-liite)
    this))
