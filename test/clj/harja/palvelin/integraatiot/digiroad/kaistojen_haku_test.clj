(ns harja.palvelin.integraatiot.digiroad.kaistojen-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad-integraatio]
            [harja.palvelin.integraatiot.digiroad.tyokalut :as tyokalut]
            [slingshot.slingshot :refer [try+]]
            [cheshire.core :as cheshire]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :digiroad-integraatio
    (component/using
      (digiroad-integraatio/->Digiroad {:url tyokalut/+digiroad-url+})
      [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-kaistojen-haku
  (let [odotettu-vastaus (cheshire/decode tyokalut/+onnistunut-digiroad-kaistojen-hakuvastaus+ true)
        url tyokalut/+kaistojen-haku-url+
        tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
        ajorata 1]
    (with-fake-http [url tyokalut/+onnistunut-digiroad-kaistojen-hakuvastaus+]
      (let [vastaus (digiroad-integraatio/hae-kaistat (:digiroad-integraatio jarjestelma) tr-osoite ajorata)]
        (is (= odotettu-vastaus vastaus))))))

#_(deftest tarkista-epaonnistunut-kutsu
  (with-fake-http [{:url kaistojen-haku-url :method :get} 500]
    (let [tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
          ajorata 1]
      (is (thrown? Exception (digiroad-integraatio/hae-kaistat (:digiroad-integraatio jarjestelma) tr-osoite ajorata))
        "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))))

#_(deftest tarkista-virhevastaus
  (let [url kaistojen-haku-url]
    (with-fake-http [url "virhevastaus"]
      (let [tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
            ajorata 1]
        (try+
          (digiroad-integraatio/hae-kaistat (:digiroad-integraatio jarjestelma) tr-osoite ajorata)
          (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
          (catch [:type digiroad-integraatio/+virhe-kaistojen-haussa+] {:keys [virheet]}
            (is true "Poikkeus heitettiin epäonnistuneesta kutsusta.")))))))
