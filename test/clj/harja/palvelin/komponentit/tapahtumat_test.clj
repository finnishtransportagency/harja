(ns harja.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.kyselyt.konversio :as konv]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [clojure.core.async :as async]
            [taoensso.timbre :as log])
  (:import (java.util.concurrent TimeoutException)
           (clojure.lang ArityException)))

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

(defn <!!-timeout [kanava timeout]
  (let [[arvo valmistunut-kanava] (async/alts!! [kanava
                                                 (async/timeout timeout)])]
    (if (not= valmistunut-kanava kanava)
      (throw (TimeoutException. (str "Ei saatu arvoa ajassa " timeout)))
      arvo)))

(deftest julkaisu-ja-kuuntelu
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
        (<!!-timeout kantakatkos 5000)
        (tapahtumat/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (thrown? AssertionError (odota-arvo saatiin 1000)) "Ei pitäisi saada tapahtumia kun kanta on alhaalla")
        (async/>!! kantakatkos :kaynnista)
        (<!!-timeout kantakatkos 5000)
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

(deftest tarkkaile-kanavaa-testit-ok-tapaukset
  (testing "Perus tarkkailun aloitus onnistuu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
      (let [tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
            a-payload 42
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (is (not (false? tarkkailija)))
        (is (thrown? TimeoutException (<!!-timeout odota-tapahtuma 500))
            "Kanavassa ei pitäisi olla vielä mitään")
        (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (<!!-timeout odota-tapahtuma 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty"))
      (let [tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
            a-payload 1337
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (<!!-timeout odota-tapahtuma 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan ei pitäisi tulla kakutettua kamaa vaan juurikin se mikä on lähetetty"))))
  (testing "Julkaiseminen ilmoittaa kaikille tarkkailijoille"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          kuuntelu-ja-tarkkailu-lkm (atom 0)
          tarkkailija-1 (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
          tarkkailija-2 (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
          tarkkailija-3 (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
          kuuntelija-1 (tapahtumat/kuuntele! tapahtumat-k :tapahtuma-a (fn [_] (swap! kuuntelu-ja-tarkkailu-lkm inc)))
          kuuntelija-2 (tapahtumat/kuuntele! tapahtumat-k :tapahtuma-a (fn [_] (swap! kuuntelu-ja-tarkkailu-lkm inc)))
          a-payload 42
          odota-tapahtuma-1 (async/go (async/<! tarkkailija-1)
                                    (swap! kuuntelu-ja-tarkkailu-lkm inc))
          odota-tapahtuma-2 (async/go (async/<! tarkkailija-2)
                                      (swap! kuuntelu-ja-tarkkailu-lkm inc))
          odota-tapahtuma-3 (async/go (async/<! tarkkailija-3)
                                      (swap! kuuntelu-ja-tarkkailu-lkm inc))]
      (is (= 0 @kuuntelu-ja-tarkkailu-lkm))
      (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
      (<!!-timeout odota-tapahtuma-1 1000)
      (<!!-timeout odota-tapahtuma-2 1000)
      (<!!-timeout odota-tapahtuma-3 1000)
      (is (= 5 @kuuntelu-ja-tarkkailu-lkm))))
  (testing "Viimeisin tarkkailu onnistuu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          ensimmainen-a-payload 42]
      (let [tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (is (not (false? tarkkailija)))
        (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a ensimmainen-a-payload (:nimi harja-tarkkailija))
        (is (= (<!!-timeout odota-tapahtuma 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload ensimmainen-a-payload})))
      (let [tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin))
            a-payload 1337]
        (is (= (<!!-timeout tarkkailija 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload ensimmainen-a-payload})
            "Kanavaan pitäisi tulla kakutettu arvo")
        (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (<!!-timeout tarkkailija 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla kakutettu arvo")))))

(deftest tarkkaile-kanavaa-testit-ei-ok-tapaukset
  (testing "Väärä hash saatu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          data {:a 1}
          tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))]
      (with-redefs [konv/sha256 (constantly "differentHash")]
        (let [julkaisu-onnistui? (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a data (:nimi harja-tarkkailija))]
          (is julkaisu-onnistui?)))
      (is (thrown? TimeoutException (<!!-timeout tarkkailija 500))
          "Väärällä hashillä olevaa dataa ei pitäisi antaa tarkkailijoille!")
      (let [viimeisin-tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin))]
        (is (thrown? TimeoutException (<!!-timeout viimeisin-tarkkailija 500))
            "Väärällä hashillä olevaa dataa ei pitäisi antaa viimeisin tarkkailijoille!"))))
  (testing "Väärän tyyppinen kuuntelija"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
      (is (thrown? IllegalArgumentException (tapahtumat/kuuntele! tapahtumat-k :tapahtuma-a 3)))
      (is (thrown? ArityException (tapahtumat/kuuntele! tapahtumat-k :tapahtuma-a (fn []))))))
  (testing "Käsky tapahtuma-looppiin ilman funktiota"
    (let [err-count (atom 0)
          lahde-redefsista? (atom false)
          original-pura-tapahtumat tapahtumat/pura-tapahtuma-loopin-ajot!]
      (with-redefs [log/error (fn [& args]
                                (swap! err-count inc))
                    tapahtumat/pura-tapahtuma-loopin-ajot! (fn [& args]
                                                             (apply original-pura-tapahtumat args)
                                                             (reset! lahde-redefsista? true))]
        (async/put! @#'tapahtumat/tapahtuma-loopin-ajot {:f (fn [])})
        (async/put! @#'tapahtumat/tapahtuma-loopin-ajot {:tunnistin "foo"})
        (async/put! @#'tapahtumat/tapahtuma-loopin-ajot {})
        (while (not @lahde-redefsista?)
          (async/<!! (async/timeout 10))))
      (is (= 3 @err-count)
          "Tapahtuma loopin käskyillä pitäisi aina olla funktio ja tunnistin määritettynä"))))

(deftest julkaisu-ilmoittaa-kaikkiin-jarjestelmiin)

(deftest yhta-aikaa-julkaisu-toimii)

(deftest possukanavien-luonti)

(deftest hashaus-onnistuu)