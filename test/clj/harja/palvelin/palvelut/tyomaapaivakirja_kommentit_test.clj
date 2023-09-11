(ns harja.palvelin.palvelut.tyomaapaivakirja-kommentit-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
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

(def jarjestelma-fixture-i
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-jvh

    :api-tyomaapaivakirja (component/using
                            (api-tyomaapaivakirja/->Tyomaapaivakirja)
                            [:http-palvelin :db :integraatioloki])))

(defn jarjestelma-fixture-b [testit]
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

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture-i
                      jarjestelma-fixture-b))

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
        vastaus-body (cheshire/decode (:body vastaus) true)
        typa-data (cheshire/decode typa true)
        typa-db (integraatio-test/hae-typa (:tyomaapaivakirja-id vastaus-body) versio)

        _ (integraatio-test/varmista-perustiedot vastaus vastaus-body typa-db typa-data urakka-id ulkoinenid paivamaara)
        _ (integraatio-test/varmista-ensitallennus typa-db)


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

        fn-hae-typaivakirjat (fn [urakka-id alkuaika loppuaika]
                               (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tyomaapaivakirja-hae +kayttaja-jvh+
                                 {:urakka-id urakka-id
                                  :alkuaika alkuaika
                                  :loppuaika loppuaika}))

        kommentti "testi kommentti"
        typa-id (:tyomaapaivakirja_id typa-db)
        alkuaika (pvm/->pvm-aika "30.5.2023 00:00")
        loppuaika (pvm/->pvm-aika "30.5.2023 00:00")
        tulos-hae-kommentit (fn-hae-kommentit urakka-id typa-id)
        tulos-hae-typa (fn-hae-typaivakirjat urakka-id alkuaika loppuaika)

        ;; Kommentteja ei tallennettu
        _ (is (empty? tulos-hae-kommentit) "Kommentteja ei löytynyt")
        _ (is (nil? (-> tulos-hae-typa :kommenttien-maara)) "Päiväkirjan kommenttien määrä tyhjä")

        ;; Tallennetaan kommentti
        tulos-tallenna (fn-tallenna kommentti urakka-id typa-id)
        tulos-hae-typa (fn-hae-typaivakirjat urakka-id alkuaika loppuaika)

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
