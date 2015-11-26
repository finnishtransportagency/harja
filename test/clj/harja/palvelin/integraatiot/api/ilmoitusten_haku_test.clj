(ns harja.palvelin.integraatiot.api.ilmoitusten-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as http]
            [clojure.java.io :as io]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-ilmoitukset (component/using
                                                              (api-ilmoitukset/->Ilmoitukset)
                                                              [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-ilmoitusten-haku
  (let [vastaus (http/get (str "http://localhost:8000/api/urakat/4/ilmoitukset")
                          {:headers {"OAM_REMOTE_USER" kayttaja
                                     "Content-Type"    "application/json"}
                           :as :stream}
                          (fn [vastaus]

                            (println "VASTAUS: " vastaus)
                            (let [lukija (io/reader (:body vastaus))]
                              (println "LUETTIN 1: " (cheshire/decode-stream lukija))
                              (println "LUETTIN 2: " (cheshire/decode-stream lukija)))))]))
