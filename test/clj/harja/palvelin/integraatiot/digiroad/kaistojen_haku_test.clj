(ns harja.palvelin.integraatiot.digiroad.kaistojen-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad])
  (:use [slingshot.slingshot :only [try+]]))

(def +digiroad-url+ "http://localhost:1234/")

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :digiroad (component/using
           (digiroad/->Digiroad {:url +digiroad-url+})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(def kaistojen-haku-url (str +digiroad-url+ "lanes/lanes_in_range"))

;; TODO:
(deftest tarkista-kaistojen-haku
  (let [odotettu-vastaus "placeholder-vastaus"
        url kaistojen-haku-url
        tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
        ajokaista 1]
    (with-fake-http [url "placeholder-vastaus"]
      (let [vastaus (digiroad/hae-kaistat (:digiroad jarjestelma) tr-osoite ajokaista)]
        (is (= odotettu-vastaus vastaus))))))

#_(deftest tarkista-epaonnistunut-kutsu
  (with-fake-http [{:url kaistojen-haku-url :method :get} 500]
    (let [tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
          ajokaista 1]
      (is (thrown? Exception (digiroad/hae-kaistat (:digiroad jarjestelma) tr-osoite ajokaista))
        "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))))

#_(deftest tarkista-virhevastaus
  (let [url kaistojen-haku-url]
    (with-fake-http [url "virhevastaus"]
      (let [tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
            ajokaista 1]
        (try+
          (digiroad/hae-kaistat (:digiroad jarjestelma) tr-osoite ajokaista)
          (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
          (catch [:type digiroad/+virhe-kaistojen-haussa+] {:keys [virheet]}
            (is true "Poikkeus heitettiin epäonnistuneesta kutsusta.")))))))
