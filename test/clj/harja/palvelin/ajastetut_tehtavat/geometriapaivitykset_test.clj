(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialue :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [clj-time.core :as t]
            [clj-time.coerce :as time-coerce])
  (:use org.httpkit.fake))

(defn aja-tieverkon-paivitys []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (geometriapaivitykset/tarkista-paivitys
      alk
      testitietokanta
      "tieverkko"
      "http://185.26.50.104/Tieosoiteverkko.zip"
      "/Users/mikkoro/Desktop/Tieverkko-testi/"
      "Tieosoiteverkko.zip"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Tieverkko-testi/Tieosoiteverkko.shp")))))

(defn aja-pohjavesialueen-paivitys []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (geometriapaivitykset/tarkista-paivitys
      alk
      testitietokanta
      "pohjavesialue"
      "http://185.26.50.104/Pohjavesialue.zip"
      "/Users/jarihan/Desktop/Pohjavesialue-testi/"
      "Pohjavesialue.zip"
      (fn []
        (pohjavesialueen-tuonti/vie-pohjavesialue-kantaan
          testitietokanta
          "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Pohjavesialue.shp")))))

(defn aja-soratien-hoitoluokkien-paivitys []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (geometriapaivitykset/tarkista-paivitys
      alk
      testitietokanta
      "tieverkko"
      "http://185.26.50.104/tl132.tgz"
      "/Users/mikkoro/Desktop/Soratiehoitoluokat-testi/"
      "Sorateiden-hoitoluokat.tgz"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Soratiehoitoluokat-testi/Sorateiden-hoitoluokat.shp")))))

(def kayttaja "jvh")

(deftest testaa-tiedoston-muokkausajan-selvitys-alk-alustalla []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)
        fake-tiedosto-url "http://www.example.com/file.zip"
        fake-muokkausaika "Tue, 15 Nov 1994 12:45:26 GMT"
        fake-vastaus {:status 200 :headers {:last-modified fake-muokkausaika} :body "ok"}]
    (component/start integraatioloki)
    (component/start alk)

    (with-fake-http
      [{:url fake-tiedosto-url :method :head} fake-vastaus]
      (let [muokkausaika (alk/hae-tiedoston-muutospaivamaara alk "tieverkko-muutospaivamaaran-haku" fake-tiedosto-url)]
        (is (= muokkausaika (time-coerce/to-sql-time (java.util.Date. fake-muokkausaika))))))))