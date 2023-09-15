(ns harja.palvelin.palvelut.tyomaapaivakirja
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]))

(defn kasittele-tyomaapaivakirjan-tila
  "Käsitellään päiväkirjan tila
   Palauttaa passatun päiväkirjan mutta lisätyllä :tila avaimella
   Myöhästyneeksi merkataan ne työmaapäiväkirjat, jotka eivät ole tuleet seuraavan arkipäivän klo 12.00 mennessä. 
   Eli perjantaina tehdyn työmaapäiväkirjan suhteen ajatellaan niin, että se ei ole myöhässä lauantaina klo 15.00 
   eikä sunnuntaina klo 15.00 vaan vasta maanantaina klo 12.01. Eli arkipäivä on se merkitsevä tekijä. Otetaan huomioon myös arkipyhät.

   Tilat: 'puuttuu' / 'ok' / 'myohassa'
   paivakirja: Päiväkirjan data
   paivamaara: Työmaapäiväkirjan päivämäärä
   luotu: Koska päiväkirja on lähetetty kyseiselle päivälle"
  [{:keys [luotu paivamaara] :as paivakirja}]
  (if (and luotu paivamaara)
    (let [paivamaara (pvm/palvelimen-aika->suomen-aikaan paivamaara)
          luotu (pvm/palvelimen-aika->suomen-aikaan luotu)
          seuraava-arkipaiva (pvm/seuraava-arkipaiva paivamaara)
          seur-akipaiva-klo-12 (pvm/dateksi (pvm/aikana seuraava-arkipaiva 12 0 0 0))
          aikaa-valissa (try
                          ;; Jäikö aikaa jäljelle lähettää päiväkirja
                          (pvm/aikavali-sekuntteina luotu seur-akipaiva-klo-12)
                          (catch Exception e
                            (if (= "The end instant must be greater than the start instant" (.getMessage e))
                              ;; Alkuaika isompi kuin seuraavan arkipäivän klo 12
                              ;; -> Päiväkirja lähetetty myöhässä -> Palautetaan vaan false
                              false
                              ;; Jos sattuu jokin muu virhe, heitetään se 
                              (throw (Exception. (.getMessage e))))))]
      (if (and aikaa-valissa (> aikaa-valissa 0))
        ;; Aikaa jäi jäljelle, eli päiväkirja lähetetty OK
        (assoc paivakirja :tila "ok")
        ;; Aikaa ei jäänyt jäljelle, päiväkirja on myöhässä
        (assoc paivakirja :tila "myohassa")))
    ;; Else -> lähetetty aikaa ei ole -> päiväkirja puuttuu 
    (assoc paivakirja :tila "puuttuu")))

(defn hae-tyomaapaivakirjat [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (let [_ (log/debug "hae-tyomaapaivakirjat :: tiedot" (pr-str tiedot))
        ;; Päiväkirjalistaukseen generoidaan rivit, vaikka päiväkirjoja ei olisi
        ;; Mutta tulevaisuuden päiville rivejä ei tarvitse generoida
        ;; Joten rajoitetaan loppupäivä tähän päivään
        loppuaika-sql (if (pvm/jalkeen? (:loppuaika tiedot) (pvm/nyt))
                        (konversio/sql-date (pvm/nyt))
                        (konversio/sql-date (:loppuaika tiedot)))
        paivakirjat (tyomaapaivakirja-kyselyt/hae-paivakirjalistaus db {:urakka-id (:urakka-id tiedot)
                                                                        :alkuaika (konversio/sql-date (:alkuaika tiedot))
                                                                        :loppuaika loppuaika-sql})]
    (map #(kasittele-tyomaapaivakirjan-tila %) paivakirjat)))

(defn- hae-kommentit [db tiedot]
  (tyomaapaivakirja-kyselyt/hae-paivakirjan-kommentit db {:urakka_id (:urakka-id tiedot)
                                                          :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)}))

(defn- hae-tyomaapaivakirjan-kommentit [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (hae-kommentit db tiedot))

(defn- tallenna-kommentti [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/raportit-kommentit user (:urakka-id tiedot))
  (tyomaapaivakirja-kyselyt/lisaa-kommentti<! db {:urakka_id (:urakka-id tiedot)
                                                  :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                  :versio (:versio tiedot)
                                                  :kommentti (:kommentti tiedot)
                                                  :luoja (:id user)})
  (hae-kommentit db tiedot))

(defn- poista-tyomaapaivakirjan-kommentti [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/raportit-kommentit user (:urakka-id tiedot))
  ;; Poistaa ainoastaan kommentin jos kommentti on poistajan itse tekemä
  (tyomaapaivakirja-kyselyt/poista-tyomaapaivakirjan-kommentti<! db {:id (:id tiedot)
                                                                     :kayttaja (:kayttaja tiedot)
                                                                     :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                                     :muokkaaja (:id user)})
  (hae-kommentit db tiedot))

;;FIXME: Tähän alkoi menemään niin paljon aikaa, että tehdessä päädyttiin tekemään yksinkertainen mäppäys
;; aikojen perusteella suorilla tietokantahauilla. Tämä on suorituskyvyn kannalta huono, koska sama pitäisi pystyä tekemään
;; postgressissä yhdellä haulla.
(defn hae-tehtavat-aikajaksolle [db urakka-id tid versio paivamaara]
  (let [tiedot {:urakka-id urakka-id
                :tyomaapaivakirja_id tid
                :versio versio}
        params1 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 0))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 2))})
        tehtavat1 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params1)

        params2 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 2))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))})
        tehtavat2 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params2)

        params3 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))})
        tehtavat3 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params3)

        params4 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))})
        tehtavat4 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params4)

        params5 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 24))})
        tehtavat5 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params5)]
    [tehtavat1 tehtavat2 tehtavat3 tehtavat4 tehtavat5]))

(defn koverttaa-paivakirjan-data [db tyomaapaivakirja versio]
  (let [;; Tehtävien tietokantamäppäys on liian monimutkainen, niin haetaan ne erikseen
        tehtavat (hae-tehtavat-aikajaksolle db (:urakka_id tyomaapaivakirja) (:tyomaapaivakirja_id tyomaapaivakirja)
                   versio (:paivamaara tyomaapaivakirja))
        tehtavat (map
                   (fn [rivi]
                     (map
                       (fn [r]
                         (assoc r :tehtavat (konversio/pgarray->vector (:tehtavat r))))
                       rivi))
                   tehtavat)]
    (-> tyomaapaivakirja
      (update
        :paivystajat
        (fn [paivystajat]
          (mapv
            #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
            (konversio/pgarray->vector paivystajat))))
      (update
        :tyonjohtajat
        (fn [tyonjohtajat]
          (mapv
            #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
            (konversio/pgarray->vector tyonjohtajat))))
      (update
        :saa-asemat
        (fn [saasemat]
          (group-by :aseman_tunniste (mapv
                                       #(konversio/pgobject->map %
                                          :havaintoaika :date :aseman_tunniste :string :aseman_tietojen_paivityshetki :date,
                                          :ilman_lampotila :double, :tien_lampotila :double, :keskituuli :long,
                                          :sateen_olomuoto :double, :sadesumma :long)
                                       (konversio/pgarray->vector saasemat)))))
      (update
        :poikkeussaat
        (fn [poikkeussaat]
          (mapv
            #(konversio/pgobject->map % :havaintoaika :date :paikka :string :kuvaus :string)
            (konversio/pgarray->vector poikkeussaat))))
      (update
        :kalustot
        (fn [kalustot]
          (mapv
            #(konversio/pgobject->map % :aloitus :date :lopetus :date :tyokoneiden_lkm :long :lisakaluston_lkm :long)
            (konversio/pgarray->vector kalustot))))
      ;; Päivitetään kalustolle vielä mahdolliset tehtävät
      (update
        :kalustot
        (fn [kalustot]
          (map-indexed
            (fn [indeksi kalusto]
              (let [;; Pieni indeksitarkistus, ettei käy käpy
                    kaluston-tehtavat (when (< indeksi (count tehtavat))
                                        (nth tehtavat indeksi))]
                (assoc kalusto :tehtavat (flatten (map :tehtavat kaluston-tehtavat)))))
            kalustot)))
      (update
        :tapahtumat
        (fn [tapahtumat]
          (mapv
            #(konversio/pgobject->map % :tyyppi :string :kuvaus :string)
            (konversio/pgarray->vector tapahtumat))))
      (update
        :toimeksiannot
        (fn [toimeksiannot]
          (mapv
            #(konversio/pgobject->map % :kuvaus :string :aika :double)
            (konversio/pgarray->vector toimeksiannot)))))))

(defn- suodata-versioiden-ryhmitys [data]
  (if (map? data)
    (let [suodata (into {} (remove (fn [[_ v]] (nil? v)) data))]
      (if (empty? suodata)
        nil
        suodata))
    (if (sequential? data)
      (let [suodata (filter (complement empty?) (map suodata-versioiden-ryhmitys data))]
        (if (empty? suodata)
          nil
          suodata))
      data)))

(defn- hae-tyomaapaivakirjan-muutoshistoria [db user {:keys [versio urakka-id tyomaapaivakirja_id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user urakka-id)
  (when tyomaapaivakirja_id
    (let [parametrit {:urakka_id urakka-id
                      :tyomaapaivakirja_id tyomaapaivakirja_id}
          fn-jsonb-konversio (fn [k]
                               (map #(apply hash-map %)
                                 (mapv (fn [rivi]
                                         (mapcat
                                           (fn [r]
                                             (if (= (type (get-in r [1])) org.postgresql.util.PGobject)
                                               (update r 1 #(konversio/jsonb->clojuremap %))
                                               r))
                                           rivi))
                                   k)))
          fn-generoi-idt-riveille (fn [data]
                                    (let [idt (map-indexed (fn [idx item] (assoc item :id (inc idx))) data)]
                                      idt))

          poikkeussaa (tyomaapaivakirja-kyselyt/hae-poikkeussaa-muutokset db parametrit)
          kalusto (tyomaapaivakirja-kyselyt/hae-kalusto-muutokset db parametrit)
          paivystajat (tyomaapaivakirja-kyselyt/hae-paivystaja-muutokset db parametrit)
          saaasemat (tyomaapaivakirja-kyselyt/hae-saaasema-muutokset db parametrit)
          tapahtumat (tyomaapaivakirja-kyselyt/hae-tapahtuma-muutokset db parametrit)
          tiesto (tyomaapaivakirja-kyselyt/hae-tieston-muutokset db parametrit)
          toimeksiannot (tyomaapaivakirja-kyselyt/hae-toimeksianto-muutokset db parametrit)
          tyonjohtajat (tyomaapaivakirja-kyselyt/hae-tyonjohtaja-muutokset db parametrit)

          ;; Lyödään tiedot yhteen jotta ovat jo jokseenkin nätisti mäpättynä 
          muutoshistoria (apply concat
                           (map #(fn-jsonb-konversio %)
                             [poikkeussaa kalusto paivystajat saaasemat
                              tapahtumat tiesto toimeksiannot tyonjohtajat]))
          ;; Ryhmitellään versiomuutokset sequensseihin (1 2) (2 3) (3 4) jotka näytetään gridissä
          ryhmitetyt-versiomuutokset (for [x (range 1 versio)]
                                       (map (fn [rivi]
                                              (let [nykyinen-vers (get-in rivi [:uudet :versio])
                                                    vanha-vers (get-in rivi [:vanhat :versio])
                                                    ret (when (or (= vanha-vers x) (= nykyinen-vers (inc x)))
                                                          rivi)]
                                                ret)) muutoshistoria))
          ;; Suodatetaan tyhjät sequenssit sun muut pois datasta 
          ryhmitetyt-versiomuutokset (suodata-versioiden-ryhmitys ryhmitetyt-versiomuutokset)
          ;; Generoidaan vielä uniikki idt gridin riveille
          ryhmitetyt-versiomuutokset (map fn-generoi-idt-riveille ryhmitetyt-versiomuutokset)]
      ryhmitetyt-versiomuutokset)))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]

    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae
      (fn [user tiedot]
        (hae-tyomaapaivakirjat db user tiedot)))

    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-tallenna-kommentti
      (fn [user tiedot]
        (tallenna-kommentti db user tiedot)))

    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae-kommentit
      (fn [user tiedot]
        (hae-tyomaapaivakirjan-kommentit db user tiedot)))

    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-poista-kommentti
      (fn [user tiedot]
        (poista-tyomaapaivakirjan-kommentti db user tiedot)))

    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae-muutoshistoria
      (fn [user tiedot]
        (hae-tyomaapaivakirjan-muutoshistoria db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :tyomaapaivakirja-hae
      :tyomaapaivakirja-tallenna-kommentti
      :tyomaapaivakirja-hae-kommentit
      :tyomaapaivakirja-poista-kommentti
      :tyomaapaivakirja-hae-muutoshistoria)
    this))
