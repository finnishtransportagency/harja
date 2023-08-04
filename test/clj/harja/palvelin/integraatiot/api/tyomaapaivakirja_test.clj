(ns harja.palvelin.integraatiot.api.tyomaapaivakirja-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date sql-timestamp-str->utc-timestr]]
            [harja.testi :refer :all]
            [clojure.test :refer :all]
            [slingshot.test :refer :all]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.api.tyomaapaivakirja :as api-tyomaapaivakirja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as tyokalut-virheet]
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

    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] 81.1) [])
          ["Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt 81.1."]))
    (is (thrown? Exception (api-tyomaapaivakirja/validoi-tyomaapaivakirja (:db jarjestelma) (assoc-in saatiedot [0 :saatieto :ilman-lampotila] 81.1)))
      "Poikkeus heitetään, kun ilman lämpötila on väärä tai uupuu")
    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] -81.1) [])
          ["Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt -81.1."]))
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :ilman-lampotila] 79.1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] 81.1) [])
          ["Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt 81.1."]))
    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] -81.1) [])
          ["Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt -81.1."]))
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :tien-lampotila] 79.1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] 151) [])
          ["Keskituuli täytyy olla väliltä 0 - 150. Oli nyt 151."]))
    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] -1) [])
          ["Keskituuli täytyy olla väliltä 0 - 150. Oli nyt -1."]))
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :keskituuli] 1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] -1) [])
          ["Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt -1."]))
    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] 151) [])
          ["Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt 151."]))
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sateen-olomuoto] 1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] -1) [])
          ["Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt -1."]))
    (is (= (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] 10001) [])
          ["Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt 10001."]))
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] 1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")
    (is (empty? (api-tyomaapaivakirja/validoi-saa (assoc-in saatiedot [0 :saatieto :sadesumma] nil) []))
      "Poikkeusta ei heitetä, koska arvo ei ole pakollinen")))

(deftest validoi-typa-arvot-kalusto
  (let [kalustotiedot [{:kalusto {:aloitus "2016-01-30T12:00:00+02:00",
                                  :lopetus "2016-01-30T14:00:00+02:00"
                                  :tyokoneiden-lkm 2
                                  :lisakaluston-lkm 4}}]]

    ;; Tarkista, että lopetus on aloituksen jälkeen
    ;; Tarkista kaluston määrät
    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lopetus] "2015-01-30T14:00:00+02:00") [])
          ["Kaluston lopetusaika täytyy olla aloitusajan jälkeen."]))
    (is (empty? (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lopetus] "2025-01-30T14:00:00+02:00") []))
      "Poikkeusta ei heitetä.")

    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :tyokoneiden-lkm] -1) [])
          ["Työkoneiden lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt -1."]))
    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :tyokoneiden-lkm] 10001) [])
          ["Työkoneiden lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt 10001."]))
    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :tyokoneiden-lkm] nil) [])
          ["Työkoneiden lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :tyokoneiden-lkm] 1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lisakaluston-lkm] -1) [])
          ["Lisäkaluston lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt -1."]))
    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lisakaluston-lkm] 10001) [])
          ["Lisäkaluston lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt 10001."]))
    (is (= (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lisakaluston-lkm] nil) [])
          ["Lisäkaluston lukumäärä täytyy olla väliltä 0 - 2000. Oli nyt null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-kalusto (assoc-in kalustotiedot [0 :kalusto :lisakaluston-lkm] 1) []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")))

(deftest validoi-typa-arvot-paivystajat-ja-tyonjohtajat
  (let [paivystaja [{:paivystaja {:aloitus "2016-01-30T12:00:00+02:00",
                                  :lopetus "2016-01-30T14:00:00+02:00"
                                  :nimi "Pekka Päivystäjä"}}]
        tyonjohtaja [{:tyonjohtaja {:aloitus "2016-01-30T12:00:00+02:00",
                                    :lopetus "2016-01-30T14:00:00+02:00"
                                    :nimi "Pekka Päivystäjä"}}]]

    ;; Tarkista, että lopetus on aloituksen jälkeen
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :lopetus] "2015-01-30T14:00:00+02:00") :paivystaja "Päivystäjän" [])
          ["Päivystäjän lopetusaika täytyy olla aloitusajan jälkeen."]))
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :lopetus] "2025-01-30T14:00:00+02:00") :paivystaja "Päivystäjän" []))
      "Poikkeusta ei heitetä.")
    ;; Lopetus ei ole pakollinen
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :lopetus] nil) :paivystaja "Päivystäjän" []))
      "Poikkeusta ei heitetä.")

    ;; Validoi nimen pituus
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :nimi] nil) :paivystaja "Päivystäjän" [])
          ["Päivystäjän nimi liian lyhyt. Oli nyt null."]))
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :nimi] "Pek") :paivystaja "Päivystäjän" [])
          ["Päivystäjän nimi liian lyhyt. Oli nyt Pek."]))
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in paivystaja [0 :paivystaja :nimi] "Päivi Päivystäjä") :paivystaja "Päivystäjän" []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")

    ;; Tarkista, että lopetus on aloituksen jälkeen
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :lopetus] "2015-01-30T14:00:00+02:00") :tyonjohtaja "Työnjohtajan" [])
          ["Työnjohtajan lopetusaika täytyy olla aloitusajan jälkeen."]))
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :lopetus] "2025-01-30T14:00:00+02:00") :tyonjohtaja "Työnjohtajan" []))
      "Poikkeusta ei heitetä.")
    ;; Lopetus ei ole pakollinen
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :lopetus] nil) :tyonjohtaja "Työnjohtajan" []))
      "Poikkeusta ei heitetä.")

    ;; Validoi nimen pituus
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :nimi] nil) :tyonjohtaja "Työnjohtajan" [])
          ["Työnjohtajan nimi liian lyhyt. Oli nyt null."]))
    (is (= (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :nimi] "Pek") :tyonjohtaja "Työnjohtajan" [])
          ["Työnjohtajan nimi liian lyhyt. Oli nyt Pek."]))
    (is (empty? (api-tyomaapaivakirja/validoi-paivystajat-ja-tyonjohtajat (assoc-in tyonjohtaja [0 :tyonjohtaja :nimi] "Tuula Työnjohtaja") :tyonjohtaja "Työnjohtajan" []))
      "Poikkeusta ei heitetä, koska arvo on oikean suuruinen.")))

(deftest validoi-typa-arvot-tieston-toimenpiteet
  (let [toimenpiteet [{:tieston-toimenpide {:aloitus "2016-01-30T12:00:00+02:00",
                                            :lopetus "2016-01-30T14:00:00+02:00"
                                            :tehtavat [{:tehtava {:id 1359}}]}}]]

    ;; Tarkista, että lopetus on aloituksen jälkeen
    (is (= (api-tyomaapaivakirja/validoi-tieston-toimenpiteet (:db jarjestelma) (assoc-in toimenpiteet [0 :tieston-toimenpide :lopetus] "2015-01-30T14:00:00+02:00") [])
          ["Toimenpiteen lopetusaika täytyy olla aloitusajan jälkeen."]))
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-toimenpiteet (:db jarjestelma) (assoc-in toimenpiteet [0 :tieston-toimenpide :lopetus] "2025-01-30T14:00:00+02:00") []))
      "Poikkeusta ei heitetä.")
    ;; Lopetus ei ole pakollinen
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-toimenpiteet (:db jarjestelma) (assoc-in toimenpiteet [0 :tieston-toimenpide :lopetus] nil) []))
      "Poikkeusta ei heitetä.")

    ;; Tehtävä täytyy löytyä tietokannasta
    (is (= (api-tyomaapaivakirja/validoi-tieston-toimenpiteet (:db jarjestelma) (assoc-in toimenpiteet [0 :tieston-toimenpide :tehtavat 0 :tehtava :id] 24342340234) [])
          ["Toimenpiteeseen liitettyä tehtävää ei löydy. Tarkista tehtävä id: 24342340234."]))
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-toimenpiteet (:db jarjestelma) (assoc-in toimenpiteet [0 :tieston-toimenpide :tehtavat 0 :tehtava :id] 1359) []))
      "Poikkeusta ei heitetä, koska tehtävä on validi.")))

(deftest validoi-typa-arvot-muut-tieston-toimenpiteet
  (let [muut-toimenpiteet [{:tieston-muu-toimenpide {:aloitus "2016-01-30T12:00:00+02:00",
                                                     :lopetus "2016-01-30T14:00:00+02:00"
                                                     :tehtavat [{:tehtava {:kuvaus "Esimerkki kuvaus"}}]}}]]

    ;; Tarkista, että lopetus on aloituksen jälkeen
    (is (= (api-tyomaapaivakirja/validoi-tieston-muut-toimenpiteet (assoc-in muut-toimenpiteet [0 :tieston-muu-toimenpide :lopetus] "2015-01-30T14:00:00+02:00") [])
          ["Tiestön muun toimenpiteen lopetusaika täytyy olla aloitusajan jälkeen."]))
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-muut-toimenpiteet (assoc-in muut-toimenpiteet [0 :tieston-muu-toimenpide :lopetus] "2025-01-30T14:00:00+02:00") []))
      "Poikkeusta ei heitetä.")
    ;; Lopetus ei ole pakollinen
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-muut-toimenpiteet (assoc-in muut-toimenpiteet [0 :tieston-muu-toimenpide :lopetus] nil) []))
      "Poikkeusta ei heitetä.")

    ;; Kuvaus
    (is (= (api-tyomaapaivakirja/validoi-tieston-muut-toimenpiteet (assoc-in muut-toimenpiteet [0 :tieston-muu-toimenpide :tehtavat 0 :tehtava :kuvaus] "s") [])
          ["Tiestön muun toimenpiteen kuvaus on liian lyhyt. Tarkenna kuvasta. Oli nyt: s."]))
    (is (empty? (api-tyomaapaivakirja/validoi-tieston-muut-toimenpiteet (assoc-in muut-toimenpiteet [0 :tieston-muu-toimenpide :tehtavat 0 :tehtava :id] "Tarkka kuvaus tehtävästä.") []))
      "Poikkeusta ei heitetä, koska tehtävä on validi.")))

(deftest validoi-typa-arvot-viranomaisen-avustaminen
  (let [avustukset [{:viranomaisen-avustus {:tunnit 4.54
                                            :kuvaus "Järkevä kuvaus"}}]]

    ;; Tarkista tunnit
    (is (= (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :tunnit] nil) [])
          []))
    (is (= (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :tunnit] -1) [])
          ["Viranomaisen avustamiseen käytetyt tunnit pitää olla väliltä 0 - 1000. Oli nyt: -1."]))
    (is (= (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :tunnit] 9999) [])
          ["Viranomaisen avustamiseen käytetyt tunnit pitää olla väliltä 0 - 1000. Oli nyt: 9999."]))
    (is (empty? (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :tunnit] 5.92) []))
      "Poikkeusta ei heitetä, koska tunnit on validit.")

    ;; Kuvaus
    (is (= (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :kuvaus] nil) [])
          ["Viranomaisen avustamisen kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (= (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :kuvaus] "nil") [])
          ["Viranomaisen avustamisen kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (empty? (api-tyomaapaivakirja/validoi-viranomaisen-avustamiset (assoc-in avustukset [0 :viranomaisen-avustus :kuvaus] "Avustettiin varovasti viranomaisia.") []))
      "Poikkeusta ei heitetä, koska kuvaus on olemassa.")))

(deftest validoi-typa-kuvaukset
  (let [data {:liikenteenohjaus-muutokset [{:liikenteenohjaus-muutos {:kuvaus "Kuvaus"}}]
              :onnettomuudet [{:onnettomuus {:kuvaus "Kuvaus"}}]
              :palautteet [{:palaute {:kuvaus "Kuvaus"}}]
              :tilaajan-yhteydenotot [{:tilaajan-yhteydenotto {:kuvaus "Kuvaus"}}]
              :muut-kirjaukset {:kuvaus "Kuvaus"}}]
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit data []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")

    ;; liikenteenohjaus-muutokset
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:liikenteenohjaus-muutokset 0 :liikenteenohjaus-muutos :kuvaus] "nil") [])
          ["Liikenteenohjausmuustosten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:liikenteenohjaus-muutokset 0 :liikenteenohjaus-muutos :kuvaus] nil) [])
          ["Liikenteenohjausmuustosten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:liikenteenohjaus-muutokset 0 :liikenteenohjaus-muutos :kuvaus] "Kuvaus on kunnossa.") []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")

    ;; onnettomuudet
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:onnettomuudet 0 :onnettomuus :kuvaus] "nil") [])
          ["Onnettomuuden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:onnettomuudet 0 :onnettomuus :kuvaus] nil) [])
          ["Onnettomuuden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:onnettomuudet 0 :onnettomuus :kuvaus] "Kuvaus on kunnossa.") []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")

    ;; tilaajan-yhteydenotot
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:tilaajan-yhteydenotot 0 :tilaajan-yhteydenotto :kuvaus] "nil") [])
          ["Yhteydenoton kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:tilaajan-yhteydenotot 0 :tilaajan-yhteydenotto :kuvaus] nil) [])
          ["Yhteydenoton kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:tilaajan-yhteydenotot 0 :tilaajan-yhteydenotto :kuvaus] "Kuvaus on kunnossa.") []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")

    ;; palautteet
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:palautteet 0 :palaute :kuvaus] "nil") [])
          ["Palautteiden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:palautteet 0 :palaute :kuvaus] nil) [])
          ["Palautteiden kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:palautteet 0 :palaute :kuvaus] "Kuvaus on kunnossa.") []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")

    ;; muut-kirjaukset
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:muut-kirjaukset :kuvaus] "nil") [])
          ["Muiden kirjausten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: nil."]))
    (is (= (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:muut-kirjaukset :kuvaus] nil) [])
          ["Muiden kirjausten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: null."]))
    (is (empty? (api-tyomaapaivakirja/validoi-muut-kuvaustekstit (assoc-in data [:muut-kirjaukset :kuvaus] "Kuvaus on kunnossa.") []))
      "Poikkeusta ei heitetä, koska kuvaukset on kunnossa.")))

(deftest validoi-typa-arvot-laajemmin
  (let [muut-toimenpiteet [{:tieston-muu-toimenpide {:aloitus "2016-01-30T12:00:00+02:00",
                                                     :lopetus "2016-01-30T14:00:00+02:00"
                                                     :tehtavat [{:tehtava {:kuvaus "Esimerkki kuvaus"}}]}}
                           ;; Jälkimmäisessä virheitä
                           {:tieston-muu-toimenpide {:aloitus "2016-01-30T12:00:00+02:00",
                                                     :lopetus "2015-01-30T14:00:00+02:00"
                                                     :tehtavat [{:tehtava {:kuvaus "e"}}]}}]
        kuvaukset {:liikenteenohjaus-muutokset [{:liikenteenohjaus-muutos {:kuvaus "Kuvaus"}}
                                           {:liikenteenohjaus-muutos {:kuvaus "k"}}]
              :onnettomuudet [{:onnettomuus {:kuvaus "Kuvaus"}}]
              :palautteet [{:palaute {:kuvaus "Kuvaus"}}]
              :tilaajan-yhteydenotot [{:tilaajan-yhteydenotto {:kuvaus "Kuvaus"}}]
              :muut-kirjaukset {:kuvaus "Kuvaus"}}
        saatiedot [{:saatieto {:havaintoaika "2016-01-30T12:00:00+02:00",
                               :aseman-tunniste "101500",
                               :aseman-tietojen-paivityshetki "2016-01-30T12:00:00+02:00",
                               :ilman-lampotila 10,
                               :tien-lampotila 10,
                               :keskituuli 16,
                               :sateen-olomuoto 23.0,
                               :sadesumma 5}}
                   ;; Jälkimmäisessä virheitä
                   {:saatieto {:havaintoaika "2016-01-30T12:00:00+02:00",
                               :aseman-tunniste "101500",
                               :aseman-tietojen-paivityshetki "2016-01-30T12:00:00+02:00",
                               :ilman-lampotila -100,
                               :tien-lampotila 100,
                               :keskituuli 160,
                               :sateen-olomuoto 231.0,
                               :sadesumma 599999}}]
        typa (merge
               {:saatiedot saatiedot
                :tieston-muut-toimenpiteet muut-toimenpiteet}
               kuvaukset)
        typa {:tyomaapaivakirja typa}
        _ (println "typa: " (pr-str typa))
        vastaus (try+
                  (api-tyomaapaivakirja/validoi-tyomaapaivakirja (:db jarjestelma) typa)
          (catch [:type tyokalut-virheet/+invalidi-json+] {:keys [virheet]}

            virheet))]

    (is (= (:viesti (first vastaus))
          "Ilman lämpötila täytyy olla väliltä -80 - 80. Oli nyt -100. Tien lämpötila täytyy olla väliltä -80 - 80. Oli nyt 100. Keskituuli täytyy olla väliltä 0 - 150. Oli nyt 160. Sateen olomuoto täytyy olla väliltä 0 - 150. Oli nyt 231.0. Sadesumma täytyy olla väliltä 0 - 10000. Oli nyt 599999. Tiestön muun toimenpiteen lopetusaika täytyy olla aloitusajan jälkeen. Tiestön muun toimenpiteen kuvaus on liian lyhyt. Tarkenna kuvasta. Oli nyt: e. Liikenteenohjausmuustosten kuvausteksti pitää olla asiallisen mittainen. Oli nyt: k."))))
