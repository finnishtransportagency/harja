(ns harja.palvelin.raportointi-test
  (:require [harja.palvelin.raportointi :as r]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clojure.test :refer [deftest is testing] :as t]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (apply tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :pdf-vienti (component/using
                                   (pdf-vienti/luo-pdf-vienti)
                                   [:http-palvelin])
                      :raportointi (component/using
                                    (r/luo-raportointi)
                                    [:db :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(t/use-fixtures :each jarjestelma-fixture)

(deftest raporttien-haku-toimii
  (let [r (r/hae-raportit (:raportointi jarjestelma))]
    (is (contains? r :laskutusyhteenveto) "Laskutusyhteenveto löytyy raporteista")
    (is (contains? r :materiaaliraportti) "Materiaaliraportti löytyy raporteista")
    (is (contains? r :yks-hint-tyot) "Yksikköhintaiset työt löytyy raporteista")
    (is (not (contains? r :olematon-raportti)))))

