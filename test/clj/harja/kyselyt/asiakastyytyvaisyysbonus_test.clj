(ns harja.kyselyt.asiakastyytyvaisyysbonus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.testi :refer :all]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(deftest laske-hoitokauden-asiakastyytyvaisyysbonus
  (let [sop @oulun-alueurakan-2014-2019-paasopimuksen-id
        maksupvm (ffirst (q (str "select pvm from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        ind_nimi (ffirst (q (str "select indeksin_nimi from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        summa (ffirst (q (str "select rahasumma from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        kyselyn-kautta (laskutusyhteenveto/laske-asiakastyytyvaisyysbonus
                         (:db jarjestelma)
                         {:maksupvm    maksupvm
                          :indeksinimi ind_nimi
                          :summa       summa})
        bonarit (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus('2015-11-15'::DATE, '"
                               ind_nimi "', '"
                               summa "');")))
        bonarit-jos-indekseja-ei-ole-syotetty (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus('2025-11-15'::DATE, '"
                                                             ind_nimi "', '"
                                                             summa "');")))]
    (testing "Testidatan Oulun alueurakka 2014 - 2019 lasketaan oikein"
      (is {:summa 1000M, :korotettuna 1050.1666666666667000M, :korotus 50.1666666666667000M} kyselyn-kautta)
      (is (= 1000M (first bonarit)) "bonari ilman korotusta")
      (is (= 1050.1666666666667000M (second bonarit)) "bonari korotuksen kera")
      (is (= 50.1666666666667000M (nth bonarit 2)) "bonarin korotus")
      (is (= [nil nil nil] bonarit-jos-indekseja-ei-ole-syotetty)))))

