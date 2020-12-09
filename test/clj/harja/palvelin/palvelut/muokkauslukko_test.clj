(ns harja.palvelin.palvelut.muokkauslukko-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muokkauslukko :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.muokkauslukko :as lukko]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-lukko-idlla (component/using
                                           (->Muokkauslukko)
                                           [:http-palvelin :db])
                        :lukitse (component/using
                                   (->Muokkauslukko)
                                   [:http-palvelin :db])
                        :tallenna-paallystysilmoitus (component/using
                                                       (->Muokkauslukko)
                                                       [:http-palvelin :db])
                        :vapauta-lukko (component/using
                                         (->Muokkauslukko)
                                         [:http-palvelin :db])
                        :virkista-lukko (component/using
                                          (->Muokkauslukko)
                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      (compose-fixtures jarjestelma-fixture tietokanta-fixture)
                      urakkatieto-fixture))

(deftest nakyman-lukitseminen-toimii
  (let [aika-nyt (t/now)
        lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                              :lukitse
                              +kayttaja-jvh+ {:id "tyhmanakyma_123"})]
    (log/debug "Lukko: " (pr-str lukko))
    (is (not (= :ei-lukittu lukko)))
    (is (= (:id lukko) "tyhmanakyma_123"))
    (is (= (:kayttaja lukko) (:id +kayttaja-jvh+)))
    (is (= (t/day (coerce/from-sql-time (:aikaleima lukko))) (t/day aika-nyt)))
    (is (= (t/month (coerce/from-sql-time (:aikaleima lukko))) (t/month aika-nyt)))
    (is (= (t/year (coerce/from-sql-time (:aikaleima lukko))) (t/year aika-nyt)))))

(deftest lukon-hakeminen-toimii
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :lukitse
                          +kayttaja-jvh+ {:id "tyhmalukko_666"})
        lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-lukko-idlla
                              +kayttaja-jvh+ {:id "tyhmalukko_666"})]
    (log/debug "Lukko: " (pr-str lukko))
    (is (not (= :ei-lukittu lukko)))
    (is (= (:id lukko) "tyhmalukko_666"))))

(deftest lukon-vapauttaminen-toimii
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :lukitse
                          +kayttaja-jvh+ {:id "tyhmalukko_007"})
        lukko-oli-olemassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-lukko-idlla
                                           +kayttaja-jvh+ {:id "tyhmalukko_007"})
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :vapauta-lukko
                          +kayttaja-jvh+ {:id "tyhmalukko_007"})
        lukko-vapautui (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :hae-lukko-idlla
                                       +kayttaja-jvh+ {:id "tyhmalukko_007"})]
    (is (not (= :ei-lukittu lukko-oli-olemassa)))
    (is (= (:id lukko-oli-olemassa) "tyhmalukko_007"))
    (is (= :ei-lukittu lukko-vapautui))))

(deftest lukon-virkistaminen-toimii
  (let [vanha-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lukitse
                                    +kayttaja-jvh+ {:id "tyhmalukko_2015"})
        _ (Thread/sleep 1000)
        virkistetty-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :virkista-lukko
                                          +kayttaja-jvh+ {:id "tyhmalukko_2015"})]
    (is (not (= :ei-lukittu vanha-lukko)))
    (is (not (= :ei-lukittu virkistetty-lukko)))
    (is (true? (t/after? (coerce/from-sql-time (:aikaleima virkistetty-lukko))
                         (coerce/from-sql-time (:aikaleima vanha-lukko)))))))

(deftest kayttajan-A-lukitseman-nakyman-lukitseminen-ei-onnistu-kayttajalta-B
  (let [jvh-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :lukitse
                                  +kayttaja-jvh+ {:id "nakyma1"})
        tero-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :lukitse
                                   +kayttaja-tero+ {:id "nakyma1"})]
    (is (not (nil? jvh-lukko)))
    (is (= tero-lukko :ei-lukittu))))

(deftest kayttajan-A-ei-voi-virkistaa-kayttajan-B-lukkoa
  (let [jvh-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :lukitse
                                  +kayttaja-jvh+ {:id "jvh_lukko_2015"})
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :virkista-lukko
                          +kayttaja-tero+ {:id "jvh_lukko_2015"})
        jvh-lukko-uudestaan (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-lukko-idlla
                                            +kayttaja-jvh+ {:id "jvh_lukko_2015"})]
    (is (not (= :ei-lukittu jvh-lukko)))
    (is (not (= :ei-lukittu jvh-lukko-uudestaan)))
    (is (true? (t/equal? (coerce/from-sql-time (:aikaleima jvh-lukko))
                         (coerce/from-sql-time (:aikaleima jvh-lukko-uudestaan)))))))

(deftest kayttajan-A-ei-voi-vapauttaa-kayttajan-B-lukkoa
  (let [jvh-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :lukitse
                                  +kayttaja-jvh+ {:id "jvhlukko_2015"})
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :vapauta-lukko
                          +kayttaja-tero+ {:id "jvhlukko_2015"})
        jvh-lukko-uudestaan (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-lukko-idlla
                                            +kayttaja-jvh+ {:id "jvhlukko_2015"})]
    (is (not (nil? jvh-lukko)))
    (is (not (= :ei-lukittu jvh-lukko-uudestaan)))))

(deftest vanha-lukko-lasketaan-oikein
  (let [tuore-lukko {:aikaleima (coerce/to-sql-time (t/minus (t/now) (t/minutes 3)))
                     :ika 180}
        vanha-lukko {:aikaleima (coerce/to-sql-time (t/minus (t/now) (t/minutes 10)))
                     :ika 600}]
    (is (false? (lukko/lukko-vanhentunut? tuore-lukko)))
    (is (true? (lukko/lukko-vanhentunut? vanha-lukko)))))

(deftest vanhentunut-lukko-tuhotaan-jos-se-haetaan
  (let [lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                              :lukitse
                              +kayttaja-jvh+ {:id "vanheneva_lukko"})
        _ (u (str "UPDATE muokkauslukko SET aikaleima = '2000-12-20 00:00:00+02' WHERE id = 'vanheneva_lukko';"))
        poistettu-vanha-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :hae-lukko-idlla
                                              +kayttaja-jvh+ {:id "vanheneva_lukko"})]
    (is (not (nil? lukko)))
    (is (= :ei-lukittu poistettu-vanha-lukko))))
