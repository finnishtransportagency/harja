(ns harja.palvelin.palvelut.digiroad-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.digiroad :as digiroad]
            [harja.palvelin.integraatiot.digiroad.digiroad-komponentti :as digiroad-integraatio]
            [harja.palvelin.integraatiot.digiroad.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [org.httpkit.fake :refer [with-fake-http]]
            [cheshire.core :as cheshire]))

(def +onnistunut-kaistojen-hakuvastaus+
  [{:aet 0
    :ajorata 1
    :kaista 12
    :let 170
    :osa 101
    :tie 4
    :tyyppi 2}
   {:aet 0
    :ajorata 1
    :kaista 11
    :let 170
    :osa 101
    :tie 4
    :tyyppi 1}])

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using
                             (integraatioloki/->Integraatioloki nil) [:db])
          :digiroad-integraatio (component/using
                                  (digiroad-integraatio/->Digiroad {:url tyokalut/+digiroad-url+})
                                  [:db :integraatioloki])
          :digiroad (component/using
                      (digiroad/->Digiroad)
                      [:http-palvelin :db :digiroad-integraatio])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest hae-kaistat
  (let [odotettu-vastaus +onnistunut-kaistojen-hakuvastaus+]
    (with-fake-http [tyokalut/+kaistojen-haku-url+ tyokalut/+onnistunut-digiroad-kaistojen-hakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :hae-kaistat-digiroadista +kayttaja-jvh+
                      {:tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
                       :ajorata 1
                       :urakka-id (hae-kemin-paallystysurakan-2019-2023-id)})]
        (is (= vastaus odotettu-vastaus))))))

(deftest hae-kaistat-ei-oikeutta
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-kaistat-digiroadista +kayttaja-seppo+
                           {:tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
                            :ajorata 1
                            :urakka-id (hae-kemin-paallystysurakan-2019-2023-id)}))
    "Poikkeusta ei heitetty! Sepolla olikin oikeus hakea kaistat."))
