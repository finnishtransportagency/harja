(ns harja.palvelin.integraatiot.api.urakan-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakat :as api-urakat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-urakat (component/using
                                                         (api-urakat/->Urakat)
                                                         [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest urakan-haku-idlla-toimii
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/" urakka] kayttaja portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (log/debug "Urakan haku id:llä: " encoodattu-body)
    (is (= 200 (:status vastaus)))
    (is (not (nil? (:urakka encoodattu-body))))
    (is (= (get-in encoodattu-body [:urakka :tiedot :id]) urakka))
    (is (>= (count (get-in encoodattu-body [:urakka :sopimukset])) 1))
    (is (not-empty (get-in encoodattu-body [:urakka :tiedot :alueurakkanumero])))

    (let [kokonaishintaiset (get-in (first (get-in encoodattu-body [:urakka :sopimukset]))
                                    [:sopimus :toteumakirjauskohteet :kokonaishintaiset])
          yksikkohintaiset (get-in (first (get-in encoodattu-body [:urakka :sopimukset]))
                                   [:sopimus :toteumakirjauskohteet :yksikkohintaiset])]
      (is (= 12 (count kokonaishintaiset)))
      (is (= 2 (count yksikkohintaiset))))))

(deftest urakan-haku-idlla-ei-toimi-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/" urakka] "Erkki Esimerkki" portti)]
    (is (= 403 (:status vastaus)))
    (is (.contains (:body vastaus) "Todennusvirhe"))))

(deftest urakan-haku-ytunnuksella-toimii
  (let [ytunnus "1565583-5"
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/" ytunnus] kayttaja portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (log/debug "Urakan haku ytunnuksella löytyi " (count (:urakat encoodattu-body)) " urakkaa: " (:body vastaus))
    (is (= 200 (:status vastaus)))
    (is (>= (count (:urakat encoodattu-body)) 2))
    (log/debug "Urakka: " (first (:urakat encoodattu-body)))
    (is (= (get-in (first (:urakat encoodattu-body)) [:urakka :tiedot :urakoitsija :ytunnus]) ytunnus))))

(deftest urakan-haku-ytunnuksella-ei-toimi-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/" "1565583-5"] "Erkki Esimerkki" portti)]
    (is (not (= 200 (:status vastaus))))))
