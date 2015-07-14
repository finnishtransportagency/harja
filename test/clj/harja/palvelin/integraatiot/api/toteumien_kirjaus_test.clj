(ns harja.palvelin.integraatiot.api.toteumien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.api.tyokalut :as apityokalut]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(def portti nil)
(def kayttaja "fastroi")
(def urakka nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'portti (fn [_] (arvo-vapaa-portti)))
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])

                        :todennus (component/using
                                    (todennus/http-todennus)
                                    [:db :klusterin-tapahtumat])
                        :http-palvelin (component/using
                                         (http-palvelin/luo-http-palvelin portti true)
                                         [:todennus])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :api-toteumat (component/using
                                        (api-pistetoteuma/->Pistetoteuma)
                                        [:http-palvelin :db :integraatioloki])))))

  (alter-var-root #'urakka
                  (fn [_]
                    (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" kayttaja "') "
                                    " AND tyyppi='hoito'::urakkatyyppi")))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-tiestotarkastus
  (is true))

(deftest tallenna-pistetoteuma
  (let [pvm (Date.)
        id (rand-int 10000)                                 ;; FIXME: varmista että ei ole olemassa
        vastaus (apityokalut/api-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                           (-> "test/resurssit/api/pistetoteuma.json"
                               slurp
                               (.replace "__PVM__" (json-tyokalut/json-pvm pvm))
                               (.replace "__ID__" (str id))))]

    (is (= 200 (:status vastaus))))) ;; FIXME Varmistaettä toteuma löytyy tietokannasta ja tiedot on ok