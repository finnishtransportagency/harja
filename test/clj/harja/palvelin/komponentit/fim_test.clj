(ns harja.palvelin.komponentit.fim-test
  (:require [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim :as fim]))

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :todennus (component/using
                      (todennus/http-todennus nil)
                      [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest kayttajaroolien-suodatus-toimii
  (let [kayttajat [{:roolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}}
                   {:roolit #{"urakan vastuuhenkilö"}}
                   {:roolit #{"ely urakanvalvoja"}}]
        pida-kaikki #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
        pida-urakanvalvoja #{"ely urakanvalvoja"}
        pida-vastuuhenkilo #{"urakan vastuuhenkilö"}]
    ;; Pidetään kaikki
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-kaikki)) 3))
    (is (every? #(% "ely urakanvalvoja")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja))))
    (is (every? #(% "urakan vastuuhenkilö")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo))))

    ;; Pidetään urakanvalvojat
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja)) 2))
    (is (every? #(% "ely urakanvalvoja")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja))))

    ;; Pidetään vastuuhenkilöt
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo)) 2))
    (is (every? #(% "urakan vastuuhenkilö")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo))))))