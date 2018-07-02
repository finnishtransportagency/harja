(ns harja.palvelin.komponentit.todennus-test
  (:require [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root
   #'jarjestelma
   (fn [_]
     (component/start
      (component/system-map
       :db (tietokanta/luo-tietokanta testitietokanta)
       :todennus (component/using
                  (todennus/http-todennus nil)
                  [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(def testiroolit {"root" {:nimi "root"
                          :kuvaus "Pääkäyttäjä"}
                  "valvoja" {:nimi "valvoja"
                             :kuvaus "Urakan valvoja"
                             :linkki "urakka"}
                  "katsoja" {:nimi "katsoja"
                             :kuvaus "Katsoja"}
                  "urakoitsija" {:nimi "urakoitsija"
                                 :kuvaus "Urakoitsijan käyttäjä"
                                 :linkki "urakoitsija"}
                  "paivystaja" {:nimi "paivystaja"
                                :kuvaus "Urakan päivystäjä"
                                :linkki "urakka"}
                  "Kayttaja" {:nimi "Kayttaja"
                              :kuvaus "Urakoitsijan käyttäjä"
                              :linkki "urakoitsija"}})

(def urakat {"u123" 666})
(def urakoitsijat {"Y123456-7" 42})
(def urakat-monta {"PR00013343" 13343 "PR00014303" 14303})

(def oikeudet (partial todennus/kayttajan-roolit urakat urakoitsijat testiroolit))

(deftest lue-oikeudet-oam-groupsista

  (is (= {:roolit #{"root"} :urakkaroolit {} :organisaatioroolit {}}
         (oikeudet "root")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"valvoja"}} :organisaatioroolit {}}
         (oikeudet "u123_valvoja")))

  (is (= {:roolit #{} :urakkaroolit {666 #{"paivystaja"}}
          :organisaatioroolit {42 #{"urakoitsija"}}}
         (oikeudet "Y123456-7_urakoitsija,u123_paivystaja"))))

(deftest liito-rooli-ei-sekoitu-harja-rooliin
  (is (= {:roolit #{} :urakkaroolit {666 #{"paivystaja"}}
          :organisaatioroolit {42 #{"urakoitsija"}}}
         (oikeudet "Y123456-7_urakoitsija,u123_paivystaja,Extranet_Liito_Kayttaja,Aina_öisin_valvoja"))))

(deftest tilaajan-kayttaja
  (is (= {:roolit             #{"Tilaajan_Kayttaja"}
          :organisaatioroolit {}
          :urakkaroolit       {}}
         (todennus/kayttajan-roolit urakat urakoitsijat oikeudet/roolit "Tilaajan_Kayttaja"))))

(deftest ely-peruskayttaja
  (is (= {:roolit             #{"ELY_Peruskayttaja"}
          :organisaatioroolit {}
          :urakkaroolit       {}}
         (todennus/kayttajan-roolit urakat urakoitsijat oikeudet/roolit "ELY_Peruskayttaja"))))

(deftest ely-urakanvalvoja
  (let [oam-groups "55746,39626,39627,27231,28875,30138,49544,29957,30006,44687,56406,PR00014281_ELY_Urakanvalvoja,PR00013343_ELY_Urakanvalvoja,28311,55550,PR00014258_ELY_Urakanvalvoja,26871,PR00014303_ELY_Urakanvalvoja,51204,49468,49469,28852,30050,30116,28851,PR00014273_ELY_Urakanvalvoja,32065,44594,31866,51805,51804,PR00014248_ELY_Urakanvalvoja,Extranet_Liito_Kayttaja,29778,44556,PR00013356_ELY_Urakanvalvoja,Extranet_Aura_Kayttaja,47074,47075,thuv,51544,51685,51684,55530,54206,56626,PR00014296_ELY_Urakanvalvoja,53865,29726,53864,r,56426,PR00014289_ELY_Urakanvalvoja,PR00014265_ELY_Urakanvalvoja"
        vastaus (todennus/kayttajan-roolit urakat-monta urakoitsijat oikeudet/roolit oam-groups)
        odotetut-roolit {:roolit             #{}
                         :organisaatioroolit {}
                         :urakkaroolit       {14303 #{"ELY_Urakanvalvoja"}
                                              13343 #{"ELY_Urakanvalvoja"}}}]
    (is (= vastaus odotetut-roolit))))

(deftest ota-organisaatio-roolin-y-tunnuksesta
  (let [todenna #(todennus/todenna-pyynto (:todennus jarjestelma) %)
        destia-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'")))
        lampunvaihtajat-id (first (first (q "SELECT id FROM organisaatio WHERE ytunnus = '2234567-8'")))]
    (testing "Organisaatio löytyy, jos OAM_ORGANIZATION on annettu oikein"
      (let [req (todenna {:headers {"oam_remote_user" "daniel"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_user_mail" "daniel@example.com"
                                    "oam_user_mobile" "1234567890"
                                    "oam_organization" "Destia Oy"
                                    "oam_groups" ""}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))

    (testing "Jos muuta organisaatiotietoa ei löyty, yritä ottaa se roolin Y-tunnuksesta"
      (let [req (todennus/todenna-pyynto (:todennus jarjestelma)
                                         {:headers {"oam_remote_user" "alpo"
                                                    "oam_user_first_name" "Alpo"
                                                    "oam_user_last_name" "Asfalttimies"
                                                    "oam_user_mail" "alpo@example.com"
                                                    "oam_user_mobile" "1234567890"
                                                    "oam_organization" "Eitällaistaolekaan Oy"
                                                    "oam_groups" "2234567-8_Paakayttaja"}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) lampunvaihtajat-id))))))

(deftest ota-organisaatio-companyid-headerista
  (let [todenna #(todennus/todenna-pyynto (:todennus jarjestelma) %)
        destia-id (first (first (q "SELECT id FROM organisaatio WHERE nimi = 'Destia Oy'")))]
    (testing "Organisaatio löytyy, jos OAM_USER_COMPANYID on annettu oikein vaikka nimi olisi väärä"
      (let [req (todenna {:headers {"oam_remote_user" "daniel"
                                    "oam_user_first_name" "Daniel"
                                    "oam_user_last_name" "Destialainen"
                                    "oam_user_mail" "daniel@example.com"
                                    "oam_user_mobile" "1234567890"
                                    "oam_organization" "Dezdia Oy"
                                    "oam_user_companyid" "2163026-3"
                                    "oam_groups" ""}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))

    (testing "Organisaatio löytyy edelleen nimen perusteella, jos OAM_USER_COMPANYID:ssä on roskaa"
      (let [req (todennus/todenna-pyynto (:todennus jarjestelma)
                                         {:headers {"oam_remote_user" "alpo"
                                                    "oam_user_first_name" "Alpo"
                                                    "oam_user_last_name" "Asfalttimies"
                                                    "oam_user_mail" "alpo@example.com"
                                                    "oam_user_mobile" "1234567890"
                                                    "oam_organization" "Destia oy"
                                                    "oam_user_companyid" "NOT_FOUND"
                                                    "oam_groups" ""}})]
        (is (= (get-in req [:kayttaja :organisaatio :id]) destia-id))))))
