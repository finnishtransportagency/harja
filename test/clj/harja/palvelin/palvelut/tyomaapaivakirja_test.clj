(ns harja.palvelin.palvelut.tyomaapaivakirja-test
  (:require [clojure.test :refer :all]
            [harja [testi :refer :all]]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.palvelut.tyomaapaivakirja :as tyomaapaivakirja]
            [harja.palvelin.integraatiot.api.tyomaapaivakirja :as api-tyomaapaivakirja]
            [harja.palvelin.integraatiot.api.tyomaapaivakirja-test :as integraatio-test]))

(def kayttaja-jvh "jvh")
(def kayttaja-yit "yit-rakennus")

(def fixture-api
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-jvh
    :api-tyomaapaivakirja (component/using
                            (api-tyomaapaivakirja/->Tyomaapaivakirja)
                            [:http-palvelin :db :integraatioloki])))

(defn fixture-typa [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tyomaapaivakirja (component/using
                              (tyomaapaivakirja/->Tyomaapaivakirja)
                              [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures fixture-api fixture-typa))

(defn- varmista-typa-tiedot [typa vastaus urakka-id paivamaara versio paivitys]
  (let [vastaus-body (cheshire/decode (:body vastaus) true)
        typa-data (cheshire/decode typa true)
        typa-db (integraatio-test/hae-typa (:tyomaapaivakirja-id vastaus-body) versio)
        _ (integraatio-test/varmista-perustiedot vastaus vastaus-body typa-db typa-data urakka-id "123456" paivamaara)
        _ (when-not paivitys (integraatio-test/varmista-ensitallennus typa-db))]
    (:tyomaapaivakirja_id typa-db)))

(defn- hae-paivakirjat [urakka-id alkuaika loppuaika]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :tyomaapaivakirja-hae +kayttaja-jvh+
    {:urakka-id urakka-id
     :alkuaika alkuaika
     :loppuaika loppuaika}))

(deftest typa-muutoshistoria-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        paivamaara "2023-05-30"
        aseman-tunniste (str (rand-int 9999999))
        urakoitsija (first (q-map (format "SELECT ytunnus, nimi FROM organisaatio WHERE nimi = '%s';" "YIT Rakennus Oy")))

        fn-hae-muutoshistoria (fn [urakka-id versio typa-id]
                                (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tyomaapaivakirja-hae-muutoshistoria +kayttaja-jvh+
                                  {:urakka-id urakka-id
                                   :versio versio
                                   :tyomaapaivakirja_id typa-id}))

        ;; Muokataan typa sopivaan muotoon
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" "123456")
               (.replace "__PAIVAMAARA__" paivamaara))

        ;; Generoidaan päiväkirja
        versio 1
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        typa-id (varmista-typa-tiedot typa vastaus urakka-id paivamaara versio false)

        ;; Generoidaan päiväkirjalle uusi versio 
        versio (inc versio)
        typa (-> "test/resurssit/api/tyomaapaivakirja-paivitys.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" "123456")
               (.replace "__PAIVAMAARA__" paivamaara)
               (.replace "__VERSIO__" (str versio))
               (.replace "__UUSI_SAA-ASEMA-TUNNISTE__" aseman-tunniste))

        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja/" typa-id] kayttaja-yit portti typa)
        typa-id (varmista-typa-tiedot typa vastaus urakka-id paivamaara versio true)
        alkuaika (pvm/->pvm-aika "30.5.2023 00:00")
        loppuaika (pvm/->pvm-aika "30.5.2023 00:00")
        tulos-hae-typa (hae-paivakirjat urakka-id alkuaika loppuaika)

        ;; Uuden version pitäisi näkyä
        _ (is (= (-> tulos-hae-typa first :versio) versio) (str "Päiväkirjan versio päivitetty: " versio))
        _ (is (some? tulos-hae-typa) "Päiväkirjan data löytyy")

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)

        ;; Sääaseman muutos
        ;; _________________
        v1-toiminto (-> muutoshistoria first first :toiminto)
        vanha-versio (-> muutoshistoria first first :vanhat :versio)

        ;; Vanhan version pitäisi olla 1 ja toiminto poistettu, sillä sääaseman tunniste vaihdettiin, eli tuli uusi tilalle
        _ (is (= v1-toiminto "poistettu") "Poistettiin sääasema")
        _ (is (= vanha-versio (dec versio)) (str "Vanha versio " (dec versio)))

        v2-toiminto (-> muutoshistoria first second :toiminto)
        nyk-versio (-> muutoshistoria first second :uudet :versio)
        nyk-aseman-tunniste (-> muutoshistoria first second :uudet :aseman_tunniste)
        _ (is (= v2-toiminto "lisatty") "Lisättiin uusi sääasema")
        _ (is (= nyk-versio versio) (str "Uusi versio " versio))
        _ (is (= nyk-aseman-tunniste aseman-tunniste) "Aseman tunniste täsmää")

        ;; Päivystäjän muutos
        ;; ___________________
        _ (u (str
               "UPDATE tyomaapaivakirja_paivystaja "
               "SET nimi = 'Valekoodari Mies' "
               "WHERE tyomaapaivakirja_id = " typa-id " AND urakka_id = " urakka-id " AND versio = " versio "; "))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        toiminto (-> muutoshistoria first first :toiminto)
        vanha-paivystaja (-> muutoshistoria first first :vanhat :nimi)
        ;; Vanha päivystäjä poistettiin
        _ (is (= toiminto "poistettu") "Poistettiin päivystäjä")
        _ (is (= vanha-paivystaja "Erkki Esimerkki") "Vanha päivystäjä täsmää")

        ;; Uusi päivystäjä lisättiin
        toiminto (-> muutoshistoria first second :toiminto)
        uusi-paivystaja (-> muutoshistoria first second :uudet :nimi)
        nyk-versio (-> muutoshistoria first second :uudet :versio)
        _ (is (= toiminto "lisatty") "Lisättiin päivystäjä")
        _ (is (= uusi-paivystaja "Valekoodari Mies") "Uusi päivystäjä täsmää")
        _ (is (= nyk-versio versio) "Uusi versio täsmää")

        ;; Lisätään toinen päivystäjä
        _ (i (format
               "INSERT INTO tyomaapaivakirja_paivystaja 
                (urakka_id, tyomaapaivakirja_id, versio, aloitus, lopetus, nimi, muokattu) 
                VALUES (%s, %s, %s, now(), now(), 'Jarno Konkari', now());"
               urakka-id typa-id versio))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        uusi-paivystaja (-> muutoshistoria first second :uudet :nimi)
        _ (is (= uusi-paivystaja "Jarno Konkari") "Lisättiin päivystäjä")

        ;; Kaluston muutos
        ;; ___________________
        _ (u (str
               "UPDATE tyomaapaivakirja_kalusto "
               "SET tyokoneiden_lkm = 20, lisakaluston_lkm = 20 "
               "WHERE tyomaapaivakirja_id = " typa-id " AND urakka_id = " urakka-id " AND versio = " versio "; "))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        toiminto (-> muutoshistoria first first :toiminto)
        vanha-t-lkm (-> muutoshistoria first first :vanhat :tyokoneiden_lkm)
        vanha-l-lkm (-> muutoshistoria first first :vanhat :lisakaluston_lkm)
        nyk-t-lkm (-> muutoshistoria first first :uudet :tyokoneiden_lkm)
        nyk-l-lkm (-> muutoshistoria first first :uudet :lisakaluston_lkm)

        ;; Muutettiin vanhat kalustojen lukumäärät -> 20 
        _ (is (= toiminto "muutettu") "Toiminto muokattu")
        _ (is (= vanha-t-lkm 2) "Vanha työkoneiden lukumäärä täsmää")
        _ (is (= vanha-l-lkm 4) "Vanha lisäkaluston lukumäärä täsmää")
        _ (is (= nyk-t-lkm 20) "Uusi työkoneiden lukumäärä täsmää")
        _ (is (= nyk-l-lkm 20) "Uusi lisäkaluston lukumäärä täsmää")

        ;; Poikkeussää
        ;; ___________________

        ;; Lisätään uusi havainto
        _ (i (format
               "INSERT INTO tyomaapaivakirja_poikkeussaa 
                (urakka_id, tyomaapaivakirja_id, versio, havaintoaika, paikka, kuvaus, muokattu) 
                VALUES (%s, %s, %s, now(), 'Lappi Lappalainen', '-40 kylmää', now());"
               urakka-id typa-id versio))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        toiminto (-> muutoshistoria first first :toiminto)
        kuvaus (-> muutoshistoria first first :uudet :kuvaus)
        paikka (-> muutoshistoria first first :uudet :paikka)
        _ (is (= toiminto "lisatty") "Lisätty säähavainto")
        _ (is (= kuvaus "-40 kylmää") "Lisätty havainnon kuvaus täsmää")
        _ (is (= paikka "Lappi Lappalainen") "Lisätty havainnon paikka täsmää")

        ;; Tapahtumat
        ;; ___________________

        ;; Lisätään uusi tapahtuma
        _ (i (format
               "INSERT INTO tyomaapaivakirja_tapahtuma
                (urakka_id, tyomaapaivakirja_id, versio, kuvaus, tyyppi, muokattu) 
                VALUES (%s, %s, %s, 'Paha kolari nelostiellä', 'onnettomuus', now());"
               urakka-id typa-id versio))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        toiminto (-> muutoshistoria first (nth 7 nil) :toiminto)
        kuvaus (-> muutoshistoria first (nth 7 nil) :uudet :kuvaus)
        tyyppi (-> muutoshistoria first (nth 7 nil) :uudet :tyyppi)
        _ (is (= toiminto "lisatty") "Lisätty tapahtuma")
        _ (is (= kuvaus "Paha kolari nelostiellä") "Lisätty kuvaus täsmää")
        _ (is (= tyyppi "onnettomuus") "Lisätty tapahtuman tyyppi täsmää")

        ;; Toimeksianto
        ;; ___________________

        ;; Lisätään uusi toimeksianto
        _ (i (format
               "INSERT INTO tyomaapaivakirja_toimeksianto
                (urakka_id, tyomaapaivakirja_id, versio, kuvaus, aika, muokattu) 
                VALUES (%s, %s, %s, 'Tehtiin jokin toimeksianto', 17, now());"
               urakka-id typa-id versio))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)
        toiminto (-> muutoshistoria first (nth 8 nil) :toiminto)
        kuvaus (-> muutoshistoria first (nth 8 nil) :uudet :kuvaus)
        aika (-> muutoshistoria first (nth 8 nil) :uudet :aika)
        _ (is (= toiminto "lisatty") "Lisätty toimeksianto")
        _ (is (= kuvaus "Tehtiin jokin toimeksianto") "Lisätty kuvaus täsmää")
        _ (is (= aika 17) "Lisätty aika täsmää")

        ;; Työnjohtaja
        ;; ___________________

        ;; Vaihdetaan johtaja
        _ (u (str
               "UPDATE tyomaapaivakirja_tyonjohtaja "
               "SET nimi = 'Huivi Huvilainen' "
               "WHERE tyomaapaivakirja_id = " typa-id " AND urakka_id = " urakka-id " AND versio = " versio "; "))

        muutoshistoria (fn-hae-muutoshistoria urakka-id versio typa-id)

        poistettu-tj (-> muutoshistoria first (nth 9 nil))
        _ (is (= (:toiminto poistettu-tj) "poistettu") "Poistettiin vanha työnjohtaja")
        _ (is (= (get-in poistettu-tj [:vanhat :nimi]) "Essi Esimerkki") "Vanha työnjohtaja")

        lisatty-tj (-> muutoshistoria first (nth 10 nil))
        _ (is (= (:toiminto lisatty-tj) "lisatty") "Lisättiin uusi työnjohtaja")
        _ (is (= (get-in lisatty-tj [:uudet :nimi]) "Huivi Huvilainen") "Uusi työnjohtaja")]))

(deftest typa-tilan-indikointi-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakoitsija (first (q-map (format "SELECT ytunnus, nimi FROM organisaatio WHERE nimi = '%s';" "YIT Rakennus Oy")))

        ;; 8. päivä syyskuuta == perjantai
        paivamaara "2023-09-08"
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" "123456")
               (.replace "__PAIVAMAARA__" paivamaara))

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        typa-id (varmista-typa-tiedot typa vastaus urakka-id paivamaara 1 false)

        alkuaika (pvm/->pvm-aika "08.9.2023 00:00")
        loppuaika (pvm/->pvm-aika "09.9.2023 00:00")

        ;; Asetetaan työmaapäiväkirjan luotu pvm seuraavan maanantain klo 12:01 (myöhässä)
        _ (u (str
               "UPDATE tyomaapaivakirja "
               "SET luotu = '2023-09-11T12:01:00+02:0' "
               "WHERE id = " typa-id " AND versio = 1; "))


        tila (-> (hae-paivakirjat urakka-id alkuaika loppuaika) first :tila)
        _ (is (= tila "myohassa") "Työmaapäiväkirjan tila on myöhässä (viikonloppu välissä)")

        ;; Asetetaan päivämääräksi 11:59 jolloin jäänyt minuutti aikaa, eli tilan pitäisi olla nyt OK.
        _ (u (str
               "UPDATE tyomaapaivakirja "
               "SET luotu = '2023-09-11T11:59:00+02:0' "
               "WHERE id = " typa-id " AND versio = 1; "))

        tila (-> (hae-paivakirjat urakka-id alkuaika loppuaika) first :tila)
        _ (is (= tila "ok") "Työmaapäiväkirjan tila on OK (viikonloppu välissä)")

        ;; Testataan vielä normaali arkipäivän tilan indikointi
        ;; 11. päivä syyskuuta == maanantai
        paivamaara "2023-09-11"
        typa (-> "test/resurssit/api/tyomaapaivakirja-kirjaus.json"
               slurp
               (.replace "__URAKOITSIJA__" (:nimi urakoitsija))
               (.replace "__YTUNNUS__" (:ytunnus urakoitsija))
               (.replace "__VIESTITUNNISTE__" (str (rand-int 9999999)))
               (.replace "__LÄHETYSAIKA__" "2016-01-30T12:00:00+02:00")
               (.replace "__ULKOINENID__" "123456")
               (.replace "__PAIVAMAARA__" paivamaara))

        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        typa-id (varmista-typa-tiedot typa vastaus urakka-id paivamaara 1 false)

        alkuaika (pvm/->pvm-aika "11.9.2023 00:00")
        loppuaika (pvm/->pvm-aika "12.9.2023 00:00")

        ;; Asetetaan luotu pvm seuraavan päivän klo 12:01 (myöhässä)
        _ (u (str
               "UPDATE tyomaapaivakirja "
               "SET luotu = '2023-09-12T12:01:00+02:0' "
               "WHERE id = " typa-id " AND versio = 1; "))

        tila (-> (hae-paivakirjat urakka-id alkuaika loppuaika) first :tila)
        _ (is (= tila "myohassa") "Työmaapäiväkirjan tila on myöhässä (arki)")

        ;; Jätetään 10 min aikaa, eli tilan pitäisi olla OK
        _ (u (str
               "UPDATE tyomaapaivakirja "
               "SET luotu = '2023-09-11T11:50:00+02:0' "
               "WHERE id = " typa-id " AND versio = 1; "))

        tila (-> (hae-paivakirjat urakka-id alkuaika loppuaika) first :tila)
        _ (is (= tila "ok") "Työmaapäiväkirjan tila on OK (arki)")]))

(deftest typa-kommentit-toimii
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

        versio 1
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/tyomaapaivakirja"] kayttaja-yit portti typa)
        typa-id (varmista-typa-tiedot typa vastaus urakka-id paivamaara versio false)

        fn-hae-kommentit (fn [urakka-id id]
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :tyomaapaivakirja-hae-kommentit +kayttaja-jvh+
                             {:urakka-id urakka-id :tyomaapaivakirja_id id}))

        fn-tallenna (fn [kommentti urakka-id id]
                      (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tyomaapaivakirja-tallenna-kommentti +kayttaja-jvh+
                        {:urakka-id urakka-id
                         :tyomaapaivakirja_id id
                         :versio 1
                         :kommentti kommentti
                         :luoja (:id +kayttaja-jvh+)}))

        kommentti "testi kommentti"
        alkuaika (pvm/->pvm-aika "30.5.2023 00:00")
        loppuaika (pvm/->pvm-aika "30.5.2023 00:00")
        tulos-hae-kommentit (fn-hae-kommentit urakka-id typa-id)
        tulos-hae-typa (hae-paivakirjat urakka-id alkuaika loppuaika)

        ;; Kommentteja ei tallennettu
        _ (is (empty? tulos-hae-kommentit) "Kommentteja ei löytynyt")
        _ (is (nil? (-> tulos-hae-typa :kommenttien-maara)) "Päiväkirjan kommenttien määrä tyhjä")

        ;; Tallennetaan kommentti
        tulos-tallenna (fn-tallenna kommentti urakka-id typa-id)
        tulos-hae-typa (hae-paivakirjat urakka-id alkuaika loppuaika)

        ;; Kommentti pitäisi olla olemassa
        _ (is (some? tulos-tallenna) "Kommentin tallennus onnistuu")
        _ (is (some? (-> tulos-tallenna first :kommentti)) "Kommentti olemassa")
        _ (is (> (-> tulos-hae-typa first :kommenttien-maara) 0) "Kommenttien määrä olemassa")
        _ (is (= (-> tulos-tallenna first :kommentti) kommentti) "Tallennettu kommentti löytyy")

        kommentti-id (-> tulos-tallenna first :id)
        fn-poista-kommentti (fn [id kayttaja typa-id kayttaja-id]
                              (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tyomaapaivakirja-poista-kommentti kayttaja
                                {:id id
                                 :kayttaja kayttaja-id
                                 :tyomaapaivakirja_id typa-id
                                 :muokkaaja kayttaja-id}))

        ;; Laitetaan muokkaajaksi eri kun luoja -> poistaminen ei pitäisi onnistua 
        poista-tulos (fn-poista-kommentti kommentti-id +kayttaja-jvh+ typa-id (-> +kayttaja-jvh+ :id inc))
        _ (is (some? poista-tulos) "Kommentti vielä olemassa")

        ;; Kommentin poisto pitäisi onnistua luojan id:llä
        poista-tulos (fn-poista-kommentti kommentti-id +kayttaja-jvh+ typa-id (:id +kayttaja-jvh+))
        _ (is (empty? poista-tulos) "Kommentin tekijä poistanut kommentin")]))
