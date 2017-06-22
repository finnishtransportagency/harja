(ns harja.palvelin.main-test
  "Testaa, että main käynnistää kaikki halutut komponentit. Tämän testin pointti on suojata
  ettei komponenttien lisäämisessä tule virheitä ja joitain tarvittuja komponentteja poistu.
  Kun lisäät komponentin, lisää se myös testin keysettiin."
  (:require [harja.palvelin.main :as sut]
            [harja.palvelin.asetukset :as asetukset]
            [clojure.test :as t :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

(def ^:dynamic *testiasetukset* nil)
(defn- testiasetukset [testit]
  (let [file (File/createTempFile "asetukset" ".edn")
        asetukset (-> "asetukset.edn"
                      slurp
                      (str/replace #"\#=\(.*?\)" "\"foo\""))]
    (spit file asetukset)
    (binding [*testiasetukset* file]
      (testit))
    (.delete file)))

(t/use-fixtures :once testiasetukset)

(def halutut-komponentit
  #{:metriikka
    :db :db-replica :klusterin-tapahtumat
    :todennus :http-palvelin
    :pdf-vienti :excel-vienti
    :virustarkistus :liitteiden-hallinta :kehitysmoodi
    :integraatioloki :sonja :sonja-sahkoposti :fim :sampo :tloik :tierekisteri :labyrintti
    :turi :yha-integraatio :raportointi :paivystystarkistukset :reittitarkistukset
    :kayttajatiedot :urakoitsijat :hallintayksikot :ping :pois-kytketyt-ominaisuudet :haku
    :indeksit :urakat :urakan-toimenpiteet :yksikkohintaiset-tyot :kokonaishintaiset-tyot
    :muut-tyot :toteumat :yllapitototeumat :paallystys :maaramuutokset :paikkaus
    :yllapitokohteet :muokkauslukko :yhteyshenkilot :toimenpidekoodit :pohjavesialueet
    :materiaalit :selainvirhe :valitavoitteet :siltatarkastukset :lampotilat :maksuerat
    :liitteet :laadunseuranta :tarkastukset :ilmoitukset :tietyoilmoitukset
    :turvallisuuspoikkeamat :integraatioloki-palvelu :raportit :yha :tr-haku
    :geometriapaivitykset :api-yhteysvarmistus :sonja-jms-yhteysvarmistus :tilannekuva
    :tienakyma :karttakuvat :debug :sahke :api-jarjestelmatunnukset
    :organisaatiot :api-urakat :api-laatupoikkeamat :api-paivystajatiedot :api-pistetoteuma
    :api-reittitoteuma :api-varustetoteuma :api-siltatarkastukset :api-tarkastukset
    :api-tyokoneenseuranta :api-tyokoneenseuranta-puhdistus :api-turvallisuuspoikkeama
    :api-suolasakkojen-lahetys :api-varusteet :api-ilmoitukset :api-yllapitokohteet :api-ping
    :api-yhteystiedot :api-tiemerkintatoteuma :laskutusyhteenvetojen-muodostus :status
    :turvalaitteiden-geometriahaku :mobiili-laadunseuranta
    :api-urakan-tyotunnit
    :sopimukset
    :urakan-tyotuntimuistutukset
    :hankkeet
    :urakan-tyotunnit
    :vv-yksikkohintaiset :vv-kokonaishintaiset :vv-vaylat :vv-hinnoittelut :vv-materiaalit
    :reimari
    :vkm})

(deftest main-komponentit-loytyy
  (let [jarjestelma (sut/luo-jarjestelma (asetukset/lue-asetukset *testiasetukset*))
        komponentit (set (keys jarjestelma))]
    (doseq [k halutut-komponentit]
      (is (komponentit k) (str "Haluttu komponentti avaimella " k " puuttuu!")))
    (doseq [k komponentit]
      (is (halutut-komponentit k) (str "Ylimääräinen komponentti avaimella " k ", lisää testiin uudet komponentit!")))))

#_(deftest restart-toimii
  (is (= :ok (sut/dev-restart))))
