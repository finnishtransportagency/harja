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
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
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
    (is (>= (count vastaus) 3))
    (is (s/valid? ::hanke/hae-harjassa-luodut-hankkeet-vastaus vastaus))))

(deftest hankkeen-tallennus-ja-paivitys-toimii
  (let [testihankkeet (map #(dissoc % ::hanke/id) (gen/sample (s/gen ::hanke/tallenna-hanke-kysely)))]

    (doseq [hanke testihankkeet]
      ;; Luo uusi hanke
      (let [hanke-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hanke +kayttaja-jvh+
                                           hanke)]
        ;; Uusi hanke löytyy vastauksesesta
        (is (= (::hanke/nimi hanke-kannassa (::hanke/nimi hanke))))

        ;; Päivitetään hanke
        (let [paivitetty-hanke (assoc hanke ::hanke/nimi (str (::hanke/nimi hanke) " päivitetty")
                                            ::hanke/id (::hanke/id hanke-kannassa))
              paivitetty-hanke-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :tallenna-hanke +kayttaja-jvh+
                                                        paivitetty-hanke)]

          ;; Hanke päivittyi
          (is (= (::hanke/nimi paivitetty-hanke-kannassa)
                 (::hanke/nimi paivitetty-hanke))))))))
