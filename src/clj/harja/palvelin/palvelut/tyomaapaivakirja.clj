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
    (let [;; Korjaa palvelimen ja tietokannan aika-eron (3 tuntia, 10800s)
          ;; Tietokannan 24:00 == Palvelimen 21:00
          luotu (pvm/dateksi (pvm/ajan-muokkaus luotu true 10800))
          paivamaara (pvm/dateksi (pvm/ajan-muokkaus paivamaara true 10800))

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
      (if aikaa-valissa
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
        tehtavat1 (if versio
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params1)
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria-tehtavat db params1))

        params2 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 2))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))})
        tehtavat2 (if versio
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params2)
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria-tehtavat db params2))

        params3 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))})
        tehtavat3 (if versio
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params3)
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria-tehtavat db params3))

        params4 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))})
        tehtavat4 (if versio
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params4)
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria-tehtavat db params4))

        params5 (merge tiedot
                  {:alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))
                   :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 24))})
        tehtavat5 (if versio
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db params5)
                    (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria-tehtavat db params5))]
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

            (if versio
              #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
              #(konversio/pgobject->map % :versio :string :aloitus :date :lopetus :date :nimi :string :muokattu :date))
            (konversio/pgarray->vector paivystajat))))
      (update
        :tyonjohtajat
        (fn [tyonjohtajat]
          (mapv
            (if versio
              #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
              #(konversio/pgobject->map % :versio :string :aloitus :date :lopetus :date :nimi :string :muokattu :date))

            (konversio/pgarray->vector tyonjohtajat))))
      (update
        :saa-asemat
        (fn [saasemat]
          (group-by :aseman_tunniste (mapv
                                       (if versio
                                         #(konversio/pgobject->map %
                                            :havaintoaika :date :aseman_tunniste :string :aseman_tietojen_paivityshetki :date,
                                            :ilman_lampotila :double, :tien_lampotila :double, :keskituuli :long,
                                            :sateen_olomuoto :double, :sadesumma :long)
                                         #(konversio/pgobject->map %
                                            :versio :string :havaintoaika :date :aseman_tunniste :string :aseman_tietojen_paivityshetki :date,
                                            :ilman_lampotila :double, :tien_lampotila :double, :keskituuli :long,
                                            :sateen_olomuoto :double, :sadesumma :long :muokattu :date))
                                       (konversio/pgarray->vector saasemat)))))
      (update
        :poikkeussaat
        (fn [poikkeussaat]
          (mapv
            (if versio
              #(konversio/pgobject->map % :havaintoaika :date :paikka :string :kuvaus :string)
              #(konversio/pgobject->map % :versio :string :havaintoaika :date :paikka :string :kuvaus :string :muokattu :date))
            (konversio/pgarray->vector poikkeussaat))))
      (update
        :kalustot
        (fn [kalustot]
          (mapv
            (if versio
              #(konversio/pgobject->map % :aloitus :date :lopetus :date :tyokoneiden_lkm :long :lisakaluston_lkm :long)
              #(konversio/pgobject->map % :versio :string :aloitus :date :lopetus :date :tyokoneiden_lkm :long :lisakaluston_lkm :long :muokattu :date))
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
            (if versio
              #(konversio/pgobject->map % :tyyppi :string :kuvaus :string)
              #(konversio/pgobject->map % :versio :string :tyyppi :string :kuvaus :string :muokattu :date))
            (konversio/pgarray->vector tapahtumat))))
      (update
        :toimeksiannot
        (fn [toimeksiannot]
          (mapv
            (if versio
              #(konversio/pgobject->map % :kuvaus :string :aika :double)
              #(konversio/pgobject->map % :versio :string :kuvaus :string :aika :double :muokattu :date))
            (konversio/pgarray->vector toimeksiannot)))))))

(defn- siivoa-sulkeet [data]
  (if (and (seq? data) (= (count data) 1))
    (siivoa-sulkeet (first data))
    (if (map? data)
      (into {} (for [[k v] data]
                 [k (siivoa-sulkeet v)]))
      data)))

(defn- etsi-versiomuutokset-sequenssista
  "Etsii edellisen :versio avaimen vectorin mapeista ja vertaa passattuun versioon
   Niputtaa versiot ja palauttaa kaikki muuttuneet arvot muodossa {:avain arvo :vanha-arvo :uusi-arvo}"
  ([data versio & [avain]]
   (let [versio-str (str versio)
         aikaisempi-versio (str (dec versio))
         aikaisempi-data (filter #(= aikaisempi-versio (str (:versio %))) data)
         nykyinen-data (filter #(= versio-str (str (:versio %))) data)]
     (if (and (seq aikaisempi-data) (seq nykyinen-data))
       (let [aikaisemmat-data (group-by #(get % avain) aikaisempi-data)
             nykyiset-data (group-by #(get % avain) nykyinen-data)
             avaimet (set (keys (merge aikaisemmat-data nykyiset-data)))
             muutokset (for [x avaimet
                             :let [aikaisempi (aikaisemmat-data x)
                                   nykyinen (nykyiset-data x)]]
                         (let [aikaisemmat-muutokset (apply merge aikaisempi)
                               nykyiset-muutokset (apply merge nykyinen)
                               kaikki-avaimet (set (keys (merge aikaisemmat-muutokset nykyiset-muutokset)))
                               muuttuneet-avaimet (filter
                                                    (fn [k]
                                                      (and ;; Ignorataan nämä
                                                        (not= k :versio)
                                                        (not= k :muokattu)
                                                        (not= k :lopetus)
                                                        (not= k :aloitus)
                                                        (not= k :lopetus)
                                                        (not= k :havaintoaika)
                                                        (not= k :aseman_tietojen_paivityshetki)
                                                        (not= (get-in aikaisemmat-muutokset [k])
                                                          (get-in nykyiset-muutokset [k]))))
                                                    kaikki-avaimet)]
                           {:avain x
                            :muutokset (map (fn [k]
                                              {:avain (keyword k)
                                               :vanha-arvo (get-in aikaisemmat-muutokset [k])
                                               :uusi-arvo (get-in nykyiset-muutokset [k])})
                                         muuttuneet-avaimet)}))]
         (siivoa-sulkeet (remove empty? (map :muutokset muutokset))))
       ()))))


(defn etsi-saaasema-versiomuutokset
  [data versio]
  (let [edellinen-versio (-> versio dec str)
        filter-nykyinen (fn [maps] (filter (fn [m] (some #(= (str versio) (:versio %)) m)) maps))
        filter-edellinen (fn [maps] (filter (fn [m] (some #(= edellinen-versio (:versio %)) m)) maps))
        nykyinen (filter-nykyinen (vals data))
        edellinen (filter-edellinen (vals data))]
    (for [x nykyinen
          :let [tunniste (:aseman_tunniste x)
                tasmatty-arvo (first (filter #(= tunniste (:aseman_tunniste %)) edellinen))]]
      (when tasmatty-arvo
        ;; (println "saa tiedot: " (concat x tasmatty-arvo))
        (etsi-versiomuutokset-sequenssista (concat x tasmatty-arvo) versio)))))

(defn- kasittele-muutoshistoria [i data muutokset versio]
  (let [rivi (nth data i nil)
        fn-data? (fn [rivi data i]
                   (and
                     (some? rivi)
                     (> (count data) (dec i))))
        fn-kay-data (fn [data i muutos versio seq]
                      (if (vector? data)
                        (let [rivi (nth data i nil)
                              rivi-versio (when (:versio rivi) (Integer/parseInt (:versio rivi)))
                              ;_ (println "Types " (type rivi-versio ) (type versio ))
                              seq (if (or
                                        (= rivi-versio versio)
                                        (= rivi-versio (dec versio)))
                                    (do
                                      ;;(println "d: " rivi)
                                      (conj seq rivi))
                                    seq)]
                          (do
                            (if (> (count data) i)
                              (recur data (inc i) muutos versio seq)
                              seq)))))
        fn-rivin-versiohistoria (fn [rivi]
                                  (let [osio (nth rivi 0 nil)
                                        data (nth rivi 1 nil)
                                        sequenssi (fn-kay-data data 0 [] versio (concat ()))
                                        ; _ (println "\n total seq: " (etsi-versiomuutokset-sequenssista sequenssi versio))
                                        ]
                                    (when (and osio data)
                                      ;(println "\n Osio: " osio " \n data: " data " \n vers. " versio " type " (type data) " \n\n ")
                                      (if (seq? data)
                                        (println "\n\n muutokset: " (etsi-versiomuutokset-sequenssista data versio) " \n"))
                                      (if (map? data)
                                        (println "\n \n Sääasema muutokset " (etsi-saaasema-versiomuutokset data versio)))
                                      (if sequenssi
                                        (println "\n\n muutokset: " (etsi-versiomuutokset-sequenssista sequenssi versio :tyyppi))))))
        ;_ (println "\n Rivi: " rivi (fn-rivin-versiohistoria rivi))
        _ (fn-rivin-versiohistoria rivi)]

    (if (fn-data? rivi data i)
      (recur (inc i) data muutokset versio)
      muutokset)))

(defn- hae-tyomaapaivakirjan-muutoshistoria [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (let [muutoshistoria (tyomaapaivakirja-kyselyt/hae-paivakirjan-muutoshistoria db)
        ; muutoshistoria (koverttaa-paivakirjan-data db muutoshistoria nil)
        _ (println "-------------------------------------------------- \n ")
        _ (println "Muutoshistoria: " muutoshistoria)
        muutoshistoria (-> muutoshistoria first (into {}))

        test (-> muutoshistoria
               (update
                 :vanhat
                 (fn [j]
                   (konversio/jsonb->clojuremap j)))
               (update
                 :uudet
                 (fn [j]
                   (konversio/jsonb->clojuremap j))))
        _ (println "test: " (get-in test [:vanhat :urakka_id]))]
    0))

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
