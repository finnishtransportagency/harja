(ns harja.palvelin.palvelut.toteumat-maarat-listaus-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")
(def +oikea-testi-tierekisteri-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/tierekisteri")

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
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
                          :tierekisteri (component/using
                                          (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil)
                                          [:db :integraatioloki])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat :tierekisteri]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


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
                                                                   :hae-toimenpiteen-tehtava-yhteenveto +kayttaja-jvh+
                                                                   {:urakka-id urakka-id
                                                                    :hoitokauden-alkuvuosi 2019})
                                                   true)
        maarien-toteumat-2020 (vain-suunnitellut-maarat
                                (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-toimenpiteen-tehtava-yhteenveto +kayttaja-jvh+
                                                {:urakka-id urakka-id
                                                 :hoitokauden-alkuvuosi 2020})
                                true)
        maarien-toteumat-2021 (vain-suunnitellut-maarat
                                (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-toimenpiteen-tehtava-yhteenveto +kayttaja-jvh+
                                                {:urakka-id urakka-id
                                                 :hoitokauden-alkuvuosi 2021})
                                true)

        oulun-mhu-urakan-maarien-toteuma-2019 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, toimenpidekoodi tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.taso = 4
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2019'::INT
                                                              AND urakka = " urakka-id)))
        oulun-mhu-urakan-maarien-toteuma-2020 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, toimenpidekoodi tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.taso = 4
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2020'::INT
                                                              AND urakka = " urakka-id)))
        oulun-mhu-urakan-maarien-toteuma-2021 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara ut, toimenpidekoodi tk
                                                              WHERE ut.tehtava = tk.id
                                                              AND tk.taso = 4
                                                              AND tk.tehtavaryhma is not null
                                                              AND \"hoitokauden-alkuvuosi\" = '2021'::INT
                                                              AND urakka = " urakka-id)))]
    (is (= (count maarien-toteumat-ilman-suunnitelmaa-2019) oulun-mhu-urakan-maarien-toteuma-2019) "Määrien toteumien määrä hoitovuodelle 2019")
    (is (= (count maarien-toteumat-2020) oulun-mhu-urakan-maarien-toteuma-2020) "Määrien toteumien määrä hoitovuodelle 2020")
    (is (= (count maarien-toteumat-2021) oulun-mhu-urakan-maarien-toteuma-2021) "Määrien toteumien määrä hoitovuodelle 2021")))

;; Hae kaikki määrien toteumat tehtäväryhmän mukaan - tehtäväryhmä = ui:lla toimenpide
(deftest maarien-toteumat-listaus-tehtavaryhmalle
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        maarien-toteumat-21 (vain-suunnitellut-maarat
                              (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :hae-toimenpiteen-tehtava-yhteenveto +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :toimenpide "2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen"
                                               :hoitokauden-alkuvuosi 2020})
                              true)
        maarien-toteumat-muuta (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-toimenpiteen-tehtava-yhteenveto +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :toimenpide "6 MUUTA"
                                                :hoitokauden-alkuvuosi 2020})
        oulun-mhu-urakan-maarien-toteuma-21 (q (str "SELECT *
                                                       FROM
                                                            urakka_tehtavamaara ut,
                                                            toimenpidekoodi tk,
                                                            tehtavaryhma tr
                                                       WHERE tk.id = ut.tehtava
                                                         AND tr.id = tk.tehtavaryhma
                                                         AND ut.\"hoitokauden-alkuvuosi\" = 2020
                                                         AND tr.otsikko = '2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'
                                                         AND ut.urakka = " urakka-id))
        oulun-mhu-urakan-maarien-toteuma-muuta (q (str "SELECT *
                                                         FROM
                                                              urakka_tehtavamaara ut,
                                                              toimenpidekoodi tk,
                                                              tehtavaryhma tr
                                                                JOIN tehtavaryhma tr2 ON tr2.id = tr.emo
                                                                JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
                                                         WHERE tk.id = ut.tehtava
                                                           AND tr.id = tk.tehtavaryhma
                                                           AND tr.otsikko = '6 MUUTA'
                                                           AND ut.\"hoitokauden-alkuvuosi\" = 2020
                                                           AND ut.urakka = " urakka-id))]
    (is (= (count maarien-toteumat-21) (count oulun-mhu-urakan-maarien-toteuma-21)) "Määrien toteumien määrä")
    (is (= (count maarien-toteumat-muuta) (count oulun-mhu-urakan-maarien-toteuma-muuta)) "Määrien toteumien määrä")))
