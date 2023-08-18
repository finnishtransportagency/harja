(ns harja.palvelin.integraatiot.api.urakan-tyotunnit-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [clojure.java.io :as io]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(def kayttaja "yit-rakennus")
(def +testi-turi-url+ "harja.testi.turi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :turi (component/using
            (turi/->Turi {:urakan-tyotunnit-url +testi-turi-url+})
            [:db :integraatioloki :liitteiden-hallinta])
    :api-urakan-tyotunnit (component/using
                            (urakan-tyotunnit/->UrakanTyotunnit)
                            [:http-palvelin :db :integraatioloki :turi])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest tarkista-tietueen-lisaaminen
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        kutsu (str "/api/urakat/" urakka "/tyotunnit")
        kutsu-data (slurp (io/resource "api/examples/urakan-tyotuntien-kirjaus-request.json"))]
    (with-fake-http [+testi-turi-url+ (fn [_ _ _]
                                        {:status 200 :body "ok"})
                     (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/post-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
        (is (.contains (:body vastaus) "Työtunnit kirjattu onnistuneesti"))))))
