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

(defn tyhjenna-turvallisuuspoikkeaman-lahetystiedot [id]
  (u (format "update turvallisuuspoikkeama set lahetetty = null, lahetys_onnistunut = null where id = %s" id)))

(deftest tarkista-turvallisuuspoikkeaman-lahetys
  (let [turpo-id 1
        fake-vastaus [{:url +turi-url+ :method :post} {:status 200}]]
    (with-fake-http [{:url +turi-url+ :method :post}
                     (fn [a b c]
                       ;; todo: tutki body ja katso, että se on oikeanlaista xml:ää
                       (println "----a: " a)
                       (println "----b: " b)
                       (println "----c: " c)
                       fake-vastaus)]
      (turi/laheta-turvallisuuspoikkeama (:turi jarjestelma) turpo-id)
      (let [tila (hae-turvallisuuspoikkeaman-tila turpo-id)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut tila))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-turvallisuuspoikkeaman-lahetystiedot turpo-id))))

(deftest tarkista-turvallisuuspoikkeaman-epaonnistunut-lahetys
  (let [turpo-id 1
        fake-vastaus [{:url +turi-url+ :method :post} {:status 500}]]
    (with-fake-http [{:url +turi-url+ :method :post} fake-vastaus]
      (turi/laheta-turvallisuuspoikkeama (:turi jarjestelma) turpo-id)
      (let [tila (hae-turvallisuuspoikkeaman-tila turpo-id)]
        (is (nil? (:lahetetty tila)) "Lähetysaikaa ei ole merkattu")
        (is (nil? (:lahetys_onnistunut tila)) "Lähetystä ei ole merkitty onnistuneeksi")
        (tyhjenna-turvallisuuspoikkeaman-lahetystiedot turpo-id)))))

