(ns harja.palvelin.komponentit.tuck-remoting-test
  (:require
    [com.stuartsierra.component :as component]
    [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
    [harja.testi :refer :all]
    [java-http-clj.websocket :as ws]
    [taoensso.timbre :as log]
    [harja.oikea-http-palvelin-jarjestelma-fixture :as oikea-palvelin-fixture]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as sut]
    [tuck.remoting.transit :as transit]))

(defrecord TestiEventti [])
(tr/define-client-event TestiEventti)

(def jarjestelma-fixture (oikea-palvelin-fixture/luo-fixture
                           :tuck-remoting (component/using
                                            (sut/luo-tuck-remoting)
                                            [:http-palvelin :db])))

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


(deftest testaa-yhteys-hookit
  (let [yhdistetty-atom (atom nil)
        ;; Rekisteröi palvelinpuolen hookit ennen client-yhteyden luomista
        poista-yhdistaessa-hook (sut/rekisteroi-yhdistaessa-hook! (:tuck-remoting jarjestelma)
                                  (fn [{::tr/keys [e! client-id] :as client}]
                                    (reset! yhdistetty-atom "yhdistetty")))
        poista-yhteys-poikki-hook (sut/rekisteroi-yhteys-poikki-hook! (:tuck-remoting jarjestelma)
                                    (fn [{::tr/keys [e! client-id] :as client}]
                                      (reset! yhdistetty-atom "katkaistu")))
        ;; Luo yhteys
        _ (luo-ws-yhteys! :asiakas-1)]

    ;; Testataan toimiiko yhteyden luominen ja hook
    (odota-ehdon-tayttymista #(= "yhdistetty" @yhdistetty-atom) "Tuck-remoting yhdistetty" 1000)
    ;; Kun yhteys on luotu, suljetaan se
    (sulje-ws-yhteys! :asiakas-1)

    ;; Testataan toimiiko yhteys poikki -hook
    (odota-ehdon-tayttymista #(= "katkaistu" @yhdistetty-atom) "Tuck-remoting yhteys katkaistu" 1000)

    ;; Siivoa
    (poista-yhdistaessa-hook)
    (poista-yhteys-poikki-hook)))

(deftest testaa-tuck-remoting
  (testing "Testaa vastaako tuck-remoting ping-viestiin"
    ;; Luo yhteys
    (luo-ws-yhteys! :asiakas-1)

    ;; Lähetä ping-viesti
    (ws/send (ws-yhteys :asiakas-1) (transit/clj->transit {:tuck.remoting/event-type :ping}))
    (odota-ehdon-tayttymista #(seq (ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

    ;; Testataan vastaako tuck-remoting pong-viestillä
    (is (= {:tuck.remoting/event-type :pong} (ws-vastaus :asiakas-1)))

    ;; Siivoa vastaus
    (siivoa-ws-vastaus! :asiakas-1)

    (sulje-kaikki-ws-yhteydet!))

  (testing "Lähetä kaikille clientille"
    ;; Luo kaksi yhteyttä
    (luo-ws-yhteys! :asiakas-1)
    (luo-ws-yhteys! :asiakas-2)

    ;; Lähetä testi-eventti kaikille clientille
    (sut/laheta-kaikille! (:tuck-remoting jarjestelma) (->TestiEventti))
    (odota-ehdon-tayttymista #(seq (ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus asiakkaalle 1" 1000)
    (odota-ehdon-tayttymista #(seq (ws-vastaus :asiakas-2)) "Tuck-remotingilta saatiin vastaus asiakkaalle 2" 1000)

    (is (= "harja.palvelin.komponentit.tuck-remoting-test.TestiEventti" (::tr/event-type (ws-vastaus :asiakas-1))))
    (is (= "harja.palvelin.komponentit.tuck-remoting-test.TestiEventti" (::tr/event-type (ws-vastaus :asiakas-2))))

    ;; Siivoa vastaus
    (siivoa-ws-vastaus! :asiakas-1)
    (siivoa-ws-vastaus! :asiakas-2)

    (sulje-kaikki-ws-yhteydet!)))
