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

(def tr-osoite-malli {})

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

(deftest testa-mita-vaan
  (luo-tr-osoitteet [[11 1 0 1500 1]
                     [11 1 1500 2500 1]])
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= 0 tulos) "Kaikki tilaajan käyttäjät saavat saman tuloksen")))
