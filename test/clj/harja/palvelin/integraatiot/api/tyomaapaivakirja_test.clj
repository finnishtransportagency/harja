(ns harja.palvelin.integraatiot.api.tyomaapaivakirja-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date sql-timestamp-str->utc-timestr]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.api.tyomaapaivakirja :as api-tyomaapaivakirja]
            [clojure.string :as str]))

(def kayttaja-yit "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit

    :api-tyomaapaivakirja (component/using
                            (api-tyomaapaivakirja/->Tyomaapaivakirja)
                            [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

;; Helpperit
(defn poista-viimeisin-typa []
  (let [;; Viimeisin typa id
        id (:id (first (q-map (str "select id FROM tyomaapaivakirja order by id desc limit 1"))))
        ;; Poista viimeisin tyomaappaivakirja
        _ (u (format "DELETE FROM tyomaapaivakirja WHERE id = %s", id))]))

(defn hae-typa [tid versio]
  (let [typa-db
        (first
          (q-map
            (format
              "SELECT t.id as tyomaapaivakirja_id, t.urakka_id, t.paivamaara::DATE, t.luotu, t.luoja, t.muokattu, t.muokkaaja, t.ulkoinen_id,
                      (SELECT array_agg(row(k.aloitus, k.lopetus, k.tyokoneiden_lkm, k.lisakaluston_lkm)) FROM tyomaapaivakirja_kalusto k WHERE k.tyomaapaivakirja_id = %s AND k.versio = %s) as kalustot,
                      (SELECT array_agg(row(p.aloitus, p.lopetus, p.nimi)) FROM tyomaapaivakirja_paivystaja p WHERE p.tyomaapaivakirja_id = %s AND p.versio = %s) as paivystajat,
                      (SELECT array_agg(row(p.havaintoaika, p.paikka, p.kuvaus)) FROM tyomaapaivakirja_poikkeussaa p WHERE p.tyomaapaivakirja_id = %s AND p.versio = %s) as poikkeussaat,
                      (SELECT array_agg(row(s.havaintoaika, s.aseman_tunniste, s.aseman_tietojen_paivityshetki, s.ilman_lampotila, s.tien_lampotila, s.keskituuli, s.sateen_olomuoto, s.sadesumma)) FROM tyomaapaivakirja_saaasema s WHERE s.tyomaapaivakirja_id = %s AND s.versio = %s) as saat,
                      (SELECT array_agg(row(t.tyyppi, t.kuvaus)) FROM tyomaapaivakirja_tapahtuma t WHERE t.tyomaapaivakirja_id = %s AND t.versio = %s) as tapahtumat,
                      (SELECT array_agg(row(tt.tyyppi, tt.aloitus, tt.lopetus)) FROM tyomaapaivakirja_tieston_toimenpide tt WHERE tt.tyomaapaivakirja_id = %s AND tt.versio = %s) as tieston_toimenpiteet,
                      (SELECT array_agg(row(t.aloitus, t.lopetus, t.nimi)) FROM tyomaapaivakirja_tyonjohtaja t WHERE t.tyomaapaivakirja_id = %s AND t.versio = %s) as tyonjohtajat,
                      (SELECT array_agg(row(t.kuvaus, t.aika)) FROM tyomaapaivakirja_toimeksianto t WHERE t.tyomaapaivakirja_id = %s AND t.versio = %s) as toimeksiannot
                 FROM tyomaapaivakirja t WHERE t.id = %s" tid versio tid versio tid versio tid versio tid versio tid versio tid versio tid versio tid)))
        typa-db (-> typa-db
                  (update :kalustot
                    (fn [kalusto]
                      (mapv
                        #(konversio/pgobject->map % :aloitus :string :lopetus :string :tyokoneiden_lkm :long :lisakaluston_lkm :long)
                        (konversio/pgarray->vector kalusto))))
                  (update :paivystajat
                    (fn [paivystaja]
                      (mapv
                        #(konversio/pgobject->map % :aloitus :string :lopetus :string :nimi :string)
                        (konversio/pgarray->vector paivystaja))))
                  (update :poikkeussaat
                    (fn [saa]
                      (mapv
                        #(konversio/pgobject->map % :havaintoaika :string :paikka :string :kuvaus :string)
                        (konversio/pgarray->vector saa))))
                  (update :saat
                    (fn [saa]
                      (mapv
                        #(konversio/pgobject->map % :havaintoaika :string :aseman_tunniste :string :aseman_tietojen_paivityshetki :string :ilman_lampotila :double :tien_lampotila :double :keskituuli :long :sateen_olomuoto :double :sadesumma :long)
                        (konversio/pgarray->vector saa))))
                  (update :tapahtumat
                    (fn [tapahtumat]
                      (mapv
                        #(konversio/pgobject->map % :tyyppi :string :kuvaus :string)
                        (konversio/pgarray->vector tapahtumat))))
                  (update :tieston_toimenpiteet
                    (fn [toimenpiteet]
                      (mapv
                        #(konversio/pgobject->map % :tyyppi :string :aloitus :string :lopetus :string)
                        (konversio/pgarray->vector toimenpiteet))))
                  (update :tyonjohtajat
                    (fn [tyonjohtajat]
                      (mapv
                        #(konversio/pgobject->map % :aloitus :string :lopetus :string :nimi :string)
                        (konversio/pgarray->vector tyonjohtajat))))
                  (update :toimeksiannot
                    (fn [toimeksiannot]
                      (mapv
                        #(konversio/pgobject->map % :kuvaus :string :aika :double)
                        (konversio/pgarray->vector toimeksiannot)))))

        ;; Tehtävien ja toimenpiteiden mäppääminen on tehty todella vaikeaksi yllä olevan tietokantahaun kautta, joten tehdään niille erillinen haku
        sql-tehtavat (q-map (format "SELECT (SELECT string_agg(t.tehtavat::TEXT, ', ')) as tehtavat FROM tyomaapaivakirja_tieston_toimenpide t
                                      WHERE t.tyomaapaivakirja_id = %s AND t.versio = %s" tid versio))
        tehtavat (mapv
                   (fn [tehtava]
                     (let [tehtava (-> tehtava
                                     (update :tehtavat (fn [t]
                                                         (-> t
                                                           (str/replace "{" "")
                                                           (str/replace "}" "")
                                                           (str/split #",")))))
                           tehtava (update tehtava :tehtavat (fn [t]
                                                               (map #(konversio/konvertoi->int %) t)))]
                       tehtava))
                   sql-tehtavat)
        sql-toimenpiteet (q-map (format "SELECT (SELECT string_agg(t.toimenpiteet::TEXT, ', ')) toimenpiteet FROM tyomaapaivakirja_tieston_toimenpide t
                                         WHERE t.tyomaapaivakirja_id = %s AND t.versio = %s" tid versio))
        toimenpiteet (mapv
                       (fn [toimenpide]
                         (let [toimenpide (-> toimenpide
                                            (update :toimenpiteet (fn [t]
                                                                    (-> t
                                                                      (str/replace "{" "")
                                                                      (str/replace "}" "")
                                                                      (str/split #",")))))
                               toimenpide (update toimenpide :toimenpiteet (fn [t]
                                                                             (mapv (fn [siivottava]
                                                                                     (let [siivottava (clojure.string/replace siivottava #"\\\"" "\"")
                                                                                           siivottava (clojure.string/replace siivottava #"\"" "")]
                                                                                       siivottava))
                                                                               t)))]
                           toimenpide))
                       sql-toimenpiteet)
        typa-db (-> typa-db
                  (assoc :tehtavat tehtavat)
                  (assoc :toimenpiteet toimenpiteet))]
    typa-db))

(defn varmista-perustiedot [vastaus vastaus-body typa-db typa-data urakka-id ulkoinenid paivamaara]
  ;; Varmistetaan, että vastaus on ok
  (is (= 200 (:status vastaus)))
  (is (= "OK" (:status vastaus-body)))

  ;; Varmistetaan työmaapäiväkirjan tiedot
  (is (= (:urakka_id typa-db) urakka-id))
  (is (= (:ulkoinen_id typa-db) ulkoinenid))
  (is (= (:paivamaara typa-db) (pvm-string->java-sql-date paivamaara)))

  ;; Varmistetaan kaluston tiedot
  (is (= (get-in typa-db [:kalustot 0 :tyokoneiden_lkm]) (get-in typa-data [:tyomaapaivakirja :kaluston-kaytto 0 :kalusto :tyokoneiden-lkm])))
  (is (= (get-in typa-db [:kalustot 0 :lisakaluston_lkm]) (get-in typa-data [:tyomaapaivakirja :kaluston-kaytto 0 :kalusto :lisakaluston-lkm])))

  ;; Varmistetaan päivystäjän tiedot
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:paivystajat 0 :aloitus]))
        (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :paivystajan-tiedot 0 :paivystaja :aloitus]))))
  (is (= (get-in typa-db [:paivystajat 0 :nimi]) (get-in typa-data [:tyomaapaivakirja :paivystajan-tiedot 0 :paivystaja :nimi])))

  ;; Varmistetaan poikkeussää
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:poikkeussaat 0 :havaintoaika]))
        (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :poikkeukselliset-saahavainnot 0 :poikkeuksellinen-saahavainto :havaintoaika]))))
  (is (= (get-in typa-db [:poikkeussaat 0 :paikka]) (get-in typa-data [:tyomaapaivakirja :poikkeukselliset-saahavainnot 0 :poikkeuksellinen-saahavainto :paikka])))
  (is (= (get-in typa-db [:poikkeussaat 0 :kuvaus]) (get-in typa-data [:tyomaapaivakirja :poikkeukselliset-saahavainnot 0 :poikkeuksellinen-saahavainto :kuvaus])))

  ;; Varmistetaan sää
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:saat 0 :havaintoaika]))
        (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :havaintoaika]))))
  (is (= (get-in typa-db [:saat 0 :aseman_tunniste]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :aseman-tunniste])))
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:saat 0 :aseman_tietojen_paivityshetki])) (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :aseman-tietojen-paivityshetki]))))
  (is (= (get-in typa-db [:saat 0 :ilman_lampotila]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :ilman-lampotila])))
  (is (= (get-in typa-db [:saat 0 :tien_lampotila]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :tien-lampotila])))
  (is (= (get-in typa-db [:saat 0 :keskituuli]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :keskituuli])))
  (is (= (get-in typa-db [:saat 0 :sateen_olomuoto]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :sateen-olomuoto])))
  (is (= (get-in typa-db [:saat 0 :sadesumma]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :sadesumma])))

  ;; Varmistetaan tapahtumat
  ;'onnettomuus', 'liikenteenohjausmuutos', 'palaute', 'tilaajan-yhteydenotto', 'muut_kirjaukset'
  (is (= 5 (count (:tapahtumat typa-db))))

  ;; Varmistetaan viranomaisen avustaminen
  (is (= (get-in typa-db [:toimeksiannot 0 :kuvaus]) (get-in typa-data [:tyomaapaivakirja :viranomaisen-avustaminen 0 :viranomaisen-avustus :kuvaus])))
  (is (= (get-in typa-db [:toimeksiannot 0 :aika]) (get-in typa-data [:tyomaapaivakirja :viranomaisen-avustaminen 0 :viranomaisen-avustus :tunnit])))

  ;; Varmistetaan tiestön toimenpiteet
  (is (= (get-in typa-db [:tieston_toimenpiteet 0 :tyyppi]) "yleinen"))
  (is (= (get-in typa-db [:tieston_toimenpiteet 1 :tyyppi]) "muu"))
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:tieston_toimenpiteet 0 :aloitus])) (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :tieston-toimenpiteet 0 :tieston-toimenpide :aloitus]))))
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:tieston_toimenpiteet 1 :aloitus])) (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :tieston-muut-toimenpiteet 0 :tieston-muu-toimenpide :aloitus]))))
  (is (= (first (:tehtavat (first (:tehtavat typa-db)))) (get-in typa-data [:tyomaapaivakirja :tieston-toimenpiteet 0 :tieston-toimenpide :tehtavat 0 :tehtava :id])))
  (is (= (first (:toimenpiteet (first (:toimenpiteet typa-db)))) (get-in typa-data [:tyomaapaivakirja :tieston-muut-toimenpiteet 0 :tieston-muu-toimenpide :tehtavat 0 :tehtava :kuvaus])))


  ;; Varmistetaan työnjohtaja
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:tyonjohtajat 0 :aloitus])) (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :tyonjohtajan-tiedot 0 :tyonjohtaja :aloitus]))))
  (is (= (sql-timestamp-str->utc-timestr (get-in typa-db [:tyonjohtajat 0 :lopetus])) (sql-timestamp-str->utc-timestr (get-in typa-data [:tyomaapaivakirja :tyonjohtajan-tiedot 0 :tyonjohtaja :lopetus]))))
  (is (= (get-in typa-db [:tyonjohtajat 0 :nimi]) (get-in typa-data [:tyomaapaivakirja :tyonjohtajan-tiedot 0 :tyonjohtaja :nimi]))))

(defn varmista-ensitallennus [typa-db]
  ;; Varmistetaan ensitallennus, eli muokatut tiedot on nill
  (is (nil? (:muokattu typa-db)))
  (is (nil? (:muokkaaja typa-db))))

(defn varmista-muokatut-tiedot [typa-db typa-data saa-asema]

  ;; Työmaapäiväkirjaa on muokattu
  (is (not (nil? (:muokattu typa-db))))
  (is (not (nil? (:muokkaaja typa-db))))
  ;; Työmaapäiväkirjan muokattuaika ei ole sama kuin luontiaika
  (is (not= (:muokattu typa-db) (:luotu typa-db)))

  ;; Sääasemaa muokattiin
  (is (= (get-in typa-db [:saat 0 :aseman_tunniste]) (get-in typa-data [:tyomaapaivakirja :saatiedot 0 :saatieto :aseman-tunniste])))
  (is (= (get-in typa-db [:saat 0 :aseman_tunniste]) saa-asema)))

;; Testit
(deftest kirjaa-typa-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        paivamaara "2023-05-30"
        ulkoinenid "123456"
        urakoitsija (first (q-map (format "SELECT ytunnus, nimi FROM organisaatio WHERE nimi = '%s';" "YIT Rakennus Oy")))
        ;; Muokataan typa sopivaan muotoon
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" ulkoinenid)
               (.replace "__PAIVAMAARA__" paivamaara))

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        vastaus-body (cheshire/decode (:body vastaus) true)

        ;; Hae typa tietokannasta
        versio 1
        typa-data (cheshire/decode typa true)
        typa-db (hae-typa (:tyomaapaivakirja-id vastaus-body) versio)
        ;; Poista typa
        _ (poista-viimeisin-typa)]
    (varmista-perustiedot vastaus vastaus-body typa-db typa-data urakka-id ulkoinenid paivamaara)
    (varmista-ensitallennus typa-db)))

(deftest paivita-typa-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        paivamaara "2023-05-30"
        ulkoinenid "12345"
        urakoitsija (first (q-map (format "SELECT ytunnus, nimi FROM organisaatio WHERE nimi = '%s';" "YIT Rakennus Oy")))
        ;; 1. Tallenna ensin typa
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" ulkoinenid)
               (.replace "__PAIVAMAARA__" paivamaara))

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        vastaus-body (cheshire/decode (:body vastaus) true)
        ;; Tyomaapaivakirjaid talteen
        tid (:tyomaapaivakirja-id vastaus-body)

        ;; 2. Päivitä typa uudella json viestillä
        uusi-saa-asema "123456"
        uusi-versio 2
        paivitys-typa (-> "test/resurssit/api/tyomaapaivakirja-paivitys.json"
                        slurp
                        (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
                        (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
                        (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
                        (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
                        (.replace "__PAIVAMAARA__" paivamaara)
                        (.replace "__VERSIO__" (str uusi-versio))
                        (.replace "__ULKOINENID__" ulkoinenid)
                        ;; Muokatut tiedot
                        (.replace "__UUSI_SAA-ASEMA-TUNNISTE__" uusi-saa-asema))

        paivitys-typa-data (cheshire/decode paivitys-typa true)
        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja/" tid] kayttaja-yit portti paivitys-typa)
        vastaus-body (cheshire/decode (:body vastaus) true)

        ;; Hae typa tietokannasta
        typa-db (hae-typa (:tyomaapaivakirja-id vastaus-body) uusi-versio)

        ;; Poista typa
        _ (poista-viimeisin-typa)]

    (varmista-perustiedot vastaus vastaus-body typa-db paivitys-typa-data urakka-id ulkoinenid paivamaara)
    (varmista-muokatut-tiedot typa-db paivitys-typa-data uusi-saa-asema)))

(deftest virheellinen-post
  (let [urakka-id "jaska"
        paivamaara "2023-05-30"
        ulkoinenid "12345"
        urakoitsija (first (q-map (format "SELECT ytunnus, nimi FROM organisaatio WHERE nimi = '%s';" "YIT Rakennus Oy")))
        ;; 1. Tallenna ensin typa
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" ulkoinenid)
               (.replace "__PAIVAMAARA__" paivamaara))

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        vastaus-body (cheshire/decode (:body vastaus) true)]
    (is (= "puutteelliset-parametrit") (get-in vastaus-body [:virheet 0 :virhe :koodi]))))

(deftest validoi-typa-arvot-saa
  (let [saatiedot [{:saatieto {:havaintoaika "2016-01-30T12:00:00+02:00",
                               :aseman-tunniste "101500",
                               :aseman-tietojen-paivityshetki "2016-01-30T12:00:00+02:00",
                               :ilman-lampotila 10,
                               :tien-lampotila 10,
                               :keskituuli 16,
                               :sateen-olomuoto 23.0,
                               :sadesumma 5}}]]
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] 81.1)))
      "Poikkeus heitetään, kun ilman lämpötila on väärä tai uupuu")
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] -81.1)))
      "Poikkeus heitetään, kun ilman lämpötila on väärä tai uupuu")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] 79.1)))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] 81.1)))
      "Poikkeus heitetään, kun tien lämpötila on väärä.")
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] -81.1)))
      "Poikkeus heitetään, kun tien lämpötila on väärä.")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] 79.1)))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] 151)))
      "Poikkeus heitetään, kun keskituuli on väärä.")
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] -1)))
      "Poikkeus heitetään, kun keskituuli on väärä.")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] 1)))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] -1)))
      "Poikkeus heitetään, kun sateen olomuoto on väärä.")
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] 151)))
      "Poikkeus heitetään, kun sateen olomuoto on väärä.")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] 1)))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] -1)))
      "Poikkeus heitetään, kun sadesumma on väärä.")
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] 10001)))
      "Poikkeus heitetään, kun sadesumma on väärä.")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] 1)))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")
    (is (nil? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] nil)))
      "Poikkeusta ei heitetä, koska arvo ei ole pakollinen")))

