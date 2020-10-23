(ns harja.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]))

(def harja-tarkkailija nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'harja-tarkkailija
                  (constantly
                    (component/start
                      (component/system-map
                        :db-event (event-tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                {:db :db-event})))))
  (alter-var-root
   #'jarjestelma
   (fn [_]
     (component/start
      (component/system-map
       :db (tietokanta/luo-tietokanta testitietokanta)
       :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])))))

  (testit)
  (try (alter-var-root #'jarjestelma component/stop)
       (catch Exception e (println "saatiin poikkeus komponentin sammutuksessa: " e)))
  (try (alter-var-root #'harja-tarkkailija component/stop)
       (catch Exception e (println "saatiin poikkeus harja-tarkkailija systeemin sammutuksessa: " e))))

(use-fixtures :once jarjestelma-fixture)

(deftest julkaisu-ja-kuuntelu []
  (testing "tapahtumat-komponentti on luotu onnistuneesti"
    (is (some? (:klusterin-tapahtumat harja-tarkkailija))))
  (let [saatiin (atom nil)]
    (testing "Perustapaus"
      (let [lopetus-fn (tapahtumat/kuuntele! (:klusterin-tapahtumat harja-tarkkailija) "seppo"
                                             (fn kuuntele-callback [viesti] (reset! saatiin true)
                                               (println "viesti saatu:" viesti)))
            kuuntelija-funktiot-kuuntelun-jalkeen @(get-in harja-tarkkailija [:klusterin-tapahtumat :kuuntelijat])]
        (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (odota-arvo saatiin))
        (lopetus-fn)
        (is (= 1 (count kuuntelija-funktiot-kuuntelun-jalkeen)) "Yhtä tapahtumaa kuunnellaan")
        (is (= 1 (count (-> kuuntelija-funktiot-kuuntelun-jalkeen first val))) "Yhdellä tapahtumalla on yksi kuuntelija")
        (is (= 0 (count (-> harja-tarkkailija (get-in [:klusterin-tapahtumat :kuuntelijat]) deref first val)))
            "Tapahtumalla ei ole enää kuuntelijaa, kun tapahtuman kuuntelu on lopetettu")))
    (testing "Toipuminen kantayhteyden katkosta"
      (reset! saatiin nil)
      (let [lopetus-fn (tapahtumat/kuuntele! (:klusterin-tapahtumat harja-tarkkailija) "seppo"
                                             (fn kuuntele-callback [viesti] (reset! saatiin true)
                                               (println "viesti saatu:" viesti)))]
        (tapa-kanta "harjatest")
        (dotimes [_ 5]
          (try
            (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
            (catch Exception e
              (Thread/sleep 500))))
        (is (odota-arvo saatiin))
        (lopetus-fn)))))
