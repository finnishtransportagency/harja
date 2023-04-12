(ns harja.palvelin.palvelut.tuck-remoting.ilmoitukset_test
  (:require
    [com.stuartsierra.component :as component]
    [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
    [harja.testi :refer :all]
    [harja.oikea-http-palvelin-jarjestelma-fixture :as oikea-palvelin-fixture]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tr-komponentti]
    [harja.palvelin.palvelut.tuck-remoting.ilmoitukset :as sut]
    [harja.tuck-remoting.ilmoitukset-eventit :as ilmoitukset-eventit]
    [harja.palvelin.komponentit.tuck-remoting.tyokalut :as tr-tyokalut]))

(def jarjestelma-fixture (oikea-palvelin-fixture/luo-fixture
                           :tuck-remoting (component/using
                                            (tr-komponentti/luo-tuck-remoting)
                                            [:http-palvelin :db])
                           :ilmoitukset-ws-palvelu (component/using
                                                     (sut/luo-ilmoitukset-ws)
                                                     [:tuck-remoting :db])))

(use-fixtures :each (compose-fixtures jarjestelma-fixture tr-tyokalut/websocket-fixture))

(def jvh-kayttajan-oam-headerit {"oam_remote_user" "jvh"
                                 "oam_user_first_name" "Jalmari"
                                 "oam_user_last_name" "Järjestelmävastuuhenkilö"
                                 "oam_user_mail" "erkki@esimerkki.com"
                                 "oam_user_mobile" "1234567890"
                                 "oam_organization" "Liikennevirasto"
                                 "oam_groups" "Jarjestelmavastaava"})


(deftest testaa-ilmoitusten-kuuntelu
  (testing "Testaa onnistuuko urakkakohtaisten kuuntelun aloittaminen"
    ;; Luo yhteys
    (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (tr-tyokalut/asiakas-e! (tr-tyokalut/ws-yhteys :asiakas-1))]

      ;; Aloita urakan 35 ilmoitusten kuuntelija
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakka 35}))
      (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

      ;; Siivoa vastaus
      (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

      (tr-tyokalut/sulje-kaikki-ws-yhteydet!)))

  (testing "Testaa onnistuuko kaikkien ilmoitusten kuuntelun aloittaminen (suodatettu kuuntelija)"
    ;; Luo yhteys
    (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (tr-tyokalut/asiakas-e! (tr-tyokalut/ws-yhteys :asiakas-1))]

      ;; Aloita kuuntelu
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakkatyyppi :kaikki :hallintayksikko 12}))
      (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" 1000)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

      ;; Siivoa vastaus
      (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

      (tr-tyokalut/sulje-kaikki-ws-yhteydet!))))
