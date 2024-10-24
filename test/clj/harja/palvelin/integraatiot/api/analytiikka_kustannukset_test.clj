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
                   :tehtava nil
                   :tyyppi :hankintakulu
                   :tavoitehintainen :true}
                  {:kohdistus-id nil
                   :rivi 2
                   :summa 493827160.5
                   :toimenpideinstanssi (hae-toimenpideinstanssi-id urakka-id "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "Vesakonraivaukset ja puun poisto (V)")
                   :tehtava nil
                   :tyyppi :hankintakulu
                   :tavoitehintainen :true}]
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


(deftest hae-kustannussuunnitelma-onnistuu-test
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Kiinteät kustannukset kannasta
        kiinteat-kulut-kannasta (:summa (first (q-map
                                                 (format "SELECT SUM(kit.summa) as summa
                                             FROM kiinteahintainen_tyo kit
                                            WHERE kit.sopimus =  (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

        ;; Arvioidut kustannukset kannasta
        arvioidut-kulut-kannasta (:summa (first (q-map
                                                  (format "SELECT SUM(kt.summa) as summa
                                              FROM kustannusarvioitu_tyo kt
                                             WHERE kt.sopimus = (SELECT id FROM sopimus WHERE urakka =  %s);" urakka-id))))

        ;; Johto-ja-hallintokorvaukset kannasta
        johto-ja-hallintokulut-kannasta (:summa (first (q-map
                                                         (format "SELECT SUM(jjh.tuntipalkka * jjh.tunnit) as summa
                                                     FROM johto_ja_hallintokorvaus jjh
                                                    WHERE jjh.\"urakka-id\" = %s;" urakka-id))))

        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-kustannukset/" urakka-id)] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        kiinteat-kulut-rajapinnasta (apply + (map #(get-in % [:kustannus :summa])
                                               (get-in encoodattu-body [:suunnitellut-kustannukset :kiinteat-kustannukset])))
        arvioidut-kulut-rajapinnasta (apply + (map #(get-in % [:kustannus :summa])
                                                (get-in encoodattu-body [:suunnitellut-kustannukset :arvioidut-kustannukset])))
        johto-ja-hallintokorvaukset-rajapinnasta (apply + (map #(get-in % [:toimenkuvan-kustannus :summa])
                                                            (get-in encoodattu-body [:suunnitellut-kustannukset :johto-ja-hallintokorvaukset])))]

    (is (= 200 (:status vastaus)))
    (is (= kiinteat-kulut-kannasta (bigdec kiinteat-kulut-rajapinnasta)))
    (is (= arvioidut-kulut-kannasta (bigdec arvioidut-kulut-rajapinnasta)))
    (is (= johto-ja-hallintokulut-kannasta (bigdec johto-ja-hallintokorvaukset-rajapinnasta)))))

(deftest hae-kustannussuunnitelma-puutteellisilla-tunnuksilla
  (let [;; Pakotetaan urakaksi Oulu MHU
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")

        ;; Poistetaan oikeudet
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        ;; Näillä oikeuksilla ei pitäisi pystyä kutsumaan analytiikan rajapintoja
        _ (anna-kirjoitusoikeus kayttaja-analytiikka)

        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-kustannukset/" urakka-id)] kayttaja-analytiikka portti)]
    ;; Käyttäjällä ei ole analytiikkaoikeuksia
    (is (= 403 (:status vastaus)) "Käyttäjältä ei löydy analytiikka api oikeuksia")
    (is (str/includes? (:body vastaus) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")))

(deftest hae-rahavaraukset-onnistuu-test
  (let [;; Löydetään n. 14 rahavarausta ja niiden tehtävät
        rahavaraukset-kannasta (q-map
                                 (str "SELECT r.id as id, r.nimi as nimi, array_agg(rt.tehtava_id) as tehtavat
                                        FROM rahavaraus r
                                             JOIN rahavaraus_tehtava rt on r.id = rt.rahavaraus_id
                                       GROUP BY r.id, r.nimi;"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/rahavaraukset")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count rahavaraukset-kannasta) (count (:rahavaraukset encoodattu-body))))))
