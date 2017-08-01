(ns harja.palvelin.integraatiot.api.urakan-tyotunnit-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(def kayttaja "yit-rakennus")
(def +testi-turi-url+ "harja.testi.turi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :turi (component/using
            (turi/->Turi {:urakan-tyotunnit-url +testi-turi-url+})
            [:db :integraatioloki :liitteiden-hallinta])
    :api-varusteet (component/using
                     (urakan-tyotunnit/->UrakanTyotunnit)
                     [:http-palvelin :db :integraatioloki :turi])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest tarkista-tietueen-lisaaminen
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        kutsu (str "/api/urakat/" urakka "/tyotunnit")
        kutsu-data (slurp (io/resource "api/examples/urakan-tyotuntien-kirjaus-request.json"))
        turikutsujen-maarat (fn []
                              (ffirst (q "SELECT count(*)
                                          FROM integraatiotapahtuma
                                          WHERE integraatio = (SELECT id
                                                               FROM integraatio
                                                               WHERE jarjestelma = 'turi' AND
                                                               nimi = 'urakan-tyotunnit') AND
                                           onnistunut;")))
        lahetetyt-tunnit (fn []
                           (ffirst (q (str "SELECT count(*)
                                           FROM urakan_tyotunnit
                                           WHERE urakka = " urakka "AND
                                           lahetys_onnistunut;"))))
        turi-kutsuja (turikutsujen-maarat)]
    (with-fake-http [+testi-turi-url+ (fn [_ _ _]
                                        {:status 200 :body "ok"})
                     (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/post-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
        (is (.contains (:body vastaus) "Työtunnit kirjattu onnistuneesti"))
        (odota-ehdon-tayttymista #(and (= (+ 3 turi-kutsuja) (turikutsujen-maarat))
                                       (= 3 (lahetetyt-tunnit)))
                                 "3 kutsua tehtiin TURI:n"
                                 10000)))))
