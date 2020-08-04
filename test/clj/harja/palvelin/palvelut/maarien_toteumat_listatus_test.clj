(ns harja.palvelin.palvelut.maarien_toteumat_listatus_test
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
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.domain.tierekisteri.varusteet :as varusteet-domain]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")
(def +oikea-testi-tierekisteri-url+ "https://harja-test.solitaservices.fi/harja/integraatiotesti/tierekisteri")

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
                          :tierekisteri (component/using
                                          (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil)
                                          [:db :integraatioloki])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat :tierekisteri]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Hae kaikki määrien toteumat ilman rajoituksia
(deftest maarien-toteumat-listaus-ilman-rajoituksia-test
  (let [maarien-toteumat (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-maarien-toteumat +kayttaja-jvh+
                                         {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})
        _ (log/debug "maarien-toteumat-listaus-ilman-rajoituksia :: maarien-toteumat" (pr-str maarien-toteumat))
        oulun-mhu-urakan-maarien-toteuma-lkm (ffirst (q
                                                       (str "SELECT count(*)
                                                             FROM urakka_tehtavamaara
                                                             WHERE urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))]
    (is (= (count maarien-toteumat) oulun-mhu-urakan-maarien-toteuma-lkm) "Määrien toteumien määrä")))

;; Hae kaikki määrien toteumat hoitovuoden mukaan
(deftest maarien-toteumat-hoittovuodelle-test
  (let [maarien-toteumat-2019 (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-maarien-toteumat +kayttaja-jvh+
                                              {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                               :alkupvm "2019-10-01"
                                               :loppupvm "2020-09-30"})
        maarien-toteumat-2020 (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-maarien-toteumat +kayttaja-jvh+
                                              {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                               :alkupvm "2020-10-01"
                                               :loppupvm "2021-09-30"})
        maarien-toteumat-2021 (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-maarien-toteumat +kayttaja-jvh+
                                              {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                               :alkupvm "2021-10-01"
                                               :loppupvm "2022-09-30"})

        oulun-mhu-urakan-maarien-toteuma-2019 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara
                                                              WHERE \"hoitokauden-alkuvuosi\" = '2019'::INT AND urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))
        oulun-mhu-urakan-maarien-toteuma-2020 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara
                                                              WHERE \"hoitokauden-alkuvuosi\" = '2020'::INT AND urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))
        oulun-mhu-urakan-maarien-toteuma-2021 (ffirst (q
                                                        (str "SELECT count(*)
                                                              FROM urakka_tehtavamaara
                                                              WHERE \"hoitokauden-alkuvuosi\" = '2021'::INT AND urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))]
    (is (= (count maarien-toteumat-2019) oulun-mhu-urakan-maarien-toteuma-2019) "Määrien toteumien määrä hoitovuodelle 2019")
    (is (= (count maarien-toteumat-2020) oulun-mhu-urakan-maarien-toteuma-2020) "Määrien toteumien määrä hoitovuodelle 2020")
    (is (= (count maarien-toteumat-2021) oulun-mhu-urakan-maarien-toteuma-2021) "Määrien toteumien määrä hoitovuodelle 2021")))

;; Hae kaikki määrien toteumat tehtäväryhmän mukaan - tehtäväryhmä = ui:lla toimenpide
(deftest maarien-toteumat-listaus-tehtavaryhmalle
  (let [maarien-toteumat-talvihoito (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-maarien-toteumat +kayttaja-jvh+
                                         {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                          :tehtavaryhma "1.0 TALVIHOITO"})
        maarien-toteumat-muuta (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-maarien-toteumat +kayttaja-jvh+
                                         {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                          :tehtavaryhma "6 MUUTA"})
        oulun-mhu-urakan-maarien-toteuma-talvihoito (q
                                                  (str "SELECT *
                                                             FROM
                                                                  urakka_tehtavamaara ut,
                                                                  toimenpidekoodi tk,
                                                                  tehtavaryhma tr
                                                                    JOIN tehtavaryhma tr2 ON tr2.id = tr.emo
                                                                    JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
                                                             WHERE tk.id = ut.tehtava
                                                               AND tr.id = tk.tehtavaryhma
                                                               AND tr.otsikko = '1.0 TALVIHOITO'
                                                               AND ut.urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id))
        oulun-mhu-urakan-maarien-toteuma-muuta (q
                                                      (str "SELECT *
                                                             FROM
                                                                  urakka_tehtavamaara ut,
                                                                  toimenpidekoodi tk,
                                                                  tehtavaryhma tr
                                                                    JOIN tehtavaryhma tr2 ON tr2.id = tr.emo
                                                                    JOIN tehtavaryhma tr3 ON tr3.id = tr2.emo
                                                             WHERE tk.id = ut.tehtava
                                                               AND tr.id = tk.tehtavaryhma
                                                               AND tr.otsikko = '6 MUUTA'
                                                               AND ut.urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id))
        ]
    (is (= (count maarien-toteumat-talvihoito) (count oulun-mhu-urakan-maarien-toteuma-talvihoito)) "Määrien toteumien määrä")
    (is (= (count maarien-toteumat-muuta) (count oulun-mhu-urakan-maarien-toteuma-muuta)) "Määrien toteumien määrä")))
