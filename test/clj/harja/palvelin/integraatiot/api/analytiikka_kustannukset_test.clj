(ns harja.palvelin.integraatiot.api.analytiikka-kustannukset-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [cheshire.core :as cheshire]
    [com.stuartsierra.component :as component]
    [harja.pvm :as pvm]
    [harja.testi :refer :all]
    [harja.kyselyt.urakat :as urakat-kyselyt]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
    [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
    [harja.palvelin.palvelut.kulut.kulut :as kulut]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka false)
                       [:http-palvelin :db-replica :integraatioloki])))

(defn http-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture http-fixture)

(defn uusi-kulu-kustannukset-testiin [urakka-id]
  {:id nil
   :urakka urakka-id
   :viite "12345678"
   :erapaiva #inst "2024-08-01T21:00:00.000-00:00"
   :kokonaissumma 987654321
   :tyyppi "laskutettava"
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa 493827160.5
                   :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                   :tehtava nil}
                  {:kohdistus-id nil
                   :rivi 2
                   :summa 493827160.5
                   :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                   :tehtava nil}]
   :koontilaskun-kuukausi "elokuu/5-hoitovuosi"})

(deftest hae-toteutuneet-kustannukset-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Luodaan kulu, joka on pakko löytyä aineistosta
        uusi-kulu (uusi-kulu-kustannukset-testiin urakka-id)
        _ (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :kulu-kohdistuksineen uusi-kulu})

        kulut-kannasta (q-map
                         (format "SELECT u.id                        AS urakka,
                                                u.urakkanro                 AS urakkatunnus,
                                                k.id                        AS \"kulu-id\",
                                                k.laskun_numero             AS \"laskun-tunniste\",
                                                k.lisatieto                 AS \"kulun-kuvaus\",
                                                k.poistettu                 AS \"poistettu\",
                                                k.koontilaskun_kuukausi     AS \"koontilaskun-kuukausi\",
                                                k.erapaiva                  AS \"kulun-ajankohta_laskun-paivamaara\",
                                                k.kokonaissumma             AS \"kulun-kokonaissumma\"
                                           FROM kulu k
                                                JOIN kulu_kohdistus kk ON k.id = kk.kulu
                                                JOIN urakka u ON k.urakka = u.id
                                          WHERE u.id = %s
                                          GROUP BY k.id, u.id
                                          ORDER BY k.erapaiva ;" urakka-id))

        ;; Varmista, että kannasta löytyy juuri luotu kulu
        juuri-luotu-kulu-kannasta (first (filter #(= (:kulun-kokonaissumma %) 987654321M) kulut-kannasta))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteutuneet-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        juuri-luotu-kulu-rajapinnasta (first (filter (fn [k]
                                                       (= (get-in k [:kulu :kulun-kokonaissumma]) 987654321))
                                               (get-in encoodattu-body [:toteutuneet-kustannukset :kulut])))]
    (is (= 200 (:status vastaus)))
    (is (= (:kulun-kokonaissumma juuri-luotu-kulu-kannasta)
          (bigdec (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-kokonaissumma]))) "Tietokannan ja rajapinnan kulu ei täsmää.")
    (is (= 2024 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-vuosi])))
    (is (= 8 (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :koontilaskun-kuukausi])))
    (is (= "2024-08-01T21:00:00Z" (get-in juuri-luotu-kulu-rajapinnasta [:kulu :kulun-ajankohta :laskun-paivamaara])))
    (is (= (count kulut-kannasta) (count (get-in encoodattu-body [:toteutuneet-kustannukset :kulut]))))))
