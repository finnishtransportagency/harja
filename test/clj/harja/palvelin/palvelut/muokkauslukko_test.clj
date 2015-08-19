(ns harja.palvelin.palvelut.muokkauslukko-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
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
    (is (false? (t/before? (coerce/from-sql-time (:aikaleima lukko)) aika-nyt)))))