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
            [harja.kyselyt.konversio :as konversio]
            [cheshire.core :as cheshire]))

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
  (with-fake-http [tyokalut/+kaistojen-haku-url+ tyokalut/+onnistunut-kaistojen-hakuvastaus+]
    (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-kaistat-digiroadista +kayttaja-jvh+
                    {:tr-osoite {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
                     :ajorata 1})]
      (is (= vastaus
            tyokalut/+onnistunut-kaistojen-hakuvastaus+)))))
