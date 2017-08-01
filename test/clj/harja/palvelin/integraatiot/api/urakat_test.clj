(ns harja.palvelin.integraatiot.api.urakat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakat :as api-urakat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.fmt :as fmt]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-urakat
                                           (component/using
                                             (api-urakat/->Urakat)
                                             [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-jarjestelmakayttajan-urakat
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "yit-rakennus" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 4 (count (:urakat encoodattu-body))) "YIT:lle löytyy oikea määrä urakoita"))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "tuntematon-jarjestelma" portti)]
    (is (= 403 (:status vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "carement" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 1 (count (:urakat encoodattu-body))))
    (is (= "Oulun alueurakka 2014-2019" (get-in (first (:urakat encoodattu-body)) [:urakka :tiedot :nimi])))))


