(ns harja.palvelin.integraatiot.api.tyokalut
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))

(defn api-kutsu
  "Tekee POST kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  [api-polku-vec kayttaja portti body]
  @(http/post (reduce str (concat ["http://localhost:" portti] api-polku-vec))
              {:body    body
               :headers {"OAM_REMOTE_USER" kayttaja
                         "Content-Type"    "application/json"}}))