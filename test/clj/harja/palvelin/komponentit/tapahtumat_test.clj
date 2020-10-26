(ns harja.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [clojure.core.async :as async]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'harja-tarkkailija
                  (constantly
                    (assoc (component/start
                             (component/system-map
                               :db-event (event-tietokanta/luo-tietokanta testitietokanta)
                               :klusterin-tapahtumat (component/using
                                                       (tapahtumat/luo-tapahtumat)
                                                       {:db :db-event})))
                      :nimi "tarkkailija-a")))
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
                                               (println "viesti saatu:" viesti)))
            kantakatkos (katkos-testikantaan!)]
        (async/<!! kantakatkos)
        (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (thrown? AssertionError (odota-arvo saatiin 1000)) "Ei pitäisi saada tapahtumia kun kanta on alhaalla")
        (async/>!! kantakatkos :kaynnista)
        (async/<!! kantakatkos)
        (loop [[kerta & loput-kerrat] (range 0 50)
               testattu-kerran? false]
          (when-not (or (nil? kerta)
                        @tapahtumat/tapahtuma-loop-kaynnissa?)
            (when-not testattu-kerran?
              (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
              (is (thrown? AssertionError (odota-arvo saatiin 200)) "Ei pitäisi saada tapahtumia ennenkuin tapahtumaloop on käynnissä"))
            (async/<!! (async/timeout 100))
            (recur loput-kerrat
                   true)))
        (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (odota-arvo saatiin 500) "Pitäisi saada tapahtumia, kun tapahtumaloop on alkanut")
        (lopetus-fn)))))

(deftest possukanavien-luonti)

(deftest hashaus-onnistuu)

(deftest tarkkaile-kanavaa-testit-ok-tapaukset []
  (testing "Perus tarkkailun aloitus onnistuu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
          a-payload 42
          odota-tapahtuma (async/go (async/<! tarkkailija))]
      (is (not (false? tarkkailija)))
      (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
      (is (= (async/<!! odota-tapahtuma)
             {:palvelin (:nimi harja-tarkkailija)
              :payload a-payload}))))
  (testing "Julkaiseminen ilmoittaa kaikille tarkkailijoille")
  (testing "Viimeisin tarkkailu onnistuu"))

(deftest tarkkaile-kanavaa-testit-ei-ok-tapaukset []
  (testing "Väärä hash saatu")
  (testing "Väärän tyyppinen kuuntelija")
  (testing "Käsky tapahtuma-looppiin ilman funktiota"))

(deftest julkaisu-ilmoittaa-kaikkiin-jarjestelmiin [])

(deftest yhta-aikaa-julkaisu-toimii [])