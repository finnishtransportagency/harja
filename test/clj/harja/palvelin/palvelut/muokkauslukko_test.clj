(ns harja.palvelin.palvelut.muokkauslukko-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.muokkauslukko :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
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
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
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
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest nakyman-lukitseminen-toimii
  (let [aika-nyt (t/now)
        lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                              :lukitse
                              +kayttaja-jvh+ {:id "tyhmanakyma_123"})]
    (log/debug "Lukko: " (pr-str lukko))
    (is (not (nil? lukko)))
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
    (is (not (nil? lukko)))
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
    (is (not (nil? lukko-oli-olemassa)))
    (is (= (:id lukko-oli-olemassa) "tyhmalukko_007"))
    (is (nil? lukko-vapautui))))

(deftest lukon-virkistaminen-toimii
  (let [vanha-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lukitse
                                    +kayttaja-jvh+ {:id "tyhmalukko_2015"})
        _ (Thread/sleep 1000)
        virkistetty-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :virkista-lukko
                                          +kayttaja-jvh+ {:id "tyhmalukko_2015"})]
    (is (not (nil? vanha-lukko)))
    (is (not (nil? virkistetty-lukko)))
    (is (true? (t/after? (coerce/from-sql-time (:aikaleima virkistetty-lukko))
                         (coerce/from-sql-time (:aikaleima vanha-lukko)))))))

(deftest kayttajan-A-lukitseman-nakyman-lukitseminen-ei-onnistu-kayttajalta-B
  (let [jvh-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lukitse
                                    +kayttaja-jvh+ {:id "jvh_2015"})
        tero-lukko (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :lukitse
                                  +kayttaja-tero+ {:id "jvh_2015"})]
    (is (not (nil? jvh-lukko)))
    (is (nil? tero-lukko))))