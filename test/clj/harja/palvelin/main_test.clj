(ns harja.palvelin.main-test
  "Testaa, että main käynnistää kaikki halutut komponentit. Tämän testin pointti on suojata
  ettei komponenttien lisäämisessä tule virheitä ja joitain tarvittuja komponentteja poistu.
  Kun lisäät komponentin, lisää se myös testin keysettiin."
  (:require [harja.palvelin.main :as sut]
            [harja.palvelin.asetukset :as asetukset]
            [harja.testi :as testi]
            [clojure.test :as t :refer [deftest is]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io File)))

(def ^:dynamic *testiasetukset* nil)

(defn- muokkaa-asetuksia [asetukset]
  (let [asetukset-datana (-> (edn/read-string asetukset)
                             (assoc-in [:http-palvelin :portti] (testi/arvo-vapaa-portti))
                             (assoc-in [:http-palvelin :salli-oletuskayttaja?] false)
                             (assoc-in [:http-palvelin :dev-resources-path] "dev-resources")
                             (assoc :tietokanta testi/testitietokanta)
                             (assoc :tietokanta-replica testi/testitietokanta)
                             (assoc :sonja {:url "tcp://localhost:61617"
                                            :kayttaja ""
                                            :salasana ""
                                            :tyyppi :activemq})
                             (assoc :sampo {})
                             (assoc :tloik {})
                             (assoc-in [:turi :turvallisuuspoikkeamat-url] "")
                             (assoc-in [:turi :urakan-tyotunnit-url] ""))]
    (str asetukset-datana)))

(defn- poista-sisimmat-sulut-reader-makrosta [s]
  (str/replace s #"\#=\(.*(\([^\(\)]*\))" (fn [args]
                                            (case (count args)
                                              0 ""
                                              1 (first args)
                                              2 (apply str (let [lopputulos (first args)
                                                                 pudotettavien-maara (count (second args))]
                                                             (drop-last pudotettavien-maara lopputulos)))))))

(defn- poista-reader-makrot [teksti korvaava-teksti]
  (loop [teksti teksti
         sisaltaa-readermakroja? (re-find #"\#=" teksti)]
    (if sisaltaa-readermakroja?
      (recur (-> teksti
                 poista-sisimmat-sulut-reader-makrosta
                 (str/replace #"\#=\([^\(\)]*\)" korvaava-teksti))
             (re-find #"\#=" teksti))
      teksti)))

(defn- testiasetukset [testit]
  (let [file (File/createTempFile "asetukset" ".edn")
        asetukset (-> "asetukset.edn"
                      slurp
                      (poista-reader-makrot "\"foo\"")
                      muokkaa-asetuksia)]
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
    :integraatioloki :sonja :sonja-sahkoposti :solita-sahkoposti :fim :sampo :tloik :tierekisteri :labyrintti
    :turi :yha-integraatio :velho-integraatio :raportointi :paivystystarkistukset :reittitarkistukset
    :kayttajatiedot :urakoitsijat :hallintayksikot :ping :pois-kytketyt-ominaisuudet :haku
    :indeksit :urakat :urakan-toimenpiteet :yksikkohintaiset-tyot :kokonaishintaiset-tyot :budjettisuunnittelu :tehtavamaarat
    :muut-tyot :laskut :aliurakoitsijat :toteumat :yllapitototeumat :paallystys :maaramuutokset
    :yllapitokohteet :muokkauslukko :yhteyshenkilot :toimenpidekoodit :pohjavesialueet
    :materiaalit :selainvirhe :valitavoitteet :siltatarkastukset :lampotilat :maksuerat
    :liitteet :laadunseuranta :tarkastukset :ilmoitukset :tietyoilmoitukset
    :turvallisuuspoikkeamat :integraatioloki-palvelu :raportit :yha :velho :tr-haku
    :geometriapaivitykset :api-yhteysvarmistus :sonja-jms-yhteysvarmistus :tilannekuva
    :tienakyma :karttakuvat :debug :sahke :api-jarjestelmatunnukset :geometria-aineistot
    :organisaatiot :api-urakat :api-laatupoikkeamat :api-paivystajatiedot :api-pistetoteuma
    :api-reittitoteuma :api-varustetoteuma :api-siltatarkastukset :api-tarkastukset
    :api-tyokoneenseuranta :api-tyokoneenseuranta-puhdistus :api-turvallisuuspoikkeama
    :api-suolasakkojen-lahetys :api-varusteet :api-ilmoitukset :api-yllapitokohteet :api-ping
    :api-yhteystiedot :api-tiemerkintatoteuma :laskutusyhteenvetojen-muodostus :status
    :vaylien-geometriahaku
    :kanavasiltojen-geometriahaku
    :mobiili-laadunseuranta
    :api-urakan-tyotunnit
    :sopimukset
    :urakan-tyotuntimuistutukset
    :hankkeet
    :urakan-tyotunnit
    :vv-toimenpiteet
    :vv-vaylat
    :vv-kiintiot
    :vv-hinnoittelut
    :vv-materiaalit
    :reimari
    :vkm
    :vv-turvalaitteet
    :hairioilmoitukset
    :ais-data
    :vv-alukset
    :kan-kohteet
    :kan-liikennetapahtumat
    :kan-hairio
    :kan-toimenpiteet
    :api-tieluvat
    :api-paikkaukset
    :koordinaatit
    :tiedostopesula
    :tieluvat
    :paikkaukset
    :jarjestelman-tila
    :yha-paikkauskomponentti
    :pot2})

(deftest main-komponentit-loytyy
  (let [jarjestelma (sut/luo-jarjestelma (asetukset/lue-asetukset *testiasetukset*))
        komponentit (set (keys jarjestelma))]
    (doseq [k halutut-komponentit]
      (is (komponentit k) (str "Haluttu komponentti avaimella " k " puuttuu!")))
    (doseq [k komponentit]
      (is (halutut-komponentit k) (str "Ylimääräinen komponentti avaimella " k ", lisää testiin uudet komponentit!")))))

#_(deftest restart-toimii
  (is (= :ok (sut/dev-restart))))
