(ns ^:integraatio harja.palvelin.palvelut.tuck-remoting.ilmoitukset_test
  (:require
    [clojure.test :as t :refer [deftest is use-fixtures compose-fixtures testing]]
    [harja.testi :refer :all]
    [clojure.core.async :as async]
    [com.stuartsierra.component :as component]
    [harja.integraatio :as integraatio]
    [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
    [harja.palvelin.integraatiot.jms :as jms]
    [harja.palvelin.integraatiot.jms.tyokalut :as jms-tk]
    [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
    [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
    [harja.palvelin.komponentit.itmf :as itmf]
    [tuck.remoting :as tr]
    [harja.palvelin.komponentit.tuck-remoting :as tr-komponentti]
    [harja.palvelin.palvelut.tuck-remoting.ilmoitukset :as sut]
    [harja.tuck-remoting.ilmoitukset-eventit :as ilmoitukset-eventit]
    [harja.palvelin.komponentit.tuck-remoting.tyokalut :as tr-tyokalut]
    [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tyokalut]))

(defonce asetukset {:itmf integraatio/itmf-asetukset})
(def jms-timeout 2000)
(def itmf-kuittaus-timeout 20000)
(def ws-vastaus-timeout 2000)

(def ilmoitukset-kayttaja "yit-rakennus")
(def jvh-kayttajan-oam-headerit {"oam_remote_user" "jvh"
                                 "oam_user_first_name" "Jalmari"
                                 "oam_user_last_name" "Järjestelmävastuuhenkilö"
                                 "oam_user_mail" "erkki@esimerkki.com"
                                 "oam_user_mobile" "1234567890"
                                 "oam_organization" "Liikennevirasto"
                                 "oam_groups" "Jarjestelmavastaava"})

(def jarjestelma-fixture (laajenna-integraatiojarjestelmafixturea
                           ilmoitukset-kayttaja
                           :api-ilmoitukset (component/using
                                              (api-ilmoitukset/->Ilmoitukset)
                                              [:http-palvelin :db :integraatioloki])
                           :itmf (component/using
                                   (itmf/luo-oikea-itmf (:itmf asetukset))
                                   [:db])
                           :api-sahkoposti (component/using
                                             (sahkoposti-api/->ApiSahkoposti {:tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                                             [:http-palvelin :db :integraatioloki :itmf])
                           :labyrintti (component/using
                                         (labyrintti/->Labyrintti "foo" "testi" "testi" (atom #{}))
                                         [:db :http-palvelin :integraatioloki])
                           :tloik (component/using
                                    (tloik-tyokalut/luo-tloik-komponentti)
                                    [:db :itmf :integraatioloki :labyrintti :api-sahkoposti])
                           :tuck-remoting (component/using
                                            (tr-komponentti/luo-tuck-remoting)
                                            [:http-palvelin :db])
                           :ilmoitukset-ws-palvelu (component/using
                                                     (sut/luo-ilmoitukset-ws)
                                                     [:tuck-remoting :db])))

(use-fixtures :each (fn [testit]
                      (binding [*aloitettavat-jmst* #{"itmf"}
                                *lisattavia-kuuntelijoita?* true
                                *jms-kaynnistetty-fn* (fn []
                                                        (jms-tk/itmf-jolokia-jono tloik-tyokalut/+tloik-ilmoitusviestijono+ nil :purge)
                                                        (jms-tk/itmf-jolokia-jono tloik-tyokalut/+tloik-ilmoituskuittausjono+ nil :purge))]
                        (let [fixture (compose-fixtures jarjestelma-fixture tr-tyokalut/websocket-fixture)]
                          (fixture testit)))))


(comment
  (deftest ilmoitusten-kuuntelu-ja-kuuntelun-lopetus
    (testing "Testaa onnistuuko kuuntelun aloitus ja lopettaminen"
      (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

      ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
      (let [e! (tr-tyokalut/asiakas-e! (tr-tyokalut/ws-yhteys :asiakas-1))
            urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")]

        ;; Aloita  ilmoitusten kuuntelija
        (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakka urakka-id}))
        (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" ws-vastaus-timeout)

        ;; Testataan onnistuiko kuuntelijan aloittaminen
        (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
              (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

        ;; Siivoa ws-vastaus
        (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

        ;; Lopeta kuuntelu
        (e! (ilmoitukset-eventit/->LopetaIlmoitustenKuuntelu))
        (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" ws-vastaus-timeout)

        ;; Testataan onnistuiko kuuntelijan aloittaminen
        #_(is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
                (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

        (tr-tyokalut/sulje-kaikki-ws-yhteydet!)))))

(deftest urakan-ilmoitusten-kuuntelu
  (testing "Testaa onnistuuko urakkakohtaisten kuuntelun aloittaminen ja urakkaan lähetetyn ilmoituksen vastaanotto"
    ;; Luo yhteys
    (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (tr-tyokalut/asiakas-e! (tr-tyokalut/ws-yhteys :asiakas-1))
          urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")]

      ;; Aloita  ilmoitusten kuuntelija
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakka urakka-id}))
      (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" ws-vastaus-timeout)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

      ;; Siivoa ws-vastaus
      (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

      ;; Lähetetään uusi testi-ilmoitus ITMF-jonoon
      (let [kuittausviestit-tloikkiin (atom nil)]
        (lisaa-kuuntelijoita! {"itmf" {tloik-tyokalut/+tloik-ilmoituskuittausjono+ #(swap! kuittausviestit-tloikkiin conj (.getText %))}})

        (async/<!! (async/timeout jms-timeout))
        (jms/laheta (:itmf jarjestelma) tloik-tyokalut/+tloik-ilmoitusviestijono+ (tloik-tyokalut/testi-ilmoitus-sanoma))

        ;; Odotellaan, että ilmoitus saadaan käsiteltyä ja kuitattua
        (odota-ehdon-tayttymista #(= 1 (count @kuittausviestit-tloikkiin)) "Kuittaus on vastaanotettu." itmf-kuittaus-timeout)

        ;; Odotetaan vastausta ws-ilmoitusten omalta ilmoitus-kuuntelijalta
        (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" ws-vastaus-timeout)

        ;; Testataan tuliko ws-ilmoituksista Ilmoitus-eventin mukana oikea vastaanotetun ilmoituksen ilmoitus-id
        (is (= "harja.tuck-remoting.ilmoitukset-eventit.Ilmoitus"
              (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

        (is (= {:ilmoitus {:ilmoitus-id 123456789}}
              (::tr/event-args (tr-tyokalut/ws-vastaus :asiakas-1))))

        ;; Siivoa ws-vastaus
        (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

        ;; Siivotaan pois testi-ilmoitus
        (tloik-tyokalut/poista-ilmoitus 123456789))

      (tr-tyokalut/sulje-kaikki-ws-yhteydet!))))

(deftest suodatettujen-ilmoitusten-kuuntelu
  (testing "Testaa onnistuuko kaikkien ilmoitusten kuuntelun aloittaminen (suodatettu kuuntelija)"
    ;; Luo yhteys
    (tr-tyokalut/luo-ws-yhteys! :asiakas-1 jvh-kayttajan-oam-headerit)

    ;; Muodosta yhteydelle asiakas-e! apuri, jolla Tuck-eventtejä lähetetään, kuten selaimen ws-client lähettäisi
    (let [e! (tr-tyokalut/asiakas-e! (tr-tyokalut/ws-yhteys :asiakas-1))]

      ;; Aloita kuuntelu
      (e! (ilmoitukset-eventit/->KuunteleIlmoituksia {:urakkatyyppi :kaikki :hallintayksikko 12}))
      (odota-ehdon-tayttymista #(seq (tr-tyokalut/ws-vastaus :asiakas-1)) "Tuck-remotingilta saatiin vastaus" ws-vastaus-timeout)

      ;; Testataan onnistuiko kuuntelijan aloittaminen
      (is (= "harja.tuck-remoting.ilmoitukset-eventit.IlmoitustenKuunteluOnnistui"
            (::tr/event-type (tr-tyokalut/ws-vastaus :asiakas-1))))

      ;; Siivoa vastaus
      (tr-tyokalut/siivoa-ws-vastaus! :asiakas-1)

      (tr-tyokalut/sulje-kaikki-ws-yhteydet!))))
