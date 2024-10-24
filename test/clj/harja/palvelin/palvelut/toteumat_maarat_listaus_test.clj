(ns harja.palvelin.palvelut.toteumat-maarat-listaus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                        (component/system-map
                          :db tietokanta
                          :db-replica tietokanta
                          :http-palvelin (testi-http-palvelin)
                          :karttakuvat (component/using
                                         (karttakuvat/luo-karttakuvat)
                                         [:http-palvelin :db])
                          :integraatioloki (component/using
                                             (integraatioloki/->Integraatioloki nil)
                                             [:db])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn vain-suunnitellut-maarat [maarien-toteumat ilman-suunnitelmaa?]
  (keep (fn [t]
          (if (and (:suunniteltu_maara t) (if ilman-suunnitelmaa?
                                            (> (:suunniteltu_maara t) 0)
                                            (> 0 (:suunniteltu_maara t))))
            t
            nil))
        maarien-toteumat))

;; Hae kaikki määrien toteumat ilman rajoituksia
;; Rajoituksettomuus on tässä kohdassa vähän ristiriitaista, koska suunniteltua määrää ei voi laittaa kuin joillekin
;; tietyille tehtäville. Niinpä teoriassa on mahdollista, että urakka_tehtavamaara taulussa on "suunniteltuja" tehtäviä, joita tämä haku
;; ei löydä. Kannattaa varmistaa todellinen suunniteltu tehtäväilanne sivulta Suunnittelu > Tehtävät ja määrät.
;; Suorituskyvyn takia on ollut myös pakko lisätä vähintään jonkinlainen aikarajaus, joka käytännössä meinaa, että hoitokauden-alkuvuosi on annettava.
;; Joten tällaista rajoittamatonta testiä ei voida enää tehdä
;(deftest maarien-toteumat-listaus-ilman-rajoituksia-test)

;; Hae kaikki määrien toteumat hoitovuoden mukaan
(deftest maarien-toteumat-hoittovuodelle-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        maarien-toteumat-ilman-suunnitelmaa-2019 (vain-suunnitellut-maarat
                                                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                   :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                                                   {:urakka-id urakka-id
                                                                    :tehtavaryhma 0
                                                                    :hoitokauden-alkuvuosi 2019})
                                                   true)
        maarien-toteumat-2020 (vain-suunnitellut-maarat
                                (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                                {:urakka-id urakka-id
                                                 :tehtavaryhma 0
                                                 :hoitokauden-alkuvuosi 2020})
                                true)
        maarien-toteumat-2021 (vain-suunnitellut-maarat
                                (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                                {:urakka-id urakka-id
                                                 :hoitokauden-alkuvuosi 2021})
                                true)

        oulun-mhu-urakan-maarien-toteuma-2019 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, tehtava tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.aluetieto = false
                                                              AND tk.\"mhu-tehtava?\" = true
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2019'::INT
                                                              AND urakka = " urakka-id)))
        oulun-mhu-urakan-maarien-toteuma-2020 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, tehtava tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.\"mhu-tehtava?\" = true
                                                              AND tk.aluetieto = false
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2020'::INT
                                                              AND urakka = " urakka-id)))
        oulun-mhu-urakan-maarien-toteuma-2021 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, tehtava tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.\"mhu-tehtava?\" = true
                                                              AND tk.aluetieto = false
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2021'::INT
                                                              AND urakka = " urakka-id)))]
    (is (= (count maarien-toteumat-ilman-suunnitelmaa-2019) oulun-mhu-urakan-maarien-toteuma-2019) "Määrien toteumien määrä hoitovuodelle 2019")
    (is (= (count maarien-toteumat-2020) oulun-mhu-urakan-maarien-toteuma-2020) "Määrien toteumien määrä hoitovuodelle 2020")
    (is (= (count maarien-toteumat-2021) oulun-mhu-urakan-maarien-toteuma-2021) "Määrien toteumien määrä hoitovuodelle 2021")))

;; Hae kaikki määrien toteumat tehtäväryhmän mukaan - tehtäväryhmä = ui:lla toimenpide
(deftest maarien-toteumat-listaus-tehtavaryhmalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        liikenneympariston-hoito-tr "2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen"
        maarien-toteumat-21 (vain-suunnitellut-maarat
                              (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :tehtavaryhma liikenneympariston-hoito-tr
                                               :hoitokauden-alkuvuosi 2020})
                              true)
        muut-tr "8 MUUTA"
        maarien-toteumat-muuta (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :tehtavaryhma muut-tr
                                                :hoitokauden-alkuvuosi 2020})
        oulun-mhu-urakan-maarien-toteuma-21 (q (str "SELECT *
                                                       FROM
                                                            urakka_tehtavamaara ut,
                                                            tehtava tk,
                                                            tehtavaryhma tr
                                                       LEFT JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id
                                                       WHERE tk.id = ut.tehtava
                                                         AND tr.id = tk.tehtavaryhma
                                                         AND ut.\"hoitokauden-alkuvuosi\" = 2020
                                                         AND o.otsikko = '2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'
                                                         AND ut.urakka = " urakka-id))
        oulun-mhu-urakan-maarien-toteuma-muuta (q (str "SELECT *
                                                         FROM tehtava tk,
                                                              tehtavaryhma tr
                                                              JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id,
                                                              urakka u
                                                         WHERE tk.tehtavaryhma = tr.id
                                                           AND tk.\"mhu-tehtava?\" = true
                                                           AND tk.kasin_lisattava_maara = true
                                                           AND (tk.aluetieto = false OR (tk.aluetieto = TRUE AND tk.kasin_lisattava_maara = TRUE))
                                                           AND o.otsikko = '8 MUUTA'
                                                           AND (tk.voimassaolo_alkuvuosi IS NULL OR tk.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
                                                           AND (tk.voimassaolo_loppuvuosi IS NULL OR tk.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
                                                           AND ((tk.suunnitteluyksikko IS not null AND tk.suunnitteluyksikko != 'euroa') OR
                                                                 tk.yksiloiva_tunniste IN ('1f12fe16-375e-49bf-9a95-4560326ce6cf',
                                                                                           '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974',
                                                                                           'd373c08b-32eb-4ac2-b817-04106b862fb1',
                                                                                           '49b7388b-419c-47fa-9b1b-3797f1fab21d',
                                                                                           '63a2585b-5597-43ea-945c-1b25b16a06e2',
                                                                                           'b3a7a210-4ba6-4555-905c-fef7308dc5ec',
                                                                                           'e32341fc-775a-490a-8eab-c98b8849f968',
                                                                                           '0c466f20-620d-407d-87b0-3cbb41e8342e',
                                                                                           'c058933e-58d3-414d-99d1-352929aa8cf9'))
                                                           AND u.id = " urakka-id))]
    ;; FIXME: edellisissä testeissä pitäisi tuo otsikko korvata jollain muulla hakutermillä, koska otsikot voi muuttua
    (is (= (count maarien-toteumat-21) (count oulun-mhu-urakan-maarien-toteuma-21)) "Määrien toteumien määrä")
    (is (= (count maarien-toteumat-muuta) (count oulun-mhu-urakan-maarien-toteuma-muuta)) "Määrien toteumien määrä")))
