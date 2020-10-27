(ns harja.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.kyselyt.konversio :as konv]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [clojure.string :as clj-str])
  (:import (java.util.concurrent TimeoutException)
           (java.util UUID)
           (clojure.lang ArityException)))

(def toinen-harja-tarkkailija nil)
(def toinen-jarjestelma nil)

(defn luo-jarjestelma [_]
  (component/start
    (component/system-map
      :db (tietokanta/luo-tietokanta testitietokanta)
      :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db]))))

(defn luo-harja-tarkkailija [nimi _]
  (assoc (component/start
           (component/system-map
             :db-event (event-tietokanta/luo-tietokanta testitietokanta)
             :klusterin-tapahtumat (component/using
                                     (tapahtumat/luo-tapahtumat)
                                     {:db :db-event})))
    :nimi nimi))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root #'harja-tarkkailija (partial luo-harja-tarkkailija "tarkkailija-a"))
  (alter-var-root #'jarjestelma luo-jarjestelma)

  (testit)
  (try (alter-var-root #'jarjestelma component/stop)
       (catch Exception e (println "saatiin poikkeus komponentin sammutuksessa: " e)))
  (try (alter-var-root #'harja-tarkkailija component/stop)
       (catch Exception e (println "saatiin poikkeus harja-tarkkailija systeemin sammutuksessa: " e))))

(use-fixtures :each jarjestelma-fixture)

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
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          err-count (atom 0)
          lahde-redefsista? (atom false)
          original-pura-tapahtumat tapahtumat/pura-tapahtuma-loopin-ajot!
          original-config log/*config*]
      (log/merge-config! {:middleware [(fn [msg]
                                         (println "MIDDLEW MSG")
                                         (when (= :error (:level msg))
                                           (swap! err-count inc))
                                         msg)]})
      (with-redefs [tapahtumat/pura-tapahtuma-loopin-ajot! (fn [& args]
                                                             (apply original-pura-tapahtumat args)
                                                             (println "LÄHDE POIS")
                                                             (reset! lahde-redefsista? true))]
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {:f (fn [])})
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {:tunnistin "foo"})
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {})
        (while (not @lahde-redefsista?)
          (async/<!! (async/timeout 10))))
      (log/set-config! original-config)
      (is (= 3 @err-count)
          "Tapahtuma loopin käskyillä pitäisi aina olla funktio ja tunnistin määritettynä"))))

(deftest julkaisu-ilmoittaa-kaikkiin-jarjestelmiin
  (alter-var-root #'toinen-harja-tarkkailija (partial luo-harja-tarkkailija "tarkkailija-b"))
  (alter-var-root #'toinen-jarjestelma luo-jarjestelma)

  (testing "Perus tarkkailun aloitus onnistuu molemmissa järjestelmissä"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          toinen-tapahtumat-k (:klusterin-tapahtumat toinen-harja-tarkkailija)]
      (let [tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a))
            toinen-tarkkailija (async/<!! (tapahtumat/tarkkaile! toinen-tapahtumat-k :tapahtuma-a))
            a-payload 42
            odota-tapahtuma (async/go (async/<! tarkkailija))
            toinen-odota-tapahtuma (async/go (async/<! toinen-tarkkailija))]
        (is (not (false? tarkkailija)))
        (is (not (false? toinen-tarkkailija)))
        (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (<!!-timeout odota-tapahtuma 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
        (is (= (<!!-timeout toinen-odota-tapahtuma 1000)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
        (let [a-payload 1337
              odota-tapahtuma (async/go (async/<! tarkkailija))
              toinen-odota-tapahtuma (async/go (async/<! toinen-tarkkailija))]
          (tapahtumat/julkaise! toinen-tapahtumat-k :tapahtuma-a a-payload (:nimi toinen-harja-tarkkailija))
          (is (= (<!!-timeout odota-tapahtuma 1000)
                 {:palvelin (:nimi toinen-harja-tarkkailija)
                  :payload a-payload})
              "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
          (is (= (<!!-timeout toinen-odota-tapahtuma 1000)
                 {:palvelin (:nimi toinen-harja-tarkkailija)
                  :payload a-payload})
              "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")))))
  (testing "viimeisin-per-palvelin tapahtuma toimii"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          toinen-tapahtumat-k (:klusterin-tapahtumat toinen-harja-tarkkailija)
          a-payload-palvelin-1 42
          a-payload-palvelin-2 1337
          _ (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a a-payload-palvelin-1 (:nimi harja-tarkkailija))
          _ (tapahtumat/julkaise! toinen-tapahtumat-k :tapahtuma-a a-payload-palvelin-2 (:nimi toinen-harja-tarkkailija))
          tarkkailija (async/<!! (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin-per-palvelin))
          paluuarvo-palvelin-1 {:palvelin (:nimi harja-tarkkailija)
                                :payload a-payload-palvelin-1}
          paluuarvo-palvelin-2 {:palvelin (:nimi toinen-harja-tarkkailija)
                                :payload a-payload-palvelin-2}]
      (is (let [paluuarvo (<!!-timeout tarkkailija 1000)]
            (or (= paluuarvo paluuarvo-palvelin-1)
                (= paluuarvo paluuarvo-palvelin-2)))
          "viimeisin-per-palvelin tarkkailijalle pitäisi tulla arvot molemmilta palvelimilta.
           Järjestystä ei ole fixattu")
      (is (let [paluuarvo (<!!-timeout tarkkailija 1000)]
            (or (= paluuarvo paluuarvo-palvelin-1)
                (= paluuarvo paluuarvo-palvelin-2)))
          "viimeisin-per-palvelin tarkkailijalle pitäisi tulla arvot molemmilta palvelimilta.
           Järjestystä ei ole fixattu")))

  (try (alter-var-root #'toinen-jarjestelma component/stop)
       (catch Exception e (println "saatiin poikkeus toisen komponentin sammutuksessa: " e)))
  (try (alter-var-root #'toinen-harja-tarkkailija component/stop)
       (catch Exception e (println "saatiin poikkeus toisen harja-tarkkailija systeemin sammutuksessa: " e))))

(deftest possukanavien-luonti
  (let [uusi-tapahtuma "foo"
        uusikanava (@#'tapahtumat/kaytettava-kanava! (get-in harja-tarkkailija [:klusterin-tapahtumat :db :db-spec]) uusi-tapahtuma)]
    (testing "Possukanavan luonti"
      (let [toinen-tapahtuma "bar"
            toinen-kanava (@#'tapahtumat/kaytettava-kanava! (get-in harja-tarkkailija [:klusterin-tapahtumat :db :db-spec]) toinen-tapahtuma)]
        (is (uuid? (UUID/fromString (-> uusikanava (clj-str/replace-first #"k_" "") (clj-str/replace #"_" "-"))))
            "Possukanavan pitäisi olla modifioitu versio UUID:stä")
        (is (uuid? (UUID/fromString (-> toinen-kanava (clj-str/replace-first #"k_" "") (clj-str/replace #"_" "-"))))
            "Possukanavan pitäisi olla modifioitu versio UUID:stä")
        (is (not= uusikanava toinen-kanava)
            "Possukanavien nimet pitäisi olla uniikkeja!")
        (is (= uusikanava (@#'tapahtumat/kaytettava-kanava! (get-in harja-tarkkailija [:klusterin-tapahtumat :db :db-spec]) uusi-tapahtuma))
            "Samalla tapahtumalla pitäisi palautua sama possukanava")))
    (testing "Kannassa olevaa possukanavaa ei voi muokata"
      (let [primary-key uusikanava]
        (u "UPDATE tapahtuma SET kanava='foobar' WHERE kanava='" primary-key "'")
        (u "UPDATE tapahtuma SET nimi='foobar' WHERE kanava='" primary-key "'")
        (let [muokkauksen-jalkeen (first (q-map "SELECT nimi, kanava FROM tapahtuma WHERE kanava='" primary-key "'"))]
          (is (= uusi-tapahtuma (:nimi muokkauksen-jalkeen))
              "Olemassa olevan tapahtuman nimeä ei saisi muuttaa")
          (is (= uusikanava (:kanava muokkauksen-jalkeen))
              "Olemassa olevan tapahtuman kanavaa ei saisi muuttaa"))))))

(deftest tapahtumat-julkaistaan-jarjestyksessa-ilman-aukkoja-julkaisuketjuun
  (let [aja-loop (async/chan)
        loop-ajettu (async/chan)
        tapahtuma-loop-sisalto-original @#'tapahtumat/tapahtuma-loop-sisalto]
    (with-redefs [tapahtumat/tapahtuma-loop-sisalto (fn [& args]
                                                         (async/<!! aja-loop)
                                                      (let [paluuarvo (apply tapahtuma-loop-sisalto-original args)]
                                                        (async/put! loop-ajettu true)
                                                        paluuarvo))]
      (testing "Viimeisin tarkkailija saa kaikki tapahtumat kakutustapahtuman jälkeen"
        (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
              kakutuskeskustelukanava (async/chan)
              tarkkailija-kuuntelun-jalkeen-original @#'tapahtumat/tarkkailija-kuuntelun-jalkeen
              _ (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a {:a 1} (:nimi harja-tarkkailija))]
          (with-redefs [tapahtumat/tarkkailija-kuuntelun-jalkeen (fn [& args]
                                                                      (let [original-fn (apply tarkkailija-kuuntelun-jalkeen-original args)]
                                                                        (fn [& args-sisa]
                                                                          (println "Ajetaan tarkkailun jälkeinen fn")
                                                                          (apply original-fn args-sisa)
                                                                          (println "nnetaan kakutus-tehty")
                                                                          (async/>!! kakutuskeskustelukanava :kakutus-tehty)
                                                                          (async/<!! kakutuskeskustelukanava))))]
            (let [tarkkailija (tapahtumat/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin)]
              (is (not (nil? (<!!-timeout (async/go-loop []
                                            (async/offer! aja-loop true)
                                            (if-let [tila (async/poll! kakutuskeskustelukanava)]
                                              tila
                                              (do (async/poll! loop-ajettu)
                                                  (recur))))
                                          2000))))
              (loop [i 2]
                (when-not (= i 10)
                  (tapahtumat/julkaise! tapahtumat-k :tapahtuma-a {:a i} (:nimi harja-tarkkailija))
                  (recur (inc i))))
              (is (thrown? TimeoutException (<!!-timeout loop-ajettu 200))
                  "tapahtuma loopin pitää odotella, että kaikki kuittaukset on tullut perille")
              (async/put! kakutuskeskustelukanava :lopeta-jalkeen-funktio)
              (is (<!!-timeout loop-ajettu 200)
                  "tapahtuma loop pitäsi nyt päästä loppuun")
              (let [tarkkailija (<!!-timeout tarkkailija 200)]
                (is (not (nil? tarkkailija)) "Ei pitäs tulla timeout tässä")
                (loop [arvo (<!!-timeout tarkkailija 200)
                       i 1]
                  (cond
                    (< i 9) (do (is (= arvo {:palvelin (:nimi harja-tarkkailija)
                                              :payload {:a i}})
                                     "Lähetetyt arvot pitäisi tulla järjestyksessä")
                                 (recur (<!!-timeout tarkkailija 200)
                                        (inc i)))
                    (= i 9) (do (is (= arvo {:palvelin (:nimi harja-tarkkailija)
                                             :payload {:a i}})
                                    "Lähetetyt arvot pitäisi tulla järjestyksessä")
                                (is (thrown? TimeoutException (<!!-timeout tarkkailija 200))
                                    "Ei pitäisi olla enää tapahtumia")))))))))
      (testing "Viimeisin tarkkailija ei saa tapahtumia ennen kakuttamista")
      (testing "Tapahtumat tulee siinä järjestyksessä kuin ne on saapunut kantaan"))))
