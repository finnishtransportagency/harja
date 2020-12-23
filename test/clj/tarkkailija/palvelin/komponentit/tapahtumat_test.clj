(ns ^:hidas tarkkailija.palvelin.komponentit.tapahtumat-test
  (:require [harja.palvelin.tapahtuma-protokollat :as tapahtumat-p]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [tarkkailija.palvelin.komponentit
             [event-tietokanta :as event-tietokanta]
             [tapahtumat :as tapahtumat]
             [jarjestelma-rajapinta :as rajapinta]]
            [tarkkailija.palvelin.palvelut.tapahtuma :as tapahtuma]
            [harja.tyokalut.predikaatti :as predikaatti]
            [harja.kyselyt.konversio :as konv]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [clojure.string :as clj-str]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util.concurrent TimeoutException)
           (java.util UUID)
           (clojure.lang ArityException)))

(def toinen-harja-tarkkailija nil)
(def toinen-jarjestelma nil)

(def tarkkailija-asetukset {:loop-odotus 100})
(def default-odottelu (+ (:loop-odotus tarkkailija-asetukset) 600))
(def default-odottelu-pidennetty (+ default-odottelu 1000))
(def pitka-odottelu (+ default-odottelu-pidennetty 5000))

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
                                     (tapahtumat/luo-tapahtumat tarkkailija-asetukset)
                                     {:db :db-event})
             :tapahtuma (component/using
                          (tapahtuma/->Tapahtuma)
                          [:klusterin-tapahtumat :rajapinta])
             :rajapinta (rajapinta/->Rajapintakasittelija)))
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

(deftest julkaisu-ja-kuuntelu
  (testing "tapahtumat-komponentti on luotu onnistuneesti"
    (is (some? (:klusterin-tapahtumat harja-tarkkailija))))
  (let [saatiin (atom nil)]
    (testing "Perustapaus"
      (let [lopetus-fn (tapahtumat-p/kuuntele! (:klusterin-tapahtumat harja-tarkkailija)
                                               "seppo"
                                             (fn kuuntele-callback [viesti]
                                               (reset! saatiin true)))
            kuuntelija-funktiot-kuuntelun-jalkeen @(get-in harja-tarkkailija [:klusterin-tapahtumat :kuuntelijat])]
        (is (fn? lopetus-fn))
        (is (tapahtumat-p/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi") "Julkaisu ei onnistunut")
        (is (odota-arvo saatiin))
        (lopetus-fn)
        (is (= 1 (count kuuntelija-funktiot-kuuntelun-jalkeen)) "Yhtä tapahtumaa kuunnellaan")
        (is (= 1 (count (-> kuuntelija-funktiot-kuuntelun-jalkeen first val))) "Yhdellä tapahtumalla on yksi kuuntelija")
        (is (= 0 (count (-> harja-tarkkailija (get-in [:klusterin-tapahtumat :kuuntelijat]) deref first val)))
            "Tapahtumalla ei ole enää kuuntelijaa, kun tapahtuman kuuntelu on lopetettu")))
    (testing "Toipuminen kantayhteyden katkosta"
      (reset! saatiin nil)
      (let [lopetus-fn (tapahtumat-p/kuuntele! (:klusterin-tapahtumat harja-tarkkailija) "seppo"
                                             (fn kuuntele-callback [viesti]
                                               (reset! saatiin true)))
            kantakatkos (katkos-testikantaan!)]
        (<!!-timeout kantakatkos 5000)
        (tapahtumat-p/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (thrown? AssertionError (odota-arvo saatiin 1000)) "Ei pitäisi saada tapahtumia kun kanta on alhaalla")
        (async/>!! kantakatkos :kaynnista)
        (<!!-timeout kantakatkos 5000)
        (loop [[kerta & loput-kerrat] (range 0 50)
               testattu-kerran? false]
          (when-not (or (nil? kerta)
                        @(get-in harja-tarkkailija [:klusterin-tapahtumat ::tapahtumat/tapahtuma-loop-kaynnissa?]))
            (when-not testattu-kerran?
              (tapahtumat-p/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
              (is (thrown? AssertionError (odota-arvo saatiin default-odottelu)) "Ei pitäisi saada tapahtumia ennenkuin tapahtumaloop on käynnissä"))
            (async/<!! (async/timeout 100))
            (recur loput-kerrat
                   true)))
        (tapahtumat-p/julkaise! (:klusterin-tapahtumat harja-tarkkailija) "seppo" "foo" "testi")
        (is (odota-arvo saatiin default-odottelu) "Pitäisi saada tapahtumia, kun tapahtumaloop on alkanut")
        (lopetus-fn)))))

(deftest tarkkaile-kanavaa-testit-ok-tapaukset
  (testing "Perus tarkkailun aloitus onnistuu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
      (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
            a-payload 42
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (is (not (false? tarkkailija)))
        (is (thrown? TimeoutException (<!!-timeout odota-tapahtuma 500))
            "Kanavassa ei pitäisi olla vielä mitään")
        (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (let [tapahtuman-arvo (<!!-timeout odota-tapahtuma default-odottelu)]
          (is (= (dissoc tapahtuman-arvo :aika)
                 {:palvelin (:nimi harja-tarkkailija)
                  :payload a-payload})
              "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
          (is (contains? tapahtuman-arvo :aika) "Tapahtumien pitäisi sisältää aika kentän")))
      (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
            a-payload 1337
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (dissoc (<!!-timeout odota-tapahtuma default-odottelu) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan ei pitäisi tulla kakutettua kamaa vaan juurikin se mikä on lähetetty"))))
   (testing "Julkaiseminen ilmoittaa kaikille tarkkailijoille"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          kuuntelu-ja-tarkkailu-lkm (atom 0)
          tarkkailija-1 (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
          tarkkailija-2 (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
          tarkkailija-3 (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
          kuuntelija-1 (tapahtumat-p/kuuntele! tapahtumat-k :tapahtuma-a (fn [_] (swap! kuuntelu-ja-tarkkailu-lkm inc)))
          kuuntelija-2 (tapahtumat-p/kuuntele! tapahtumat-k :tapahtuma-a (fn [_] (swap! kuuntelu-ja-tarkkailu-lkm inc)))
          a-payload 42
          odota-tapahtuma-1 (async/go (async/<! tarkkailija-1)
                                      (swap! kuuntelu-ja-tarkkailu-lkm inc))
          odota-tapahtuma-2 (async/go (async/<! tarkkailija-2)
                                      (swap! kuuntelu-ja-tarkkailu-lkm inc))
          odota-tapahtuma-3 (async/go (async/<! tarkkailija-3)
                                      (swap! kuuntelu-ja-tarkkailu-lkm inc))]
      (is (= 0 @kuuntelu-ja-tarkkailu-lkm))
      (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
      (<!!-timeout odota-tapahtuma-1 default-odottelu)
      (<!!-timeout odota-tapahtuma-2 default-odottelu)
      (<!!-timeout odota-tapahtuma-3 default-odottelu)
      (is (= 5 @kuuntelu-ja-tarkkailu-lkm)))
    )
  (testing "Viimeisin tarkkailu onnistuu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          ensimmainen-a-payload 42]
      (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
            odota-tapahtuma (async/go (async/<! tarkkailija))]
        (is (not (false? tarkkailija)))
        (is (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a ensimmainen-a-payload (:nimi harja-tarkkailija)))
        (is (= (dissoc (<!!-timeout odota-tapahtuma default-odottelu-pidennetty) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload ensimmainen-a-payload})))
      (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin))
            a-payload 1337]
        (is (= (dissoc (<!!-timeout tarkkailija default-odottelu-pidennetty) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload ensimmainen-a-payload})
            "Kanavaan pitäisi tulla kakutettu arvo")
        (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (dissoc (<!!-timeout tarkkailija default-odottelu-pidennetty) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla kakutettu arvo")))))

(deftest tarkkaile-kanavaa-testit-ei-ok-tapaukset
  (testing "Väärä hash saatu"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          data {:a 1}
          tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))]
      (with-redefs [konv/sha256 (constantly "eriHash")]
        (let [julkaisu-onnistui? (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a data (:nimi harja-tarkkailija))]
          (is julkaisu-onnistui?)))
      (is (thrown? TimeoutException (<!!-timeout tarkkailija default-odottelu))
          "Väärällä hashillä olevaa dataa ei pitäisi antaa tarkkailijoille!")
      (let [viimeisin-tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin))]
        (is (thrown? TimeoutException (<!!-timeout viimeisin-tarkkailija default-odottelu))
            "Väärällä hashillä olevaa dataa xnei pitäisi antaa viimeisin tarkkailijoille!"))))
  (testing "Väärän tyyppinen kuuntelija"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
      (is (thrown? IllegalArgumentException (tapahtumat-p/kuuntele! tapahtumat-k :tapahtuma-a 3)))
      (is (thrown? ArityException (tapahtumat-p/kuuntele! tapahtumat-k :tapahtuma-a (fn []))))))
  (testing "Käsky tapahtuma-looppiin ilman viestintä- ja possukanavaa"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          err-count (atom 0)
          lahde-redefsista? (atom false)
          original-pura-tapahtumat tapahtumat/pura-tapahtuma-loopin-ajot!
          original-config log/*config*]
      (log/merge-config! {:middleware [(fn [msg]
                                         (when (= :error (:level msg))
                                           (swap! err-count inc))
                                         msg)]})
      (with-redefs [tapahtumat/pura-tapahtuma-loopin-ajot! (fn [& args]
                                                             (let [paluuarvo (apply original-pura-tapahtumat args)]
                                                               (reset! lahde-redefsista? true)
                                                               paluuarvo))]
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {:viestintakanava (async/chan 3)})
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {:possukanava (@#'tapahtumat/uusi-tapahtuman-kanava)})
        (async/put! (::tapahtumat/tapahtuma-loopin-ajot tapahtumat-k) {})
        (while (not @lahde-redefsista?)
          (async/<!! (async/timeout 50)))
        (async/<!! (async/timeout 200)))
      (log/set-config! original-config)
      (is (= 3 @err-count)
          "Tapahtuma loopin käskyillä pitäisi aina olla funktio ja tunnistin määritettynä")))
  (testing "Väärä argumentti yhtäaikaa alkaville"
    (is (thrown? IllegalArgumentException (binding [tapahtumat/*tarkkaile-yhta-aikaa* 1]
                                            (tapahtumat-p/tarkkaile! (:klusterin-tapahtumat harja-tarkkailija) :foo))))
    (is (thrown? IllegalArgumentException (binding [tapahtumat/*tarkkaile-yhta-aikaa* {:tunnistin :foo :lkm 3}]
                                            (tapahtumat-p/tarkkaile! (:klusterin-tapahtumat harja-tarkkailija) :foo))))
    (is (thrown? IllegalArgumentException (binding [tapahtumat/*tarkkaile-yhta-aikaa* {:lkm 3}]
                                            (tapahtumat-p/tarkkaile! (:klusterin-tapahtumat harja-tarkkailija) :foo))))
    (is (thrown? IllegalArgumentException (binding [tapahtumat/*tarkkaile-yhta-aikaa* {:tunnistin "foo"}]
                                            (tapahtumat-p/tarkkaile! (:klusterin-tapahtumat harja-tarkkailija) :foo))))))

(deftest julkaisu-ilmoittaa-kaikkiin-jarjestelmiin
  (alter-var-root #'toinen-harja-tarkkailija (partial luo-harja-tarkkailija "tarkkailija-b"))
  (alter-var-root #'toinen-jarjestelma luo-jarjestelma)

  (testing "Perus tarkkailun aloitus onnistuu molemmissa järjestelmissä"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          toinen-tapahtumat-k (:klusterin-tapahtumat toinen-harja-tarkkailija)]
      (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
            toinen-tarkkailija (async/<!! (tapahtumat-p/tarkkaile! toinen-tapahtumat-k :tapahtuma-a))
            a-payload 42
            odota-tapahtuma (async/go (async/<! tarkkailija))
            toinen-odota-tapahtuma (async/go (async/<! toinen-tarkkailija))]
        (is (not (false? tarkkailija)))
        (is (not (false? toinen-tarkkailija)))
        (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
        (is (= (dissoc (<!!-timeout odota-tapahtuma default-odottelu) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
        (is (= (dissoc (<!!-timeout toinen-odota-tapahtuma default-odottelu) :aika)
               {:palvelin (:nimi harja-tarkkailija)
                :payload a-payload})
            "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
        (let [a-payload 1337
              odota-tapahtuma (async/go (async/<! tarkkailija))
              toinen-odota-tapahtuma (async/go (async/<! toinen-tarkkailija))]
          (tapahtumat-p/julkaise! toinen-tapahtumat-k :tapahtuma-a a-payload (:nimi toinen-harja-tarkkailija))
          (is (= (dissoc (<!!-timeout odota-tapahtuma default-odottelu-pidennetty) :aika)
                 {:palvelin (:nimi toinen-harja-tarkkailija)
                  :payload a-payload})
              "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")
          (is (= (dissoc (<!!-timeout toinen-odota-tapahtuma default-odottelu-pidennetty) :aika)
                 {:palvelin (:nimi toinen-harja-tarkkailija)
                  :payload a-payload})
              "Kanavaan pitäisi tulla se kama, joka on juuri lähetetty")))))
  (testing "viimeisin-per-palvelin tapahtuma toimii"
    (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
          toinen-tapahtumat-k (:klusterin-tapahtumat toinen-harja-tarkkailija)
          a-payload-palvelin-1 42
          a-payload-palvelin-2 1337
          _ (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload-palvelin-1 (:nimi harja-tarkkailija))
          _ (tapahtumat-p/julkaise! toinen-tapahtumat-k :tapahtuma-a a-payload-palvelin-2 (:nimi toinen-harja-tarkkailija))
          tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin-per-palvelin))
          paluuarvo-palvelin-1 {:palvelin (:nimi harja-tarkkailija)
                                :payload a-payload-palvelin-1}
          paluuarvo-palvelin-2 {:palvelin (:nimi toinen-harja-tarkkailija)
                                :payload a-payload-palvelin-2}]
      (is (let [paluuarvo (dissoc (<!!-timeout tarkkailija (* 2 default-odottelu-pidennetty)) :aika)]
            (or (= paluuarvo paluuarvo-palvelin-1)
                (= paluuarvo paluuarvo-palvelin-2)))
          "viimeisin-per-palvelin tarkkailijalle pitäisi tulla arvot molemmilta palvelimilta.
           Järjestystä ei ole fixattu")
      (is (let [paluuarvo (dissoc (<!!-timeout tarkkailija (* 2 default-odottelu-pidennetty)) :aika)]
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
        (is (uuid? (UUID/fromString uusikanava))
            "Possukanavan pitäisi olla modifioitu versio UUID:stä")
        (is (uuid? (UUID/fromString toinen-kanava))
            "Possukanavan pitäisi olla modifioitu versio UUID:stä")
        (is (not= uusikanava toinen-kanava)
            "Possukanavien nimet pitäisi olla uniikkeja!")
        (is (= uusikanava (@#'tapahtumat/kaytettava-kanava! (get-in harja-tarkkailija [:klusterin-tapahtumat :db :db-spec]) uusi-tapahtuma))
            "Samalla tapahtumalla pitäisi palautua sama possukanava")))
    (testing "Kannassa olevaa possukanavaa ei voi muokata"
      (let [primary-key uusikanava]
        (u "UPDATE tapahtumatyyppi SET kanava='foobar' WHERE kanava='" primary-key "'")
        (u "UPDATE tapahtumatyyppi SET nimi='foobar' WHERE kanava='" primary-key "'")
        (let [muokkauksen-jalkeen (first (q-map "SELECT nimi, kanava FROM tapahtumatyyppi WHERE kanava='" primary-key "'"))]
          (is (= uusi-tapahtuma (:nimi muokkauksen-jalkeen))
              "Olemassa olevan tapahtuman nimeä ei saisi muuttaa")
          (is (= uusikanava (:kanava muokkauksen-jalkeen))
              "Olemassa olevan tapahtuman kanavaa ei saisi muuttaa"))))))

(deftest tapahtumat-julkaistaan-jarjestyksessa-ilman-aukkoja-julkaisuketjuun
  (let [aja-loop-atom (atom false)
        loop-ajettu-atom (atom false)
        odota-loopin-ajo! (fn []
                           (odota-ehdon-tayttymista (fn [] (= @loop-ajettu-atom true)) "loop-ajettu-atom ei true" 5000)
                           (reset! loop-ajettu-atom false)
                            true)
        tapahtuma-loop-sisalto-original @#'tapahtumat/tapahtuma-loop-sisalto
        laheta-viimeisimmat-arvot!-original @#'tapahtumat/laheta-viimeisimmat-arvot!]
    (with-redefs [tapahtumat/tapahtuma-loop-sisalto (fn [{db :db :as args-map}]
                                                      ;; Tämä ehto on hyödyllinen oikeastaan vain REPL:issä
                                                      (if (= (:dbname db) (:tietokanta testitietokanta))
                                                        (do
                                                          (odota-ehdon-tayttymista (fn [] (= @aja-loop-atom true)) "aja-loop ei true" 50000)
                                                          (let [paluuarvo (tapahtuma-loop-sisalto-original args-map)]
                                                            (reset! aja-loop-atom false)
                                                            (reset! loop-ajettu-atom true)
                                                            paluuarvo))
                                                        (tapahtuma-loop-sisalto-original args-map)))]
      (reset! aja-loop-atom true)
      (odota-loopin-ajo!)
      (testing "Viimeisin tarkkailija saa kaikki tapahtumat kakutustapahtuman jälkeen"
        (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
              kakutuskeskustelukanava (async/chan)
              _ (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a {:a 1} (:nimi harja-tarkkailija))]
          (with-redefs [tapahtumat/laheta-viimeisimmat-arvot!
                        (fn [ok-ajot-possukanavittain db]
                          (if (= (:dbname db) (:tietokanta testitietokanta))
                            (let [kutsuttu-paluuarvoa? (atom false)
                                  paluuarvo-oritinal @#'tapahtumat/paluuarvo]
                              (with-redefs [tapahtumat/paluuarvo (fn [& args]
                                                                   (reset! kutsuttu-paluuarvoa? true)
                                                                   (apply paluuarvo-oritinal args))]
                                (laheta-viimeisimmat-arvot!-original ok-ajot-possukanavittain db)
                                (when @kutsuttu-paluuarvoa?
                                  (async/>!! kakutuskeskustelukanava :kakutus-tehty)
                                  (async/<!! kakutuskeskustelukanava))))
                            (laheta-viimeisimmat-arvot!-original ok-ajot-possukanavittain db)))]
            (let [tarkkailija (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a :viimeisin)
                  kakutuskanavan-arvo (<!!-timeout (async/thread
                                                     (let [alts-loppu? (atom false)]
                                                       (loop []
                                                         (reset! aja-loop-atom true)
                                                         (let [[arvo _] (async/alts!! [(async/timeout 1000)
                                                                                       (async/go-loop []
                                                                                         (if (and (not @loop-ajettu-atom)
                                                                                                  (not @alts-loppu?))
                                                                                           (recur)
                                                                                           (do (when-not @alts-loppu?
                                                                                                 (reset! loop-ajettu-atom false))
                                                                                               :ajettu)))
                                                                                       kakutuskeskustelukanava])]
                                                           (reset! alts-loppu? true)
                                                           (case arvo
                                                             :kakutus-tehty :jatketaan-matkaa
                                                             (nil :ajettu) (recur))))))
                                                   (+ default-odottelu 4000))]
              (is (not (nil? kakutuskanavan-arvo)))
              (loop [i 2]
                (when-not (= i 10)
                  (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a {:a i} (:nimi harja-tarkkailija))
                  (recur (inc i))))
              (async/>!! kakutuskeskustelukanava :lopeta-jalkeen-funktio)
              ;; Odootetaan että loop menee kakutuksen jälkeen loppuun
              (odota-loopin-ajo!)
              ;; Ajetaan loop uudestaan, jotta saadaan nuo edellä julkaistut 9 tapahtumaa käsiteltyä
              (reset! aja-loop-atom true)
              (odota-loopin-ajo!)
              (let [tarkkailija (<!!-timeout tarkkailija default-odottelu)]
                (is (not (nil? tarkkailija)) "Ei pitäs tulla timeout tässä")
                (loop [arvo (dissoc (<!!-timeout tarkkailija default-odottelu) :aika)
                       i 1]
                  (cond
                    (< i 9) (do (is (= arvo {:palvelin (:nimi harja-tarkkailija)
                                             :payload {:a i}})
                                    "Lähetetyt arvot pitäisi tulla järjestyksessä")
                                (recur (dissoc (<!!-timeout tarkkailija default-odottelu) :aika)
                                       (inc i)))
                    (= i 9) (do (is (= arvo {:palvelin (:nimi harja-tarkkailija)
                                             :payload {:a i}})
                                    "Lähetetyt arvot pitäisi tulla järjestyksessä")
                                (is (thrown? TimeoutException (<!!-timeout tarkkailija default-odottelu))
                                    "Ei pitäisi olla enää tapahtumia")))))))))
      (testing "Viimeisin tarkkailija ei saa tapahtumia ennen kakuttamista"
        (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)
              kakutuskeskustelukanava (async/chan)]
          (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-b {:b 1} (:nimi harja-tarkkailija))
          (with-redefs [tapahtumat/laheta-viimeisimmat-arvot!
                        (fn [ok-ajot-possukanavittain db]
                          (if (= (:dbname db) (:tietokanta testitietokanta))
                            (let [kutsuttu-paluuarvoa? (atom false)
                                  paluuarvo-oritinal @#'tapahtumat/paluuarvo]
                              (with-redefs [tapahtumat/paluuarvo (fn [& args]
                                                                   (reset! kutsuttu-paluuarvoa? true)
                                                                   (async/>!! kakutuskeskustelukanava :kakutus-seuraavaksi)
                                                                   (async/<!! kakutuskeskustelukanava)
                                                                   (apply paluuarvo-oritinal args))]
                                (laheta-viimeisimmat-arvot!-original ok-ajot-possukanavittain db)))
                            (laheta-viimeisimmat-arvot!-original ok-ajot-possukanavittain db)))]
            (let [tarkkailija (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-b :viimeisin)]
              (is (not (nil? (<!!-timeout (async/thread
                                            (let [alts-loppu? (atom false)]
                                              (loop []
                                                (reset! aja-loop-atom true)
                                                (let [[arvo _] (async/alts!! [(async/timeout 1000)
                                                                              (async/go-loop []
                                                                                (if (and (not @loop-ajettu-atom)
                                                                                         (not @alts-loppu?))
                                                                                  (recur)
                                                                                  (do (when-not @alts-loppu?
                                                                                        (reset! loop-ajettu-atom false))
                                                                                      :ajettu)))
                                                                              kakutuskeskustelukanava])]
                                                  (reset! alts-loppu? true)
                                                  (case arvo
                                                    :kakutus-seuraavaksi :jatketaan-matkaa
                                                    (nil :ajettu) (recur))))))
                                          (+ default-odottelu 4000)))))
              (loop [i 2]
                (when-not (= i 10)
                  (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-b {:b i} (:nimi harja-tarkkailija))
                  (recur (inc i))))
              (async/>!! kakutuskeskustelukanava :lopeta-jalkeen-funktio)
              (is (odota-loopin-ajo!)
                  "tapahtuma loop pitäsi nyt päästä loppuun")
              ;; Ajetaan loop uudestaan, jotta saadaan nuo edellä julkaistut 9 tapahtumaa käsiteltyä
              (reset! aja-loop-atom true)
              (odota-loopin-ajo!)
              (let [tarkkailija (<!!-timeout tarkkailija default-odottelu)]
                (is (not (nil? tarkkailija)) "Ei pitäs tulla timeout tässä")
                (do (is (= (dissoc (<!!-timeout tarkkailija default-odottelu) :aika)
                           {:palvelin (:nimi harja-tarkkailija)
                            :payload {:b 9}}))
                    (is (thrown? TimeoutException (<!!-timeout tarkkailija default-odottelu))
                        "Pitäisi olla vain tuo yksi arvo"))))))))
    (reset! aja-loop-atom true)))

(deftest ryhmittain-ajettavat-testit-ajetaan-yhta-aikaa
  (let [aja-loop (async/chan)
        loop-ajettu (async/chan)
        aja-loop-atom (atom false)
        loop-ajettu-atom (atom false)
        odota-loopin-ajo! (fn []
                            (odota-ehdon-tayttymista (fn [] (= @loop-ajettu-atom true)) "loop-ajettu-atom ei true" 5000)
                            (reset! loop-ajettu-atom false)
                            true)
        loop-kunnes-realisoinut (fn loop-kunnes-realisoinut
                                  ([kanava] (loop-kunnes-realisoinut kanava (+ 2000 default-odottelu)))
                                  ([kanava timeout]
                                   (let [lopeta-looppaus (async/chan)]
                                     (try (<!!-timeout (async/go-loop []
                                                         (when-not (async/poll! lopeta-looppaus)
                                                           (reset! aja-loop-atom true)
                                                           (if-let [kanavan-arvo (async/poll! kanava)]
                                                             (do (async/put! lopeta-looppaus true)
                                                                 (odota-loopin-ajo!)
                                                                 kanavan-arvo)
                                                             (do (odota-loopin-ajo!)
                                                                 (recur)))))
                                                       timeout)
                                          (catch TimeoutException e
                                            (async/put! lopeta-looppaus true)
                                            (throw e))))))
        odota-yksi-loop (fn []
                          (reset! aja-loop-atom true)
                          (odota-loopin-ajo!))
        tapahtuma-loop-sisalto-original @#'tapahtumat/tapahtuma-loop-sisalto]
    (with-redefs [tapahtumat/tapahtuma-loop-sisalto (fn [{db :db :as args-map}]
                                                      ;; Tämä ehto on hyödyllinen oikeastaan vain REPL:issä
                                                      (if (= (:dbname db) (:tietokanta testitietokanta))
                                                        (do
                                                          (odota-ehdon-tayttymista (fn [] (= @aja-loop-atom true)) "aja-loop ei true" 50000)
                                                          (let [paluuarvo (tapahtuma-loop-sisalto-original args-map)]
                                                            (reset! aja-loop-atom false)
                                                            (reset! loop-ajettu-atom true)
                                                            paluuarvo))
                                                        (tapahtuma-loop-sisalto-original args-map)))]
      (testing "Viimeisin tarkkailija ei saa tapahtumia ennen kakuttamista"
        (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
          (let [tarkkailija-1 (binding [tapahtumat/*tarkkaile-yhta-aikaa* {:tunnistin "foo"
                                                                           :lkm 2}]
                                (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
                tarkkailija-2 (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a)
                tarkkailija-2 (loop-kunnes-realisoinut tarkkailija-2)]
            (is (predikaatti/chan? tarkkailija-2))
            (is (thrown? TimeoutException (loop-kunnes-realisoinut tarkkailija-1 100))
                "Tarkkailija-1 pitäisi odotella toista tarkkailijaa")
            (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a {:a 1} (:nimi harja-tarkkailija))
            (is (= (dissoc (loop-kunnes-realisoinut tarkkailija-2 default-odottelu) :aika)
                   {:palvelin (:nimi harja-tarkkailija)
                    :payload {:a 1}})
                "Tarkkailija-2 ei pitäisi jumittaa ykkösen takia")
            (let [tarkkailija-3 (binding [tapahtumat/*tarkkaile-yhta-aikaa* {:tunnistin "foo"
                                                                             :lkm 2}]
                                  (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
                  tarkkailija-3 (loop-kunnes-realisoinut tarkkailija-3)
                  tarkkailija-1 (loop-kunnes-realisoinut tarkkailija-1)]
              (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a {:a 2} (:nimi harja-tarkkailija))
              (odota-yksi-loop)
              (is (= (dissoc (<!!-timeout tarkkailija-1 default-odottelu) :aika)
                     {:palvelin (:nimi harja-tarkkailija)
                      :payload {:a 2}}))
              (is (= (dissoc (<!!-timeout tarkkailija-2 default-odottelu) :aika)
                     {:palvelin (:nimi harja-tarkkailija)
                      :payload {:a 2}}))
              (is (= (dissoc (<!!-timeout tarkkailija-3 default-odottelu) :aika)
                     {:palvelin (:nimi harja-tarkkailija)
                      :payload {:a 2}})))))))
    (reset! aja-loop-atom true)))

(deftest lopeta-tarkkailu-toimii
  (let [tapahtumat-k (:klusterin-tapahtumat harja-tarkkailija)]
    (let [tarkkailija (async/<!! (tapahtumat-p/tarkkaile! tapahtumat-k :tapahtuma-a))
          a-payload 42
          odota-tapahtuma (async/go (async/<! tarkkailija))]
      (is (not (false? tarkkailija)))
      (tapahtumat-p/lopeta-tarkkailu! tapahtumat-k tarkkailija)
      (tapahtumat-p/julkaise! tapahtumat-k :tapahtuma-a a-payload (:nimi harja-tarkkailija))
      (is (thrown? TimeoutException (<!!-timeout odota-tapahtuma default-odottelu))))))