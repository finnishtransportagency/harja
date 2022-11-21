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
            [harja.palvelin.ajastetut-tehtavat.harja-status :as harja-status]
            [harja.palvelin.palvelut.status :as status]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tyokalut]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.itmf :as itmf]))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea +kayttaja-jvh+
                                           :komponenttien-tila (komponenttien-tila/komponentin-tila {:sonja {:paivitystiheys-ms (:paivitystiheys-ms integraatio/sonja-asetukset)}
                                                                                                     :itmf {:paivitystiheys-ms (:paivitystiheys-ms integraatio/itmf-asetukset)}
                                                                                                     :db {:paivitystiheys-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :paivitystiheys-ms])
                                                                                                          :kyselyn-timeout-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :kyselyn-timeout-ms])}
                                                                                                     :db-replica {:paivitystiheys-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :paivitystiheys-ms])
                                                                                                                  :replikoinnin-max-viive-ms (get-in testitietokanta [:tarkkailun-timeout-arvot :kyselyn-timeout-ms])}})
                                           :status (component/using
                                                     (status/luo-status true)
                                                     [:http-palvelin :db :komponenttien-tila])
                                           :sonja (component/using
                                                    (sonja/luo-oikea-sonja integraatio/sonja-asetukset)
                                                    [:db])
                                           :itmf (component/using
                                                   (itmf/luo-oikea-itmf integraatio/itmf-asetukset)
                                                   [:db])
                                           :tloik (component/using
                                                    (luo-tloik-komponentti)
                                                    [:db :itmf :integraatioloki])))

(use-fixtures :each (fn [testit]
                      (binding [*aloitettavat-jmst* #{"sonja"}
                                *kaynnistyksen-jalkeen-hook*
                                (fn []
                                  (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
                                         (fn [tila]
                                           (-> tila
                                               (assoc-in [tapahtuma-apurit/host-nimi :harja :kaikki-ok?] true)
                                               (assoc-in [tapahtuma-apurit/host-nimi :db :kaikki-ok?] true)
                                               (assoc-in [tapahtuma-apurit/host-nimi :db-replica :kaikki-ok?] true)
                                               (assoc-in [tapahtuma-apurit/host-nimi :sonja :kaikki-ok?] true)
                                               (assoc-in [tapahtuma-apurit/host-nimi :itmf :kaikki-ok?] true)
                                               (assoc-in ["testihost" :harja :kaikki-ok?] true)
                                               (assoc-in ["testihost" :harja :viesti] "kaik kivast")
                                               (assoc-in ["testihost" :db :kaikki-ok?] true)
                                               (assoc-in ["testihost" :db-replica :kaikki-ok?] true)
                                               (assoc-in ["testihost" :sonja :kaikki-ok?] true)
                                               (assoc-in ["testihost" :itmf :kaikki-ok?] true)))))
                                *ennen-sulkemista-hook* (fn []
                                                          (reset! (-> jarjestelma :komponenttien-tila :komponenttien-tila) nil))]
                        ;; Testatessa ei ole replicaa käytössä, niin sen tilaa ei voi oikein testata. Harjan tila nyt on muutenkin
                        ;; ok, jos testiin asti päästään
                        (with-redefs [status/harjan-tila-ok? (fn [& args] (async/go true))
                                      status/replikoinnin-tila-ok? (fn [& args] (async/go true))]
                          (jarjestelma-fixture testit)))))

(deftest toimiiko-dbn-testaus
  (testing "Kaikki hienosti"
    (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
             {:viesti ""
              :harja-ok? true
              :sonja-yhteys-ok? true
              :itmf-yhteys-ok? true
              :yhteys-master-kantaan-ok? true
              :replikoinnin-tila-ok? true}))
      (is (= (get vastaus :status) 200))))
  (testing "harja ei ole ok"
    (with-redefs [status/harjan-tila-ok? (fn [& _] (async/go false))]
      (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
                      (fn [tila]
                        (-> tila
                            (assoc-in [tapahtuma-apurit/host-nimi :harja :kaikki-ok?] false)
                            (assoc-in [tapahtuma-apurit/host-nimi :harja :viesti] "poks")))))
      (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
        (is (= (-> vastaus :body (cheshire/decode true))
               {:viesti (format "HOST: %s\nVIESTI: poks\nHOST: testihost\nVIESTI: kaik kivast" tapahtuma-apurit/host-nimi)
                :harja-ok? false
                :sonja-yhteys-ok? true
                :itmf-yhteys-ok? true
                :yhteys-master-kantaan-ok? true
                :replikoinnin-tila-ok? true}))
        (is (= (get vastaus :status) 503)))
      (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
                      assoc-in
                      [tapahtuma-apurit/host-nimi :harja :kaikki-ok?]
                      true))))
  (testing "kanta ei ole ok"
    (with-redefs [status/dbn-tila-ok? (fn [& _] (async/go false))]
      (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
        (is (= (-> vastaus :body (cheshire/decode true))
               {:viesti (str "Ei saatu yhteyttä kantaan 50 sekunnin kuluessa.")
                :harja-ok? true
                :sonja-yhteys-ok? true
                :itmf-yhteys-ok? true
                :yhteys-master-kantaan-ok? false
                :replikoinnin-tila-ok? true}))
        (is (= (get vastaus :status) 503)))))
  (testing "Kanta palautuu"
    (let [vastaus (tyokalut/get-kutsu ["/status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
             {:viesti ""
              :harja-ok? true
              :sonja-yhteys-ok? true
              :itmf-yhteys-ok? true
              :yhteys-master-kantaan-ok? true
              :replikoinnin-tila-ok? true}))
      (is (= (get vastaus :status) 200)))))


(deftest toimiiko-app-status
  (testing "Kaikki hienosti"
    (let [vastaus (tyokalut/get-kutsu ["/app_status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
            {:viesti "Harja ok"}))
      (is (= (get vastaus :status) 200))))
  (testing "harja ei ole ok"
    (with-redefs [status/harjan-tila-ok? (fn [& _] (async/go false))]
      (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
                 (fn [tila]
                   (-> tila
                     (assoc-in [tapahtuma-apurit/host-nimi :harja :kaikki-ok?] false)
                     (assoc-in [tapahtuma-apurit/host-nimi :harja :viesti] "poks")))))
      (let [vastaus (tyokalut/get-kutsu ["/app_status"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
        (is (= (-> vastaus :body (cheshire/decode true))
              {:viesti (format "HOST: %s\nVIESTI: poks\nHOST: testihost\nVIESTI: kaik kivast" tapahtuma-apurit/host-nimi)}))
        (is (= (get vastaus :status) 503)))
      (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
                 assoc-in
                 [tapahtuma-apurit/host-nimi :harja :kaikki-ok?]
                 true)))))


(deftest toimiiko-app-status-local
  (testing "Kaikki hienosti"
    (let [vastaus (tyokalut/get-kutsu ["/app_status_local"] +kayttaja-jvh+ nil {:timeout 1000} portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
            {:viesti "Harja ok"}))
      (is (= (get vastaus :status) 200))))
  ;; Testi poistettu käytöstä, koska rajapinnan sisäinen timeout failure-responsen lähettämiselle on liian korkea (10 sec)
  #_(testing "harja ei ole ok"
    (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
               (fn [tila]
                 (-> tila
                   (assoc-in [tapahtuma-apurit/host-nimi :harja :kaikki-ok?] false)))))
    (let [vastaus (tyokalut/get-kutsu ["/app_status_local"] +kayttaja-jvh+ nil {:timeout 20000} portti)]
      (is (= (-> vastaus :body (cheshire/decode true))
            {:viesti ""}))
      (is (= (get vastaus :status) 503)))
    (println (swap! (-> jarjestelma :komponenttien-tila :komponenttien-tila)
               assoc-in
               [tapahtuma-apurit/host-nimi :harja :kaikki-ok?]
               true))))

(def tloik-asetukset (assoc {} :tloik {:ilmoitusviestijono tloik-tyokalut/+tloik-ilmoitusviestijono+
                                       :ilmoituskuittausjono tloik-tyokalut/+tloik-ilmoituskuittausjono+
                                       :toimenpidejono tloik-tyokalut/+tloik-ilmoitustoimenpideviestijono+
                                       :toimenpidekuittausjono tloik-tyokalut/+tloik-ilmoitustoimenpidekuittausjono+
                                       :toimenpideviestijono tloik-tyokalut/+tloik-toimenpideviestijono+}))
(deftest uusi-status-toimii
  (testing "Uusi statuskysely toimii"
    (let [;; Lisää sonja tila ok:ksi tietokantaan
          _ (u (str "INSERT INTO jarjestelman_tila (palvelin, tila, \"osa-alue\", paivitetty) VALUES
          ('test-palvelin', '{\"istunnot\": [], \"yhteyden-tila\": \"ACTIVE\"}', 'sonja', NOW());"))
          _ (harja-status/tarkista-harja-status (:db jarjestelma) (:itmf jarjestelma) tloik-asetukset true)
          vastaus (tyokalut/get-kutsu ["/uusi-status"] +kayttaja-jvh+ portti)
          body (-> vastaus
                 :body
                 (cheshire/decode true)
                 (dissoc :viesti))]
      (is (= body
            {:harja-ok? true
             :itmf-yhteys-ok? true
             :replikoinnin-tila-ok? true
             :sonja-yhteys-ok? true
             :yhteys-master-kantaan-ok? true}))
      (is (= (get vastaus :status) 200)))))

(deftest uusi-status-vastaa-virhetta
  (testing "Uusi statuskysely palauttaa virhettä"
    (let [_ (harja-status/tarkista-harja-status (:db jarjestelma) (:itmf jarjestelma) tloik-asetukset false)
          vastaus (tyokalut/get-kutsu ["/uusi-status"] +kayttaja-jvh+ portti)
          body (-> vastaus
                 :body
                 (cheshire/decode true)
                 (dissoc :viesti))
          sorted-body (into (sorted-map) body)]
      (is (= sorted-body
            {:harja-ok? false,
             :itmf-yhteys-ok? true,
             :sonja-yhteys-ok? false,
             :replikoinnin-tila-ok? true,
             :yhteys-master-kantaan-ok? true,}))
      (is (= (get vastaus :status) 503)))))
