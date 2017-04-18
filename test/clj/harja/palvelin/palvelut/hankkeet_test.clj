(ns harja.palvelin.palvelut.hankkeet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.hanke :as hanke]
            [taoensso.timbre :as log]
            [clojure.spec.gen :as gen]
            [clojure.spec :as s]
            [clojure.string :as str]
            [harja.palvelin.palvelut.hankkeet :as hankkeet]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hankkeet (component/using
                                    (hankkeet/->Hankkeet)
                                    [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hankkeiden-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-harjassa-luodut-hankkeet +kayttaja-jvh+ {})]

    (is (>= (count vastaus) 10))))

(deftest hankkeen-tallennus-ja-paivitys-toimii
  (let [testihankkeet (map #(dissoc % :id) (gen/sample (s/gen ::hanke/hanke)))]

    (doseq [hanke testihankkeet]
      ;; Luo uusi hanke
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-hanke +kayttaja-jvh+
                                    {:hanke hanke})]
        ;; Uusi hanke löytyy vastaukset
        (is (some? (filter #(= (:nimi %) (:nimi hanke)) vastaus)))))))
