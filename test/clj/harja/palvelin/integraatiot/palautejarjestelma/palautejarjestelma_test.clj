(ns harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
            [harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-komponentti :as pj]
            [harja.domain.palautejarjestelma-domain :as domain]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]))

(def kayttaja "jvh")

(def +testi-pj+
  {:paivitysaika nil
   :url "https://feikki-palautejarjestelma-api.com"
   :kayttajatunnus "pj-testi-kayttajatunnus"
   :salasana "pj-testi-salasana"})

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :palautejarjestelma (component/using
                          (pj/->Palautejarjestelma +testi-pj+)
                          [:db :integraatioloki])
    :ilmoitukset (component/using
                   (ilmoitukset/->Ilmoitukset)
                   [:db :integraatioloki :http-palvelin])))


(use-fixtures :once (compose-fixtures tietokanta-fixture
                      jarjestelma-fixture))

(def odotetut-aiheet
  [{::domain/aihe-id 1
    ::domain/nimi "Aihe 1"
    ::domain/kaytossa? true
    ::domain/jarjestys 100}
   {::domain/aihe-id 2
    ::domain/nimi "Aihe 2"
    ::domain/kaytossa? true
    ::domain/jarjestys 200}
   {::domain/aihe-id 3
    ::domain/nimi "Aihe 3, ei käytössä"
    ::domain/kaytossa? false
    ::domain/jarjestys 300}])

(def odotetut-tarkenteet
  [{::domain/tarkenne-id 101
    ::domain/nimi "Tarkenne 1-1"
    ::domain/aihe-id 1
    ::domain/kaytossa? true
    ::domain/jarjestys 100}
   {::domain/tarkenne-id 102
    ::domain/nimi "Tarkenne 1-2"
    ::domain/aihe-id 1
    ::domain/kaytossa? true
    ::domain/jarjestys 200}
   {::domain/tarkenne-id 201
    ::domain/nimi "Tarkenne 2-1"
    ::domain/aihe-id 2
    ::domain/kaytossa? true
    ::domain/jarjestys 300}
   {::domain/tarkenne-id 202
    ::domain/nimi "Tarkenne 2-2"
    ::domain/aihe-id 2
    ::domain/kaytossa? true
    ::domain/jarjestys 400}
   {::domain/tarkenne-id 203
    ::domain/nimi "Tarkenne 2-3, ei käytössä"
    ::domain/aihe-id 2
    ::domain/kaytossa? false
    ::domain/jarjestys 500}
   {::domain/tarkenne-id 301
    ::domain/nimi "Tarkenne 3-1"
    ::domain/aihe-id 3
    ::domain/kaytossa? true
    ::domain/jarjestys 600}])

(def odotetut-aiheet-ja-tarkenteet-kannasta
  [{:aihe-id 1
    :nimi "Aihe 1"
    :kaytossa? true
    :jarjestys 100
    :tarkenteet [{:tarkenne-id 101
                  :nimi "Tarkenne 1-1"
                  :aihe-id 1
                  :kaytossa? true
                  :jarjestys 100}
                 {:tarkenne-id 102
                  :nimi "Tarkenne 1-2"
                  :aihe-id 1
                  :kaytossa? true
                  :jarjestys 200}]}
   {:aihe-id 2
    :nimi "Aihe 2"
    :kaytossa? true
    :jarjestys 200
    :tarkenteet [{:tarkenne-id 201
                  :nimi "Tarkenne 2-1"
                  :aihe-id 2
                  :kaytossa? true
                  :jarjestys 300}
                 {:tarkenne-id 202
                  :nimi "Tarkenne 2-2"
                  :aihe-id 2
                  :kaytossa? true
                  :jarjestys 400}]}])

(deftest hae-aiheet-onnistuu
  (with-fake-http
    ["https://feikki-palautejarjestelma-api.com/api/x_sgtk_open311/v1/publicws/subjects?locale=fi"
     (slurp "resources/xsd/palautejarjestelma/esimerkit/aiheet.xml")]
    (let [aiheet (pj/hae-aiheet (:palautejarjestelma jarjestelma))]
      (is (= (count aiheet) 3))
      (is (= odotetut-aiheet aiheet)))))

(deftest hae-tarkenteet-onnistuu
  (with-fake-http
    ["https://feikki-palautejarjestelma-api.com/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi"
     (slurp "resources/xsd/palautejarjestelma/esimerkit/tarkenteet.xml")]
    (let [tarkenteet (pj/hae-tarkenteet (:palautejarjestelma jarjestelma))]
      (is (= (count tarkenteet) 6))
      (is (= odotetut-tarkenteet tarkenteet)))))

(deftest tallenna-aiheet-ja-tarkenteet-onnistuu
  (with-fake-http
    ["https://feikki-palautejarjestelma-api.com/api/x_sgtk_open311/v1/publicws/subjects?locale=fi"
     (slurp "resources/xsd/palautejarjestelma/esimerkit/aiheet.xml")
     "https://feikki-palautejarjestelma-api.com/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi"
     (slurp "resources/xsd/palautejarjestelma/esimerkit/tarkenteet.xml")]
    (let [_aiheet (pj/paivita-aiheet-ja-tarkenteet (:palautejarjestelma jarjestelma))
          aiheet-ja-tarkenteet-kannassa (ilmoitukset/hae-ilmoitusten-aiheet-ja-tarkenteet
                                          (:db jarjestelma) +kayttaja-jvh+)]
      (is (= odotetut-aiheet-ja-tarkenteet-kannasta aiheet-ja-tarkenteet-kannassa)))))