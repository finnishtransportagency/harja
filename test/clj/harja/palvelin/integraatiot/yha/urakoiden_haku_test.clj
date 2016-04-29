(ns harja.palvelin.integraatiot.yha.urakoiden-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")
(def +yha-url+ "http://localhost:1234")
(def +onnistunut-hakuvastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat>
      <yha:urakka>
       <yha:yha-id>3</yha:yha-id>
       <yha:elyt>
         <yha:ely>POP</yha:ely>
       </yha:elyt>
       <yha:vuodet>
         <yha:vuosi>2016</yha:vuosi>
       </yha:vuodet>
       <yha:sampotunnus>SAMPOTUNNUS</yha:sampotunnus>
       <yha:tunnus>YHATUNNUS</yha:tunnus>
      </yha:urakka>
    </yha:urakat>
  </yha:urakoiden-hakuvastaus>")
(def +virhevastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat/>
    <yha:virhe>Tapahtui virhe</yha:virhe>
  </yha:urakoiden-hakuvastaus>")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha (component/using
           (yha/->Yha {:yha {:url +yha-url+}})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)


(deftest tarkista-urakoiden-haku
  (let [odotettu-vastaus {:urakat [{:yha-id 3,
                                    :elyt ["POP"],
                                    :tunnus "YHATUNNUS",
                                    :vuodet [2016],
                                    :sampotunnus "SAMPOTUNNUS"}]}
        url (str +yha-url+ "/urakkahaku")]
    (with-fake-http [url +onnistunut-hakuvastaus+]
      (let [vastaus (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016)]
        (println vastaus)
        (is (= odotettu-vastaus vastaus))))))

(deftest tarkista-epaonnistunut-kutsu
  (with-fake-http [{:url (str +yha-url+ "/urakkahaku") :method :get} 500]
    (is (thrown? Exception (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016))
        "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")))

(deftest tarkista-virhevastaus
  (let [url (str +yha-url+ "/urakkahaku")]
    (with-fake-http [url +virhevastaus+]
      (try+
        (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016)
        (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
        (catch [:type yha/+virhe-urakoiden-haussa+] {:keys [virheet]}
          (is true "Poikkeus heitettiin epäonnistuneesta kutsusta."))))))