(ns harja.palvelin.integraatiot.turi.turi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [harja.kyselyt.urakan-tyotunnit :as urakan-tyotunnit]))

(def kayttaja "jvh")
(def +turvallisuuspoikkeama-url+ "http://localhost:1234/turvallisuuspoikkeama")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :turi (component/using
            (turi/->Turi {:turvallisuuspoikkeamat-url +turvallisuuspoikkeama-url+
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

(deftest tyotuntitietokantahaku
  (let [tyotunnit (urakan-tyotunnit/hae-lahettamattomat-tai-epaonnistuneet-tyotunnit (:db jarjestelma))
        oletustyotunnit {:harja.domain.urakan-tyotunnit/tyotunnit 666,
                         :harja.domain.urakan-tyotunnit/id 1,
                         :harja.domain.urakan-tyotunnit/vuosi 2017,
                         :harja.domain.urakan-tyotunnit/vuosikolmannes 1,
                         :harja.domain.urakan-tyotunnit/urakka-id 4}]
    (is (= oletustyotunnit (first tyotunnit)))))
