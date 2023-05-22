(ns harja.palvelin.integraatiot.api.tyomaapaivakirja
  "Työmaapäiväkirjan hallinta API:n kautta. Alkuun kirjaus ja päivitys mahdollisuudet"
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [POST PUT]]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date aika-string->java-sql-date]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(s/def ::urakka-id #(and (integer? %) (pos? %)))

(defn- tarkista-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:id "Urakka-id puuttuu"})
  (when (not (s/valid? ::urakka-id (:id parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Urakka-id muodossa: %s. Anna muodossa: 1" (:id parametrit))})))

;; TODO: Validoi sisään tuleva data
(defn validoi-tyomaapaivakirja [data]
  )

(defn tallenna-tyomaapaivakirja [db urakka-id data kayttaja tyomaapaivakirja-id]
  (let [tyomaapaivakirja-id (konv/konvertoi->int tyomaapaivakirja-id)
        versio (get-in data [:tunniste :versio]) ; Jokaiselle payloadilla on oma versionsa
        tyomaapaivakirja {:urakka_id urakka-id
                          :kayttaja (:id kayttaja)
                          :paivamaara (pvm-string->java-sql-date (get-in data [:tunniste :paivamaara]))
                          :ulkoinen-id (get-in data [:tunniste :id])
                          :id tyomaapaivakirja-id}

        tyomaapaivakirja-id (if tyomaapaivakirja-id
                              ;; Päivitä vanhaa
                              (do
                                (tyomaapaivakirja-kyselyt/paivita-tyomaapaivakirja<! db tyomaapaivakirja)
                                (:id tyomaapaivakirja))
                              ;; ELSE: Lisää uusi
                              (:id (tyomaapaivakirja-kyselyt/lisaa-tyomaapaivakirja<! db tyomaapaivakirja)))

        ;; Tallennetaan kalusto
        kalustot (get-in data [:kaluston-kaytto])
        _ (doall (for [k kalustot
                       :let [kalusto (:kalusto k)
                             kalusto (-> kalusto
                                       (assoc :aloitus (aika-string->java-sql-date (:aloitus kalusto)))
                                       (assoc :lopetus (aika-string->java-sql-date (:lopetus kalusto)))
                                       (merge {:versio versio
                                               :tyomaapaivakirja_id tyomaapaivakirja-id
                                               :urakka_id urakka-id}))]]
                   (tyomaapaivakirja-kyselyt/lisaa-kalusto<! db kalusto)))
        ;; Tallennetaan päivystäjä
        paivystajat (get-in data [:paivystajan-tiedot])
        _ (doall (for [p paivystajat
                       :let [paivystaja (:paivystaja p)
                             paivystaja (-> paivystaja
                                          (assoc :aloitus (aika-string->java-sql-date (:aloitus paivystaja)))
                                          (assoc :lopetus (aika-string->java-sql-date (:lopetus paivystaja))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-paivystaja<! db (merge
                                                                     paivystaja
                                                                     {:versio versio
                                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                      :urakka_id urakka-id}))))

        ;; Tallennetaan työnjohtaja
        tyonjohtajat (get-in data [:tyonjohtajan-tiedot])
        _ (doall (for [j tyonjohtajat
                       :let [johtaja (:tyonjohtaja j)
                             johtaja (-> johtaja
                                       (assoc :aloitus (aika-string->java-sql-date (:aloitus johtaja)))
                                       (assoc :lopetus (aika-string->java-sql-date (:lopetus johtaja))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-tyonjohtaja<! db (merge
                                                                      johtaja
                                                                      {:versio versio
                                                                       :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                       :urakka_id urakka-id}))))

        ;; Tallennetaan sää
        saatiedot (get-in data [:saatiedot])
        _ (doall (for [s saatiedot
                       :let [saa (:saatieto s)
                             saa (-> saa
                                   (assoc :havaintoaika (aika-string->java-sql-date (:havaintoaika saa)))
                                   (assoc :aseman-tietojen-paivityshetki (aika-string->java-sql-date (:aseman-tietojen-paivityshetki saa))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-saatiedot<! db (merge
                                                                    saa
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id}))))

        ;; Tallennetaan poikkeuksellinen sää
        poikkeussaa (get-in data [:poikkeukselliset-saahavainnot])
        _ (doall (for [p poikkeussaa
                       :let [poikkeus (:poikkeuksellinen-saahavainto p)
                             poikkeus (-> poikkeus
                                        (assoc :havaintoaika (aika-string->java-sql-date (:havaintoaika poikkeus))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-poikkeussaa<! db (merge
                                                                      poikkeus
                                                                      {:versio versio
                                                                       :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                       :urakka_id urakka-id}))))

        ;; Tallennetaan tiestön toimenpiteet
        tieston-toimenpiteet (get-in data [:tieston-toimenpiteet])
        _ (doall (for [t tieston-toimenpiteet
                       :let [toimenpide (:tieston-toimenpide t)
                             toimenpide (-> toimenpide
                                          (assoc :aloitus (aika-string->java-sql-date (:aloitus toimenpide)))
                                          (assoc :lopetus (aika-string->java-sql-date (:lopetus toimenpide)))
                                          (assoc :tyyppi "yleinen")
                                          (assoc :toimenpiteet nil) ;; Ei voida lisätä toimenpiteitä.
                                          (assoc :tehtavat (->
                                                             (map (comp :id :tehtava) (:tehtavat toimenpide))
                                                             (konv/seq->array))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-tie-toimenpide<! db (merge
                                                                         toimenpide
                                                                         {:versio versio
                                                                          :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                          :urakka_id urakka-id}))))
        ;; Tallennetaan muut toimenpiteet
        muut-toimenpiteet (get-in data [:tieston-muut-toimenpiteet])
        _ (doall (for [t muut-toimenpiteet
                       :let [toimenpide (:tieston-muu-toimenpide t)
                             toimenpide (-> toimenpide
                                          (assoc :aloitus (aika-string->java-sql-date (:aloitus toimenpide)))
                                          (assoc :lopetus (aika-string->java-sql-date (:lopetus toimenpide)))
                                          (assoc :tyyppi "muu")
                                          (assoc :tehtavat nil) ;; Ei voida lisätä tehtäviä
                                          (assoc :toimenpiteet (->
                                                                 (map (comp :kuvaus :tehtava) (:tehtavat toimenpide))
                                                                 (konv/seq->array))))]]
                   (tyomaapaivakirja-kyselyt/lisaa-tie-toimenpide<! db (merge
                                                                         toimenpide
                                                                         {:versio versio
                                                                          :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                          :urakka_id urakka-id}))))

        ;; Tallennetaan onnettomuudet
        onnettomuudet (get-in data [:onnettomuudet])
        _ (doall (for [o onnettomuudet]
                   (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                                    (:onnettomuus o)
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id
                                                                     :tyyppi "onnettomuus"}))))

        ;; Tallennetaan liikenteenohjaus muutokset
        liikenteenohjaus-muutokset (get-in data [:liikenteenohjaus-muutokset])
        _ (doall (for [l liikenteenohjaus-muutokset]
                   (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                                    (:liikenteenohjaus-muutos l)
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id
                                                                     :tyyppi "liikenteenohjausmuutos"}))))

        ;; Tallennetaan palautteet
        palautteet (get-in data [:palautteet])
        _ (doall (for [p palautteet]
                   (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                                    (:palaute p)
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id
                                                                     :tyyppi "palaute"}))))

        ;; Tallennetaan viranomaisen avustaminen
        viranomaisen-avustaminen (get-in data [:viranomaisen-avustaminen])
        _ (doall (for [v viranomaisen-avustaminen]
                   (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                                    (:viranomaisen-avustus v)
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id
                                                                     :tyyppi "viranomaisen_avustus"}))))

        ;; Tallennetaan tilaan yhteydenotot
        tilaajan-yhteydenotot (get-in data [:tilaajan-yhteydenotot])
        _ (doall (for [t tilaajan-yhteydenotot]
                   (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                                    (:tilaajan-yhteydenotto t)
                                                                    {:versio versio
                                                                     :tyomaapaivakirja_id tyomaapaivakirja-id
                                                                     :urakka_id urakka-id
                                                                     :tyyppi "tilaajan-yhteydenotto"}))))

        ;; Tallennetaan muut kirjaukset
        muut_kirjaukset (get-in data [:muut-kirjaukset :kuvaus])
        _ (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db {:kuvaus muut_kirjaukset
                                                          :versio versio
                                                          :tyomaapaivakirja_id tyomaapaivakirja-id
                                                          :urakka_id urakka-id
                                                          :tyyppi "muut_kirjaukset"})]
    tyomaapaivakirja-id))

(defn kirjaa-tyomaapaivakirja [db {:keys [id tid] :as parametrit} data kayttaja]
  (validoi-tyomaapaivakirja data)
  (tarkista-parametrit parametrit)
  (let [urakka-id (konv/konvertoi->int id)
        tyomaapaivakirja (:tyomaapaivakirja data)
        ;; Tallenna
        tyomaapaivakirja-id (jdbc/with-db-transaction [db db]
                              (tallenna-tyomaapaivakirja db urakka-id tyomaapaivakirja kayttaja tid))



        ;; Muodosta vastaus
        vastaus {:status "OK"
                 :tyomaapaivakirja-id tyomaapaivakirja-id}
        ]
    vastaus))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-tyomaapaivakirja
      (POST "/api/urakat/:id/tyomaapaivakirja" request
        (kasittele-kutsu db
          integraatioloki
          :kirjaa-tyomaapaivakirja
          request
          json-skeemat/tyomaapaivakirja-kirjaus-request
          json-skeemat/tyomaapaivakirja-kirjaus-response
          (fn [parametrit data kayttaja db]
            (kirjaa-tyomaapaivakirja db parametrit data kayttaja))))
      true)
    (julkaise-reitti
      http :paivita-tyomaapaivakirja
      (PUT "/api/urakat/:id/tyomaapaivakirja/:tid" request
        (kasittele-kutsu db
          integraatioloki
          :paivita-tyomaapaivakirja
          request
          json-skeemat/tyomaapaivakirja-paivitys-request
          json-skeemat/tyomaapaivakirja-kirjaus-response
          (fn [parametrit data kayttaja db]
            (kirjaa-tyomaapaivakirja db parametrit data kayttaja))))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :kirjaa-tyomaapaivakirja
      :paivita-tyomaapaivakirja)
    this))
