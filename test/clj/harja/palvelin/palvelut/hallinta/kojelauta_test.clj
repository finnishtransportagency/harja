(ns harja.palvelin.palvelut.hallinta.kojelauta-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.kojelauta :as kojelauta]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja
             [testi :refer :all]]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kojelauta-hallinta (component/using
                                (kojelauta/->KojelautaHallinta)
                                [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :ely-id nil})]
    (is (every? #(integer? (:id %)) vastaus))
    (is (every? #(string? (:nimi %)) vastaus))
    (is (every? #(integer? (:hoitokauden_alkuvuosi %)) vastaus))
    (is (every? #(integer? (:ely_id %)) vastaus))
    (is (every? #(map? (:ks_tila %)) vastaus))
    (is (= 10 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024-vajaa-kayttooikeus-throwaa
  ;; Kojelauta tässä vaiheessa vain pääkäyttäjälle, urakanvalvojalle ei näytetä
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           +kayttaja-tero+
                           {:hoitokauden-alkuvuosi 2024
                            :urakka-idt nil
                            :ely-id nil})) "Ei oikeutta poikkeus heitetään")

  ;; Kojelauta tässä vaiheessa vain pääkäyttäjälle, urakoitsijalle ei näytetä
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           +kayttaja-urakan-vastuuhenkilo+
                           {:hoitokauden-alkuvuosi 2015
                            :urakka-idt nil
                            :ely-id nil})) "Ei oikeutta poikkeus heitetään")
  )

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2005-ei-palauta-yhtaan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2005
                                                          :urakka-idt nil
                                                          :ely-id nil})]

    (is (= 0 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-pop-elyn-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [pop-ely-id @pohjois-pohjanmaan-hallintayksikon-id
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :ely-id pop-ely-id})]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (str/includes? vastaus "Raahen MHU") "Iin MHU")
    (is (str/includes? vastaus "MHU Suomussalmi") "Iin MHU")
    (is (str/includes? vastaus "MHU Kajaani") "Kajaanin MHU")

    (is (= 4 (count vastaus)) "Urakoiden lukumäärä")))

(deftest vain-iin-mhu-kojelautaan-hk-alkuvuosi-2024
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :ely-id nil})]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))
