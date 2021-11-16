(ns harja.palvelin.palvelut.tierekisteri-haku-test
  (:require [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tienakyma :as tienakyma]
            [slingshot.slingshot :refer [try+]]
            [harja.paneeliapurit :as paneeli]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku])
  (:import [harja.domain.roolit EiOikeutta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tienakyma (component/using
                                     (tierekisteri-haku/->TierekisteriHaku)
                                     [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(def tienumero 6666)

(defn luo-tr-osoite [[kaista osa a-et l-et tyyppi]]
  (u (str
       "INSERT INTO tr_osoitteet
        (\"tr-numero\", \"tr-ajorata\", \"tr-kaista\", \"tr-osa\",  \"tr-alkuetaisyys\", \"tr-loppuetaisyys\", tietyyppi)
        VALUES (" tienumero ", 1, " kaista ", " osa ", " a-et ", " l-et ", " tyyppi ")")))

(defn luo-tr-osoitteet [osoitteet]
  (u (str "DELETE FROM tr_osoitteet WHERE \"tr-numero\" = " tienumero))
  (doseq [osoite osoitteet]
    (luo-tr-osoite osoite))
  (u "REFRESH MATERIALIZED VIEW tr_tiedot"))

(defn- kutsu
  ([kayttaja payload] (kutsu :hae-tr-tiedot kayttaja payload))
  ([palvelu kayttaja payload]
   (kutsu-palvelua (:http-palvelin jarjestelma) palvelu kayttaja payload)))

(defn parametrit
  [a b c]
  {:tr-numero a
   :tr-alkuosa b
   :tr-loppuosa c})

(defn tarkista-oikeus-poikkeus [kutsu-fn]
  (try+
    (kutsu-fn)
    (is false "EiOikeutta poikkeusta ei heitetty")
    (catch EiOikeutta e
      (is true "EiOikeutta poikkeus heitetty"))))

(deftest sama-kaista-ja-rako
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [11 1 1500 2500 1]
                     [11 1 4500 5000 1]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 3000,
                                   :ajoradat [{:osiot [{:pituus 2500,
                                                        :kaistat
                                                        [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                         {:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                        :tr-alkuetaisyys 0}
                                                       {:pituus 500,
                                                        :kaistat
                                                        [{:pituus 500, :tr-kaista 11, :tr-alkuetaisyys 4500}],
                                                        :tr-alkuetaisyys 4500}],
                                               :tr-ajorata 1}],
                                   :tr-alkuetaisyys 0}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

(deftest eri-kaistat-kaikki-sisalla
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [21 1 100 300 1]
                     [11 1 1500 2500 1]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 2500,
                                   :ajoradat [{:osiot
                                               [{:pituus 1500,
                                                 :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                           {:pituus 200, :tr-kaista 21, :tr-alkuetaisyys 100}],
                                                 :tr-alkuetaisyys 0}
                                                {:pituus 1000,
                                                 :kaistat [{:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                 :tr-alkuetaisyys 1500}],
                                               :tr-ajorata 1}],
                                   :tr-alkuetaisyys 0}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

(deftest eri-kaistat-kaksi-sisalla
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [21 1 100 300 1]
                     [21 1 350 400 1]
                     [11 1 1500 2500 1]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 2500,
                                   :ajoradat [{:osiot [{:pituus 1500,
                                                        :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                                  {:pituus 200, :tr-kaista 21, :tr-alkuetaisyys 100}],
                                                        :tr-alkuetaisyys 0}
                                                       {:pituus 50,
                                                        :kaistat [{:pituus 50, :tr-kaista 21, :tr-alkuetaisyys 350}],
                                                        :tr-alkuetaisyys 350}
                                                       {:pituus 1000,
                                                        :kaistat [{:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                        :tr-alkuetaisyys 1500}],
                                               :tr-ajorata 1}],
                                   :tr-alkuetaisyys 0}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

(deftest eri-kaistat-menee-yli
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [21 1 100 2300 1]
                     [11 1 1500 2500 1]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 2500,
                                   :ajoradat [{:osiot
                                               [{:pituus 2500,
                                                 :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                           {:pituus 2200, :tr-kaista 21, :tr-alkuetaisyys 100}
                                                           {:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                 :tr-alkuetaisyys 0}],
                                               :tr-ajorata 1}],
                                   :tr-alkuetaisyys 0}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

(deftest eri-kaistat-menee-kaikien-yli
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [21 1 100 3300 1]
                     [11 1 1500 2500 1]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 2500,
                                   :ajoradat [{:osiot
                                               [{:pituus 3300,
                                                 :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                           {:pituus 3200, :tr-kaista 21, :tr-alkuetaisyys 100}
                                                           {:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                 :tr-alkuetaisyys 0}],
                                               :tr-ajorata 1}],
                                   :tr-alkuetaisyys 0}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

