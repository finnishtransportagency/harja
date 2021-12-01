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

(defn luo-tr-osoite [[osa a-et l-et kaista]]
  (u (str
       "INSERT INTO tr_osoitteet
        (\"tr-numero\", \"tr-ajorata\", \"tr-kaista\", \"tr-osa\",  \"tr-alkuetaisyys\", \"tr-loppuetaisyys\", tietyyppi)
        VALUES (" tienumero ", 1, " kaista ", " osa ", " a-et ", " l-et ", 1)")))

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

(deftest sama-kaista-ja-rako
  (luo-tr-osoitteet [[1 0 1500 11]
                     [1 1500 2500 11]
                     [1 4500 5000 11]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 3000,
                                   :tr-alkuetaisyys 0,
                                   :ajoradat [{:osiot [{:pituus 2500,
                                                        :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                                                        :tr-alkuetaisyys 0}
                                                       {:pituus 500,
                                                        :kaistat [{:pituus 500, :tr-kaista 11, :tr-alkuetaisyys 4500}],
                                                        :tr-alkuetaisyys 4500}],
                                               :tr-ajorata 1}]}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

