(ns ^:integraatio harja.palvelin.palvelut.status-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [cheshire.core :as cheshire]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.integraatio :as integraatio]
            [harja.palvelin.komponentit.komponenttien-tila :as komponenttien-tila]
            [harja.palvelin.palvelut.status :as status]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [clojure.core.async :as async]))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea +kayttaja-jvh+
                                           :komponenttien-tila (komponenttien-tila/komponentin-tila {:sonja {:paivitystiheys-ms (:paivitystiheys-ms integraatio/sonja-asetukset)}
                                                                                                     :db {:paivitystiheys-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :paivitystiheys-ms])
                                                                                                          :kyselyn-timeout-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :kyselyn-timeout-ms])}
                                                                                                     :db-replica {:paivitystiheys-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :paivitystiheys-ms])
                                                                                                                  :replikoinnin-max-viive-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :kyselyn-timeout-ms])}})
                                           :status (component/using
                                                     (status/luo-status true)
                                                     [:http-palvelin :komponenttien-tila])
                                           :sonja (component/using
                                                    (sonja/luo-oikea-sonja integraatio/sonja-asetukset)
                                                    [:db])
                                           :tloik (component/using
                                                    (luo-tloik-komponentti)
                                                    [:db :sonja :integraatioloki])))

(use-fixtures :each (fn [testit]
                      (binding [*aloita-sonja?* true]
                        ;; Testatessa ei ole replicaa käytössä, niin sen tilaa ei voi oikein testata. Harjan tila nyt on muutenkin
                        ;; ok, jos testiin asti päästään
                        (with-redefs [status/harjan-tila-ok? (fn [& args] (async/go true))
                                      status/replikoinnin-tila-ok? (fn [& args] (async/go true))]
                          (jarjestelma-fixture testit)))))

(deftest toimiiko-dbn-testaus
  (testing "Kaikki hienosti"
    (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
             {:viesti ""
              :harja-ok? true
              :sonja-yhteys-ok? true
              :yhteys-master-kantaan-ok? true
              :replikoinnin-tila-ok? true}))
      (is (= (get vastaus :status) 200))))
  (testing "harja ei ole ok"
    (with-redefs [status/harjan-tila-ok? (fn [& args] (async/go false))]
      (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ portti)]
        (is (= (-> vastaus :body (cheshire/decode true))
               {:viesti (format "HOST: %s\nVIESTI: " tapahtuma-apurit/host-nimi)
                :harja-ok? false
                :sonja-yhteys-ok? true
                :yhteys-master-kantaan-ok? true
                :replikoinnin-tila-ok? true}))
        (is (= (get vastaus :status) 503)))))
  (testing "kanta ei ole ok"
    (with-redefs [status/dbn-tila-ok? (fn [& args] (async/go false))]
      (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ portti)]
        (is (= (-> vastaus :body (cheshire/decode true))
               {:viesti (str "Ei saatu yhteyttä kantaan 10 sekunnin kuluessa.")
                :harja-ok? true
                :sonja-yhteys-ok? true
                :yhteys-master-kantaan-ok? false
                :replikoinnin-tila-ok? true}))
        (is (= (get vastaus :status) 503)))))
  (testing "Kanta palautuu"
    (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
             {:viesti ""
              :harja-ok? true
              :sonja-yhteys-ok? true
              :yhteys-master-kantaan-ok? true
              :replikoinnin-tila-ok? true}))
      (is (= (get vastaus :status) 200)))))