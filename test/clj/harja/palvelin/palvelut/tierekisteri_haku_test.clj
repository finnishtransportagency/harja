(ns harja.palvelin.palvelut.tierekisteri-haku-test
  (:require [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tienakyma :as tienakyma]
            [slingshot.slingshot :refer [try+]]
            [harja.paneeliapurit :as paneeli]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku])
  (:import [harja.domain.roolit EiOikeutta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tienakyma (component/using
                                     (tierekisteri-haku/->TierekisteriHaku)
                                     [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(def tienumero 6666)

(defn luo-tr-osoite [[osa a-et l-et kaista]]
  (u (str
       "INSERT INTO tr_osoitteet
        (\"tr-numero\", \"tr-ajorata\", \"tr-kaista\", \"tr-osa\",  \"tr-alkuetaisyys\", \"tr-loppuetaisyys\", tietyyppi)
        VALUES (" tienumero ", 1, " kaista ", " osa ", " a-et ", " l-et ", 1)")))

(defn luo-tr-osoitteet [osoitteet]
  (u (str "DELETE FROM tr_osoitteet WHERE \"tr-numero\" = " tienumero))
  (doseq [osoite osoitteet]
    (luo-tr-osoite osoite))
  (u "REFRESH MATERIALIZED VIEW tr_tiedot"))

(defn- kutsu
  ([kayttaja payload] (kutsu :hae-tr-tiedot kayttaja payload))
  ([palvelu kayttaja payload]
   (kutsu-palvelua (:http-palvelin jarjestelma) palvelu kayttaja payload)))

(defn parametrit
  [a b c]
  {:tr-numero a
   :tr-alkuosa b
   :tr-loppuosa c})

(deftest hae-tr-tieosuudet-koko-tie
  (luo-tr-osoitteet [[1 100 500 11]])
  (is (= [{:tr-numero tienumero
           :tr-ajorata 1
           :tr-kaista 11
           :tr-alkuosa 1
           :tr-alkuetaisyys 100
           :tr-loppuosa 1
           :tr-loppuetaisyys 500}]
         (kutsu :hae-tr-tieosuudet +kayttaja-jvh+ {:tr-numero tienumero}))))

(deftest hae-tr-tieosuudet-rajauksella
  (luo-tr-osoitteet [[1 100 500 11]
                     [1 500 1000 11]
                     [2 0 600 11]])
  (let [hae (fn [rajaus]
              (kutsu :hae-tr-tieosuudet +kayttaja-jvh+
                     (assoc rajaus :tr-numero tienumero)))
        koko-tie [{:tr-numero tienumero :tr-ajorata 1 :tr-kaista 11
                   :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 2 :tr-loppuetaisyys 600}]]
    (testing "vierekkäiset osuudet yhdistyvät myös osarajan yli"
      (is (= koko-tie (hae {}))))
    (testing "pelkkä alkupään rajaus"
      (is (= [(assoc (first koko-tie) :tr-alkuetaisyys 300)]
             (hae {:tr-alkuosa 1 :tr-alkuetaisyys 300})))
      (is (= koko-tie (hae {:tr-alkuosa 1}))))
    (testing "pelkkä loppupään rajaus"
      (is (= [(assoc (first koko-tie) :tr-loppuosa 1 :tr-loppuetaisyys 700)]
             (hae {:tr-loppuosa 1 :tr-loppuetaisyys 700}))))
    (testing "molemmat rajat ja puuttuvan etäisyyden oletusarvo"
      (is (= [(assoc (first koko-tie) :tr-alkuetaisyys 300
                     :tr-loppuosa 1 :tr-loppuetaisyys 700)]
             (hae {:tr-alkuosa 1 :tr-alkuetaisyys 300
                   :tr-loppuosa 1 :tr-loppuetaisyys 700})))
      (is (= [(assoc (first koko-tie) :tr-loppuosa 1 :tr-loppuetaisyys 1000)]
             (hae {:tr-loppuosa 1}))))
    (testing "tuntemattomalla tiellä ei ole tieosuuksia"
      (is (= [] (kutsu :hae-tr-tieosuudet +kayttaja-jvh+ {:tr-numero 99999999}))))))

(deftest hae-tr-tieosuudet-jarjestys-ja-raot
  (luo-tr-osoitteet [[1 0 100 21]
                     [1 200 300 21]
                     [1 0 100 11]
                     [2 0 100 11]])
  (u (str "INSERT INTO tr_osoitteet
           (\"tr-numero\", \"tr-ajorata\", \"tr-kaista\", \"tr-osa\", \"tr-alkuetaisyys\", \"tr-loppuetaisyys\", tietyyppi)
           VALUES (" tienumero ", 2, 11, 1, 0, 100, 1)"))
  (let [tulos (kutsu :hae-tr-tieosuudet +kayttaja-jvh+ {:tr-numero tienumero})]
      (is (= [[1 0 1 11 2 100]
        [1 0 1 21 1 100]
        [1 0 2 11 1 100]
            [1 200 1 21 1 300]]
           (mapv (juxt :tr-alkuosa :tr-alkuetaisyys :tr-ajorata :tr-kaista
                       :tr-loppuosa :tr-loppuetaisyys) tulos)))
    (is (every? #(= #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa
                       :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}
                    (set (keys %))) tulos))))

(deftest hae-tr-tieosuudet-virheelliset-rajat
  (doseq [rajaus [{:tr-alkuetaisyys 1}
                 {:tr-loppuetaisyys 1}
                 {:tr-alkuosa 3 :tr-loppuosa 2}
                 {:tr-alkuosa 1 :tr-alkuetaisyys 20
                  :tr-loppuosa 1 :tr-loppuetaisyys 10}
                 {:tr-alkuosa 1 :tr-alkuetaisyys 100
                  :tr-loppuosa 1 :tr-loppuetaisyys 100}
                 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-loppuetaisyys 0}
                 {:tr-alkuosa -1}]]
    (is (thrown? Exception
                 (kutsu :hae-tr-tieosuudet +kayttaja-jvh+
                        (assoc rajaus :tr-numero tienumero)))))
  (is (thrown? Exception
               (kutsu :hae-tr-tieosuudet +kayttaja-jvh+ {}))))

(deftest hae-osien-tiedot-suoraan
  (luo-tr-osoitteet [[1 0 100 11]
                     [1 100 200 11]
                     [1 0 200 12]
                     [1 300 400 11]])
  (let [tulos (tierekisteri-haku/hae-osien-tiedot
                (:db jarjestelma)
                {:tr-numero tienumero :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 1})]
    (is (= [{:tr-numero tienumero
             :tr-osa 1
             :pituudet {:pituus 300
                        :tr-alkuetaisyys 0
                        :ajoradat [{:tr-ajorata 1
                                    :osiot [{:pituus 200 :tr-alkuetaisyys 0
                                             :kaistat [{:pituus 200 :tr-kaista 11 :tr-alkuetaisyys 0}
                                                       {:pituus 200 :tr-kaista 12 :tr-alkuetaisyys 0}]}
                                            {:pituus 100 :tr-alkuetaisyys 300
                                             :kaistat [{:pituus 100 :tr-kaista 11 :tr-alkuetaisyys 300}]}]}]}}]
           tulos))))

(deftest sama-kaista-ja-rako
  (luo-tr-osoitteet [[1 0 1500 11]
                     [1 1500 2500 11]
                     [1 4500 5000 11]])
  (let [odotettu-arvo [{:tr-numero 6666,
                        :tr-osa 1,
                        :pituudet {:pituus 3000,
                                   :tr-alkuetaisyys 0,
                                   :ajoradat [{:osiot [{:pituus 2500,
                                                        :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                                                        :tr-alkuetaisyys 0}
                                                       {:pituus 500,
                                                        :kaistat [{:pituus 500, :tr-kaista 11, :tr-alkuetaisyys 4500}],
                                                        :tr-alkuetaisyys 4500}],
                                               :tr-ajorata 1}]}}]
        tulos (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tr-tiedot +kayttaja-jvh+ (parametrit tienumero 1 1))]
    (is (= odotettu-arvo tulos) "Kaistat kenttä on yhdistetty vaikka on tr_osoitteet taulukossa kaksi riviä")))

(deftest ajoratakilometrien-laskenta
  (let [db (:db jarjestelma)
        tieosoite-osa-4-0-218 {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 0 :tr-loppuosa 4 :tr-loppuetaisyys 218}
        ;; kohdassa 0-318 ensimmäiset 218m on kaksirajotaista, loput 1 ajorataista
        ajorata-kilometrit-4-0-218-odotettu (* 2 218) ;; 436
        tulos-osa-4-0-218 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-0-218)
        tieosoite-osa-4-0-218-kaannetty {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 218 :tr-loppuosa 4 :tr-loppuetaisyys 0}
        ;; kohdassa 0-318 ensimmäiset 218m on kaksirajotaista, loput 1 ajorataista
        tulos-osa-4-0-218-kaannetty (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-0-218-kaannetty)

        tieosoite-osa-4-0-318 {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 0 :tr-loppuosa 4 :tr-loppuetaisyys 318}
        ajorata-kilometrit-0-318-odotettu (+ (* 2 218) 100) ;; 536
        tulos-osa-4-0-318 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-0-318)

        tieosoite-osa-4-200-318 {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 200 :tr-loppuosa 4 :tr-loppuetaisyys 318}
        ajorata-kilometrit-osa-4-200-318 136         ;; 18m ensin 2 ajorataista, 100m yksiajorataista
        tulos-osa-4-200-318 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-200-318)

        tieosoite-osa-4-0-1218 {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 0 :tr-loppuosa 4 :tr-loppuetaisyys 1218}
        ajorata-kilometrit-4-0-1218-odotettu 1804
        tulos-osa-4-0-1218 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-0-1218)

        tieosoite-osa-3-5 {:tr-numero 20 :tr-alkuosa 3, :tr-alkuetaisyys 0 :tr-loppuosa 5 :tr-loppuetaisyys 318}
        ajorata-kilometrit-osa-3-5-odotettu 19405
        tulos-osa-3-5 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-3-5)

        tieosoite-osa-3-4 {:tr-numero 20 :tr-alkuosa 3, :tr-alkuetaisyys 0 :tr-loppuosa 4 :tr-loppuetaisyys 318}
        ajorata-kilometrit-osa-3-4-odotettu 12200
        tulos-osa-3-4 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-3-4)

        tieosoite-osa-4-5 {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 0 :tr-loppuosa 5 :tr-loppuetaisyys 318}
        ajorata-kilometrit-osa-4-5-odotettu 7741
        tulos-osa-4-5 (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-5)

        tieosoite-osa-4-5-lyhyt {:tr-numero 20 :tr-alkuosa 4, :tr-alkuetaisyys 0 :tr-loppuosa 5 :tr-loppuetaisyys 317}
        ajorata-kilometrit-osa-4-5-lyhyt-odotettu 7740
        tulos-osa-4-5-lyhyt (tierekisteri-haku/tieosoitteen-ajoratakilometrit db tieosoite-osa-4-5-lyhyt)

        sata-metria-kahta-ajorataa {:tr-numero 20 :tr-alkuosa 4 :tr-alkuetaisyys 4000 :tr-loppuosa 4 :tr-loppuetaisyys 4100}
        sata-metria-kahta-ajorataa-odotettu 200
        tulos-sata-metria-kahta-ajorataa (tierekisteri-haku/tieosoitteen-ajoratakilometrit db sata-metria-kahta-ajorataa)

        yksi-ajoratainen-kaksi-kaistaa {:tr-numero 20 :tr-alkuosa 33 :tr-alkuetaisyys 0 :tr-loppuosa 33 :tr-loppuetaisyys 8757}
        yksi-ajoratainen-kaksi-kaistaa-odotettu 8757
        tulos-yksi-ajoratainen-kaksi-kaistaa (tierekisteri-haku/tieosoitteen-ajoratakilometrit db yksi-ajoratainen-kaksi-kaistaa)]
    (is (= ajorata-kilometrit-4-0-218-odotettu tulos-osa-4-0-218))
    (is (= ajorata-kilometrit-4-0-218-odotettu tulos-osa-4-0-218-kaannetty))
    (is (= ajorata-kilometrit-0-318-odotettu tulos-osa-4-0-318))
    (is (= ajorata-kilometrit-osa-4-200-318 tulos-osa-4-200-318))
    (is (= ajorata-kilometrit-4-0-1218-odotettu tulos-osa-4-0-1218))
    (is (= ajorata-kilometrit-osa-3-5-odotettu tulos-osa-3-5))
    (is (= ajorata-kilometrit-osa-3-4-odotettu tulos-osa-3-4))
    (is (= ajorata-kilometrit-osa-4-5-odotettu tulos-osa-4-5))
    (is (= ajorata-kilometrit-osa-4-5-lyhyt-odotettu tulos-osa-4-5-lyhyt))
    (is (= sata-metria-kahta-ajorataa-odotettu tulos-sata-metria-kahta-ajorataa))
    (is (= yksi-ajoratainen-kaksi-kaistaa-odotettu tulos-yksi-ajoratainen-kaksi-kaistaa))))
