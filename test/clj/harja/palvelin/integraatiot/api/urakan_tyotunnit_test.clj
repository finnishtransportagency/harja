(ns harja.palvelin.integraatiot.api.urakan-tyotunnit-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [clojure.java.io :as io]
            [org.httpkit.fake :refer [with-fake-http]]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-urakan-tyotunnit (component/using
                            (urakan-tyotunnit/->UrakanTyotunnit)
                            [:http-palvelin :db :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest tarkista-tietueen-lisaaminen
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        kutsu (str "/api/urakat/" urakka "/tyotunnit")
        kutsu-data (slurp (io/resource "api/examples/urakan-tyotuntien-kirjaus-request.json"))]
      (let [_ (anna-kirjoitusoikeus kayttaja)
            vastaus (api-tyokalut/post-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
        (is (.contains (:body vastaus) "Työtunnit kirjattu onnistuneesti")))))
