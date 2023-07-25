(ns harja.palvelin.integraatiot.api.tyomaapaivakirja
  "Työmaapäiväkirjan hallinta API:n kautta. Alkuun kirjaus ja päivitys mahdollisuudet"
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST PUT]]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.api.tyokalut.json :as tyokalut-json]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(s/def ::urakka-id #(and (integer? %) (pos? %)))

(defn- tarkista-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:id "Urakka-id puuttuu"})
  (when (not (s/valid? ::urakka-id (konv/konvertoi->int (:id parametrit))))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Urakka-id muodossa: %s. Anna muodossa: 1" (:id parametrit))})))

(defn validoi-saa [saatiedot]
  (doseq [s saatiedot
          :let [saa (:saatieto s)]]
    (when (or (< (:ilman-lampotila saa) -80) (> (:ilman-lampotila saa) 80))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s." (:ilman-lampotila saa))}))

    (when (and (not (nil? (:tien-lampotila saa)))
            (or (< (:tien-lampotila saa) -80) (> (:tien-lampotila saa) 80)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt %s." (:tien-lampotila saa))}))

    (when (and (not (nil? (:keskituuli saa)))
            (or (< (:keskituuli saa) 0) (> (:keskituuli saa) 150)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Keskituuli täytyy olla väliltä 0 - 150. Oli nyt %s." (:keskituuli saa))}))

    (when (and (not (nil? (:sateen-olomuoto saa)))
            (or (< (:sateen-olomuoto saa) 0) (> (:sateen-olomuoto saa) 150)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt %s." (:sateen-olomuoto saa))}))

    (when (and (not (nil? (:sadesumma saa)))
            (or (< (:sadesumma saa) 0) (> (:sadesumma saa) 10000)))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt %s." (:sadesumma saa))}))))

(defn validoi-kalusto [kalustot]
  (doseq [k kalustot
          :let [kalusto (:kalusto k)
                aloitus (tyokalut-json/pvm-string->joda-date (:aloitus kalusto))
                lopetus (tyokalut-json/pvm-string->joda-date (:lopetus kalusto))]]

    (when (pvm/ennen? lopetus aloitus)
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Kaluston lopetusaika täytyy olla aloitusajan jälkeen.")}))

    (when (or (< (:tyokoneiden-lkm kalusto) 0) (> (:tyokoneiden-lkm kalusto) 2000))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Työkoneiden lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt %s." (:tyokoneiden-lkm kalusto))}))

    (when (or (< (:lisakaluston-lkm kalusto) 0) (> (:lisakaluston-lkm kalusto) 2000))
      (virheet/heita-viallinen-apikutsu-poikkeus
        {:koodi virheet/+puutteelliset-parametrit+
         :viesti (format "Lisäkaluston lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt %s." (:lisakaluston-lkm kalusto))}))))

;; TODO: Validoi sisään tuleva data
(defn validoi-tyomaapaivakirja [data]
  (println "validoi-tyomaapäivkäirja :: data" (pr-str data))
  (validoi-saa (get-in data [:saatiedot]))
  (validoi-kalusto (get-in data [:kaluston-kaytto]))

  )

(defn- hae-tyomaapaivakirjan-versiotiedot [db kayttaja tiedot]
  (validointi/tarkista-urakka-ja-kayttaja db (:urakka_id tiedot) kayttaja)
  (let [hakuparametrit {:urakka_id (:urakka_id tiedot)
                        :paivamaara (pvm-string->java-sql-date (:paivamaara tiedot))}
        _ (log/debug "hae-tyomaapaivakirjan-versiotiedot :: hakuparametrit" (pr-str hakuparametrit))
        versiotiedot (first (tyomaapaivakirja-kyselyt/hae-tyomaapaivakirjan-versiotiedot db hakuparametrit))
        versionro (if (or (nil? versiotiedot)) {:versio nil
                                                :tyomaapaivakirja_id nil} versiotiedot)]
    versionro))

(defn- tallenna-kalusto [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [k (get-in data [:kaluston-kaytto])
          :let [kalusto (:kalusto k)
                kalusto (-> kalusto
                          (assoc :aloitus (aika-string->java-sql-date (:aloitus kalusto)))
                          (assoc :lopetus (aika-string->java-sql-date (:lopetus kalusto)))
                          (merge {:versio versio
                                  :tyomaapaivakirja_id tyomaapaivakirja-id
                                  :urakka_id urakka-id}))]]
    (tyomaapaivakirja-kyselyt/lisaa-kalusto<! db kalusto)))

(defn- tallenna-paivystajat [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:paivystajan-tiedot])
          :let [paivystaja (:paivystaja p)
                paivystaja (-> paivystaja
                             (assoc :aloitus (aika-string->java-sql-date (:aloitus paivystaja)))
                             (assoc :lopetus (aika-string->java-sql-date (:lopetus paivystaja))))]]
    (tyomaapaivakirja-kyselyt/lisaa-paivystaja<! db (merge
                                                      paivystaja
                                                      {:versio versio
                                                       :tyomaapaivakirja_id tyomaapaivakirja-id
                                                       :urakka_id urakka-id}))))

(defn- tallenna-tyonjohtajat [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [j (get-in data [:tyonjohtajan-tiedot])
          :let [johtaja (:tyonjohtaja j)
                johtaja (-> johtaja
                          (assoc :aloitus (aika-string->java-sql-date (:aloitus johtaja)))
                          (assoc :lopetus (aika-string->java-sql-date (:lopetus johtaja))))]]
    (tyomaapaivakirja-kyselyt/lisaa-tyonjohtaja<! db (merge
                                                       johtaja
                                                       {:versio versio
                                                        :tyomaapaivakirja_id tyomaapaivakirja-id
                                                        :urakka_id urakka-id}))))

(defn- tallenna-saatiedot [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [s (get-in data [:saatiedot])
          :let [saa (:saatieto s)
                saa (-> saa
                      (assoc :havaintoaika (aika-string->java-sql-date (:havaintoaika saa)))
                      (assoc :aseman-tietojen-paivityshetki (aika-string->java-sql-date (:aseman-tietojen-paivityshetki saa))))]]
    (tyomaapaivakirja-kyselyt/lisaa-saatiedot<! db (merge
                                                     saa
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id}))))

(defn- tallenna-poikkeussaa [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:poikkeukselliset-saahavainnot])
          :let [poikkeus (:poikkeuksellinen-saahavainto p)
                poikkeus (-> poikkeus
                           (assoc :havaintoaika (aika-string->java-sql-date (:havaintoaika poikkeus))))]]
    (tyomaapaivakirja-kyselyt/lisaa-poikkeussaa<! db (merge
                                                       poikkeus
                                                       {:versio versio
                                                        :tyomaapaivakirja_id tyomaapaivakirja-id
                                                        :urakka_id urakka-id}))))

(defn- tallenna-toimenpiteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [t (get-in data [:tieston-toimenpiteet])
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

(defn- tallenna-muut-toimenpiteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [t (get-in data [:tieston-muut-toimenpiteet])
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

(defn- tallenna-onnettomuudet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [o (get-in data [:onnettomuudet])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:onnettomuus o)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "onnettomuus"}))))

(defn- tallenna-liikenteenohjaus-muutokset [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [l (get-in data [:liikenteenohjaus-muutokset])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:liikenteenohjaus-muutos l)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "liikenteenohjausmuutos"}))))

(defn- tallenna-palautteet [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [p (get-in data [:palautteet])]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (:palaute p)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi "palaute"}))))

(defn- tallenna-tapahtuma [db data versio tyomaapaivakirja-id urakka-id paa-avain toissijainen-avain tyyppi]
  (doseq [v (paa-avain data)]
    (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db (merge
                                                     (toissijainen-avain v)
                                                     {:versio versio
                                                      :tyomaapaivakirja_id tyomaapaivakirja-id
                                                      :urakka_id urakka-id
                                                      :tyyppi tyyppi}))))

(defn- tallenna-muut-kirjaukset [db data versio tyomaapaivakirja-id urakka-id]
  (tyomaapaivakirja-kyselyt/lisaa-tapahtuma<! db {:kuvaus (get-in data [:muut-kirjaukset :kuvaus])
                                                  :versio versio
                                                  :tyomaapaivakirja_id tyomaapaivakirja-id
                                                  :urakka_id urakka-id
                                                  :tyyppi "muut_kirjaukset"}))

(defn- tallenna-toimeksiannot [db data versio tyomaapaivakirja-id urakka-id]
  (doseq [v (get-in data [:viranomaisen-avustaminen])]
    (tyomaapaivakirja-kyselyt/lisaa-toimeksianto<! db (merge
                                                        (:viranomaisen-avustus v)
                                                        {:versio versio
                                                         :tyomaapaivakirja_id tyomaapaivakirja-id
                                                         :urakka_id urakka-id
                                                         :kuvaus (:kuvaus (:viranomaisen-avustus v))
                                                         :aika (:tunnit (:viranomaisen-avustus v))}))))

(defn tallenna-tyomaapaivakirja [db urakka-id data kayttaja tyomaapaivakirja-id]
  (let [_ (log/debug "tallenna-tyomaapaivakirja :: data" (pr-str data))
        tyomaapaivakirja-id (konv/konvertoi->int tyomaapaivakirja-id)
        versio (get-in data [:tunniste :versio]) ; Jokaiselle payloadilla on oma versionsa
        versiotiedot (hae-tyomaapaivakirjan-versiotiedot db kayttaja {:urakka_id urakka-id
                                                                      :paivamaara (get-in data [:tunniste :paivamaara])})
        tyomaapaivakirja {:urakka_id urakka-id
                          :kayttaja (:id kayttaja)
                          :paivamaara (pvm-string->java-sql-date (get-in data [:tunniste :paivamaara]))
                          :ulkoinen-id (get-in data [:tunniste :id])
                          :versio (get-in data [:tunniste :versio])
                          :id tyomaapaivakirja-id}
        ;; Varmista, että annettu versio on suurempi, kuin kannassa oleva
        _ (when tyomaapaivakirja-id
            (if (not= (get-in data [:tunniste :versio])
                  (inc (:versio versiotiedot)))
              (throw+ {:type virheet/+vaara-versio-tyomaapaivakirja+ :virheet [{:koodi virheet/+vaara-versio-tyomaapaivakirja-virhe-koodi+
                                                                               :viesti "Työmaapäiväkirjan versio ei täsmää"}]})))
        tyomaapaivakirja-id (if tyomaapaivakirja-id
                              ;; Päivitä vanhaa
                              (do
                                (tyomaapaivakirja-kyselyt/paivita-tyomaapaivakirja<! db tyomaapaivakirja)
                                (:id tyomaapaivakirja))
                              ;; ELSE: Lisää uusi
                              (try
                                (:id (tyomaapaivakirja-kyselyt/lisaa-tyomaapaivakirja<! db tyomaapaivakirja))
                                (catch Exception e
                                  (throw+ {:type virheet/+duplikaatti-tyomaapaivakirja+ :virheet [{:koodi virheet/+duplikaatti-tyomaapaivakirja-virhe-koodi+
                                                                                                   :viesti "Duplikaatti versio ja päivämäärä"}]}))))

        ;; Tallennetaan jokainen osio omalla versionumerolla.
        _ (tallenna-kalusto db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-paivystajat db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-tyonjohtajat db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-saatiedot db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-poikkeussaa db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-toimenpiteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-muut-toimenpiteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-onnettomuudet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-liikenteenohjaus-muutokset db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-palautteet db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-toimeksiannot db data versio tyomaapaivakirja-id urakka-id)
        _ (tallenna-tapahtuma db data versio tyomaapaivakirja-id urakka-id :tilaajan-yhteydenotot :tilaajan-yhteydenotto "tilaajan-yhteydenotto")
        _ (tallenna-muut-kirjaukset db data versio tyomaapaivakirja-id urakka-id)]
    tyomaapaivakirja-id))

(defn kirjaa-tyomaapaivakirja [db {:keys [id tid] :as parametrit} data kayttaja]
  (validoi-tyomaapaivakirja data)
  (tarkista-parametrit parametrit)
  (let [urakka-id (konv/konvertoi->int id)
        tyomaapaivakirja (:tyomaapaivakirja data)
        ;; Tallenna
        tyomaapaivakirja-id (jdbc/with-db-transaction [db db]
                              (tallenna-tyomaapaivakirja db urakka-id tyomaapaivakirja kayttaja tid))
        ;; Muodosta OK vastaus - Error vastaus pärähtää throwssa ja sen käsittelee kutsu-kasittelijä
        vastaus {:status "OK"
                 :tyomaapaivakirja-id tyomaapaivakirja-id}]
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
    (julkaise-palvelu (:http-palvelin this)
      :hae-tyomaapaivakirjan-versiotiedot
      (fn [user tiedot]
        (hae-tyomaapaivakirjan-versiotiedot (:db this) user tiedot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :kirjaa-tyomaapaivakirja
      :paivita-tyomaapaivakirja
      :hae-tyomaapaivakirjan-versiotiedot)
    this))
