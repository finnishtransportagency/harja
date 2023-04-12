(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset_test
  (:require
    [clojure.string :as str]
    [com.stuartsierra.component :as component]
    [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
    [harja.testi :refer :all]
    [java-http-clj.websocket :as ws]
    [taoensso.timbre :as log]
    [harja.oikea-http-palvelin-jarjestelma-fixture :as oikea-palvelin-fixture]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tr-komponentti]
    [harja.palvelin.palvelut.tuck-remoting.ilmoitukset :as sut]
    [harja.tuck-remoting.ilmoitukset-eventit :as ilmoitukset-eventit]
    [tuck.remoting.transit :as transit]))

(def jarjestelma-fixture (oikea-palvelin-fixture/luo-fixture
                           :tuck-remoting (component/using
                                            (tr-komponentti/luo-tuck-remoting)
                                            [:http-palvelin :db])
                           :ilmoitukset-ws-palvelu (component/using
                                                     (sut/luo-ilmoitukset-ws)
                                                     [:tuck-remoting :db])))

(def ws-client-yhteydet-atom
  "Kokoelma WS-client yhteysobjekteja"
  (atom {}))

(def ws-client-yhteys-vastaukset-atom
  "Saadut vastaukset ws-yhteyden id:llä varustettuna"
  (atom {}))

(defn websocket-client [id]
  (let [ws-url (str "ws://" "localhost:" portti "/_/ws?")]
    (log/info "Yhdistetään WS: " ws-url)

    (ws/build-websocket
      ws-url
      {:on-text (fn [ws vastaus last?]
                  (log/info (str "WS-asiakas " id " sai vastauksen:" vastaus (transit/transit->clj vastaus)))
                  (swap! ws-client-yhteys-vastaukset-atom assoc id (transit/transit->clj vastaus)))
       :on-error (fn [ws throwable]
                   (log/error (str "WS-asiakas " id " sai virheen: ") (.getMessage throwable)))}
      {:headers {"oam_remote_user" "jvh"
                 "oam_user_first_name" "Jalmari"
                 "oam_user_last_name" "Järjestelmävastuuhenkilö"
                 "oam_user_mail" "erkki@esimerkki.com"
                 "oam_user_mobile" "1234567890"
                 "oam_organization" "Liikennevirasto"
                 "oam_groups" "Jarjestelmavastaava"}})))

(defn luo-ws-yhteys! [id]
  (when id
    (swap! ws-client-yhteydet-atom assoc id (websocket-client id))))

(defn ws-yhteys [id]
  (get-in @ws-client-yhteydet-atom [id]))

(defn sulje-ws-yhteys! [id]
  (when (get-in @ws-client-yhteydet-atom [id])
    (ws/close (get-in @ws-client-yhteydet-atom [id]))
    (swap! ws-client-yhteydet-atom dissoc id)))

(defn sulje-kaikki-ws-yhteydet! []
  (when (seq @ws-client-yhteydet-atom)
    (doseq [yhteys (vals @ws-client-yhteydet-atom)]
      (ws/close yhteys))))

(defn ws-vastaus [id]
  (when id
    (get-in @ws-client-yhteys-vastaukset-atom [id])))

(defn siivoa-ws-vastaus! [id]
  (swap! ws-client-yhteys-vastaukset-atom dissoc id))

(defn websocket-fixture [testit]
  (reset! ws-client-yhteys-vastaukset-atom {})

  (testit)
  ;; Jos ws-yhteyksiä on päällä testin jälkeen, varmista että ne suljetaan
  (when (seq @ws-client-yhteydet-atom)
    (doseq [yhteys (vals @ws-client-yhteydet-atom)]
      (ws/close yhteys)))

  (reset! ws-client-yhteydet-atom {}))

(use-fixtures :each (compose-fixtures jarjestelma-fixture websocket-fixture))

(def event-id (atom 0))

(defn asiakas-e!
  "Lähettää tuck-remoten server-eventin, kuten selaimessa toimiva CLJS WS-client sen lähettäisi"
  [ws-yhteys]
  (fn [tuck-server-event]
    (ws/send
      ws-yhteys
      (transit/clj->transit
        {::tr/event-id (swap! event-id inc)
         ::tr/event-type (str/replace (.getName (type tuck-server-event)) \_ \-)
         ::tr/event-args (into {} tuck-server-event)}))))

(deftest testaa-ilmoitusten-kuuntelu
  (testing "Testaa onnistuuko urakkakohtaisten kuuntelun aloittaminen"
    ;; Luo yhteys
    (luo-ws-yhteys! :asiakas-1)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (asiakas-e! (ws-yhteys :asiakas-1))]

      ;; Aloita urakan 35 ilmoitusten kuuntelija
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakka 35}))
      (odota-ehdon-tayttymista #(seq (ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (ws-vastaus :asiakas-1))))

      ;; Siivoa vastaus
      (siivoa-ws-vastaus! :asiakas-1)

      (sulje-kaikki-ws-yhteydet!)))

  (testing "Testaa onnistuuko kaikkien ilmoitusten kuuntelun aloittaminen (suodatettu kuuntelija)"
    ;; Luo yhteys
    (luo-ws-yhteys! :asiakas-1)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (asiakas-e! (ws-yhteys :asiakas-1))]

      ;; Aloita kuuntelu
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakkatyyppi :kaikki :hallintayksikko 12}))
      (odota-ehdon-tayttymista #(seq (ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (ws-vastaus :asiakas-1))))

      ;; Siivoa vastaus
      (siivoa-ws-vastaus! :asiakas-1)

      (sulje-kaikki-ws-yhteydet!))))
