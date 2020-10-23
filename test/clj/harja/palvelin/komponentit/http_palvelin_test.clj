(ns harja.palvelin.komponentit.http-palvelin-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.kyselyt.konversio :as konv]
            [clojure.test :refer :all]
            [clojure.string :as clj-str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+]]

            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])))

(use-fixtures :each jarjestelma-fixture)

(defn ok-get-palvelu [user] {})

(defn bad-request-get-palvelu [user]
  (throw (IllegalArgumentException. "bad request")))

(defn internal-server-error-get-palvelu [user]
  (throw (RuntimeException. "internal server error")))

(deftest get-palvelu-palauta-ok
  (println "petar palvelin " (:http-palvelin jarjestelma))
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) :ok-palvelu ok-get-palvelu)
  (let [vastaus (api-tyokalut/get-kutsu ["/_/ok-palvelu"] kayttaja portti)]
    (println "petar vastaus " vastaus)
    (is (= 200 (:status vastaus)))))

