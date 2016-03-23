(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeaman-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(def kayttaja "jvh")
(def +turi-url+ "http://localhost:1234/turvallisuuspoikkeama")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :turi (component/using
            (turi/->Turi {:turi {:url +turi-url+ :kayttajatunnus "testi" :salasana "testi"}})
            [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn hae-turvallisuuspoikkeaman-tila [id]
  (let [tila (first (q (format "select lahetetty, lahetys_onnistunut from turvallisuuspoikkeama where id = %s" id)))]
    {:lahetetty (first tila)
     :lahetys_onnistunut (second tila)}))

(deftest tarkista-turvallisuuspoikkeaman-lahetys
  (let [fake-vastaus [{:url +turi-url+ :method :post} {:status 200}]]
    (with-fake-http [{:url +turi-url+ :method :post} fake-vastaus]
      (turi/laheta-turvallisuuspoikkeama (:turi jarjestelma) 1)
      (let [tila (hae-turvallisuuspoikkeaman-tila 1)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkattu")
        (is (true? (:lahetys_onnistunut tila))) "Lähetysaika on merkattu"))))

