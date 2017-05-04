(ns harja.palvelin.integraatiot.turi.turi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(def kayttaja "jvh")
(def +turvallisuuspoikkeama-url+ "http://localhost:1234/turvallisuuspoikkeama")
(def +tyotunnit-url+ "http://localhost:1234/tyotunnit")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :turi (component/using
            (turi/->Turi {:turvallisuuspoikkeamat-url +turvallisuuspoikkeama-url+
                          :urakan-tyotunnit-url +tyotunnit-url+
                          :kayttajatunnus "kayttajatunnus"
                          :salasana "salasana"})
            [:db :http-palvelin :integraatioloki :liitteiden-hallinta])))

(use-fixtures :each jarjestelma-fixture)

(defn hae-turvallisuuspoikkeaman-tila [id]
  (let [tila (first (q (format "select lahetetty, lahetys_onnistunut from turvallisuuspoikkeama where id = %s" id)))]
    {:lahetetty (first tila)
     :lahetys_onnistunut (second tila)}))

(defn hae-turi-id [id]
  (ffirst (q (format "select turi_id from turvallisuuspoikkeama where id = %s" id))))

(defn tyhjenna-turvallisuuspoikkeaman-lahetystiedot [id]
  (u (format "update turvallisuuspoikkeama set lahetetty = null, lahetys_onnistunut = null where id = %s" id)))

(defn tyhjenna-tyotuntien-lahetystiedot [urakka-id vuosi kolmannes]
  (u (format "update urakan_tyotunnit set lahetetty = null, lahetys_onnistunut = null where urakka = %s and vuosi = %s and vuosikolmannes = %s"
             urakka-id vuosi kolmannes)))

(defn hae-tyotuntien-tila [urakka-id vuosi kolmannes]
  (let [tila (first (q (format "select lahetetty, lahetys_onnistunut from urakan_tyotunnit where urakka = %s and vuosi = %s and vuosikolmannes = %s"
                               urakka-id vuosi kolmannes)))]
    {:lahetetty (first tila)
     :lahetys_onnistunut (second tila)}))

(deftest turvallisuuspoikkeaman-lahetys
  (let [turpo-id 1]
    (with-fake-http [{:url +turvallisuuspoikkeama-url+ :method :post}
                     (fn [_ opts _]
                       (is (= +turvallisuuspoikkeama-url+ (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
                       (is (= (first (:basic-auth opts)) "kayttajatunnus") "Autentikaatiossa käytetään oikeaa käyttäjätunnusta")
                       (is (= (second (:basic-auth opts)) "salasana") "Autentikaatiossa käytetään oikeaa salasanaa")
                       {:status 200 :body "id: 666\n"})]
      (turi/laheta-turvallisuuspoikkeama (:turi jarjestelma) turpo-id)
      (let [tila (hae-turvallisuuspoikkeaman-tila turpo-id)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut tila))) "Lähetys on merkitty onnistuneeksi")
      (is (= 666 (hae-turi-id turpo-id)))
      (tyhjenna-turvallisuuspoikkeaman-lahetystiedot turpo-id))))

(deftest turvallisuuspoikkeaman-epaonnistunut-lahetys
  (let [turpo-id 1]
    (with-fake-http [{:url +turvallisuuspoikkeama-url+ :method :post} 500]
      (turi/laheta-turvallisuuspoikkeama (:turi jarjestelma) turpo-id)
      (let [tila (hae-turvallisuuspoikkeaman-tila turpo-id)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkitty")
        (is (false? (:lahetys_onnistunut tila)) "Lähetys on merkitty epäonnistuneeksi")
        (tyhjenna-turvallisuuspoikkeaman-lahetystiedot turpo-id)))))

(deftest tyotuntien-lahetys
  (with-fake-http [{:url +tyotunnit-url+ :method :post}
                   (fn [_ opts _]
                     (is (= +tyotunnit-url+ (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
                     (is (= (first (:basic-auth opts)) "kayttajatunnus") "Autentikaatiossa käytetään oikeaa käyttäjätunnusta")
                     (is (= (second (:basic-auth opts)) "salasana") "Autentikaatiossa käytetään oikeaa salasanaa")
                     {:status 200})]
    (let [urakka-id (ffirst (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019'"))
          vuosi 2017
          kolmannes 1]

      (turi/laheta-urakan-vuosikolmanneksen-tyotunnit-turiin (:turi jarjestelma) urakka-id vuosi kolmannes)
      (let [tila (hae-tyotuntien-tila urakka-id vuosi kolmannes)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut tila))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-tyotuntien-lahetystiedot urakka-id vuosi kolmannes))))

(deftest tyotuntien-epaonnistunut-lahetys
  (let [urakka-id (ffirst (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019'"))
        vuosi 2017
        kolmannes 1]
    (with-fake-http [{:url +turvallisuuspoikkeama-url+ :method :post} 500]
      (turi/laheta-urakan-vuosikolmanneksen-tyotunnit-turiin (:turi jarjestelma) urakka-id vuosi kolmannes)
      (let [tila (hae-tyotuntien-tila urakka-id vuosi kolmannes)]
        (is (not (nil? (:lahetetty tila))) "Lähetysaika on merkitty")
        (is (false? (:lahetys_onnistunut tila)) "Lähetys on merkitty epäonnistuneeksi")
        (tyhjenna-tyotuntien-lahetystiedot urakka-id vuosi kolmannes)))))
