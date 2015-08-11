(ns harja.palvelin.integraatiot.api.tyokoneenseuranta-test
  (:require [harja.palvelin.integraatiot.api.tyokoneenseuranta :refer :all :as tyokoneenseuranta]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [clojure.test :refer :all]))

(def kayttaja "fastroi")

(def jarjestelma-fixture (laajenna-integraatiojarjestelmafixturea kayttaja
                          :api-tyokoneenseuranta (component/using
                                                  (tyokoneenseuranta/->Tyokoneenseuranta)
                                                  [:http-palvelin :db :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest tallenna-tyokoneen-seurantakirjaus
  (let [kutsu (api-tyokalut/post-kutsu ["/api/seuranta/tyokone"] kayttaja portti (slurp "test/resurssit/api/tyokoneseuranta.json"))]
    (is (= 200) (:status kutsu))
    (is (= "Hello world" (:body kutsu)))))
