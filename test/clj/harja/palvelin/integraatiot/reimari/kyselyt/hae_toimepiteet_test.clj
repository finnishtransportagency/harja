(ns harja.palvelin.integraatiot.reimari.kyselyt.hae-toimepiteet-test
  (:require  [clojure.test :refer [deftest use-fixtures compose-fixtures is]]
             [harja.testi :as ht]
             [harja.palvelin.komponentit.tietokanta :as tietokanta]
             [com.stuartsierra.component :as component]
             [harja.kyselyt.vesivaylat :as vv-kyselyt]))


;; (use-fixtures :once ht/tietokanta-fixture)



(defn jarjestelma-fixture [testit]
  (alter-var-root #'ht/jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (tietokanta/luo-tietokanta ht/testitietokanta)
                      ;; :http-palvelin (testi-http-palvelin)
                      ))))

  (testit)
  (alter-var-root #'ht/jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                     jarjestelma-fixture
                     ht/urakkatieto-fixture))


(deftest hae-toimenpiteet-kysely
  (ht/u "INSERT INTO reimari_toimenpide (\"reimari-id\", urakoitsija, sopimus, turvalaite, lisatieto, lisatyo, suoritettu, luotu, \"reimari-luotu\", alus, tila, tyyppi, tyolaji, tyoluokka, vayla, \"reimari-muokattu\", \"luoja\", \"muokkaaja\", \"muokattu\", vastuuhenkilo, asiakas) values (42, '(55, Ukko Urakoitsija)', '(-5, 1022542301, Hoitosopimus)' , '(62, Pysäytin, 555)', '', false, '2017-01-01T23:23Z', '2017-01-01', '2017-01-01', '(MBKE24524, MS Piggy)', '1022541202', '1022542001', '1022541802', 1022541905, '(123, Vayla X, 55)', '2017-11-11', 1, 1, '2016-02-02', 'Vesa Vastuullinen', 'Aapo Asiakas');")
  (is (= #{:harja.domain.muokkaustiedot/poistaja-id} (clojure.set/difference vv-kyselyt/kaikki-toimenpiteen-kentat (set (keys (first (vv-kyselyt/hae-toimenpiteet ht/ds {:urakoitsija-id 55})))))))
  )
