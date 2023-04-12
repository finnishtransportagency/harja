(ns harja.palvelin.komponentit.tuck-remoting.tuck-remoting-test
  (:require
    [com.stuartsierra.component :as component]
    [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
    [harja.testi :refer :all]
    [java-http-clj.websocket :as ws]
    [harja.oikea-http-palvelin-jarjestelma-fixture :as oikea-palvelin-fixture]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as sut]
    [tuck.remoting.transit :as transit]
    [harja.palvelin.komponentit.tuck-remoting.tyokalut :as tr-tyokalut]))

(defrecord TestiEventti [])
(tr/define-client-event TestiEventti)

(def jarjestelma-fixture (oikea-palvelin-fixture/luo-fixture
                           :tuck-remoting (component/using
                                            (sut/luo-tuck-remoting)
                                            [:http-palvelin :db])))

(use-fixtures :each (compose-fixtures jarjestelma-fixture tr-tyokalut/websocket-fixture))


(def jvh-kayttajan-oam-headerit {"oam_remote_user" "jvh"
                                 "oam_user_first_name" "Jalmari"
                                 "oam_user_last_name" "Järjestelmävastuuhenkilö"
                                 "oam_user_mail" "erkki@esimerkki.com"
                                 "oam_user_mobile" "1234567890"
                                 "oam_organization" "Liikennevirasto"
                                 "oam_groups" "Jarjestelmavastaava"})


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
        _ (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)]

    ;; Testataan toimiiko yhteyden luominen ja hook
    (odota-ehdon-tayttymista #(= "yhdistetty" @yhdistetty-atom) "Tuck-remoting yhdistetty" 1000)
    ;; Kun yhteys on luotu, suljetaan se
    (tr-tyokalut/sulje-ws-yhteys! :asiakas-1)

    ;; Testataan toimiiko yhteys poikki -hook
    (odota-ehdon-tayttymista #(= "katkaistu" @yhdistetty-atom) "Tuck-remoting yhteys katkaistu" 1000)

    ;; Siivoa
    (poista-yhdistaessa-hook)
    (poista-yhteys-poikki-hook)))

(deftest testaa-tuck-remoting
  (testing "Testaa vastaako tuck-remoting ping-viestiin"
    ;; Luo yhteys
    (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

    ;; Lähetä ping-viesti
    (ws/send (tr-tyokalut/ws-yhteys :asiakas-1) (transit/clj->transit {:tuck.remoting/event-type :ping}))
    (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

    ;; Testataan vastaako tuck-remoting pong-viestillä
    (is (= {:tuck.remoting/event-type :pong} (tr-tyokalut/ws-vastaus :asiakas-1)))

    ;; Siivoa vastaus
    (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

    (tr-tyokalut/sulje-kaikki-ws-yhteydet!))

  (testing "Lähetä kaikille clientille"
    ;; Testaa viestien lähetystä isommalle asiakasjoukolle

    (let [asiakkaat-lkm 40]
      (doseq [id (range asiakkaat-lkm)]
        (tr-tyokalut/luo-ws-yhteys! id jvh-kayttajan-oam-headerit))

      ;; Lähetä testi-eventti kaikille clientille
      (sut/laheta-kaikille! (:tuck-remoting jarjestelma) (->TestiEventti))

      (doseq [id (range asiakkaat-lkm)]
        (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus id)) (str "Tuck-remotingilta saatiin vastaus asiakkaalle " id) 2000))

      (doseq [id (range asiakkaat-lkm)]
        (is (= "harja.palvelin.komponentit.tuck-remoting.tuck-remoting-test.TestiEventti"
              (::tr/event-type (tr-tyokalut/ws-vastaus id)))))

      ;; Siivoa vastaukset
      (doseq [id (range asiakkaat-lkm)]
        (tr-tyokalut/siivoa-ws-vastaus! id))

      (tr-tyokalut/sulje-kaikki-ws-yhteydet!))))
