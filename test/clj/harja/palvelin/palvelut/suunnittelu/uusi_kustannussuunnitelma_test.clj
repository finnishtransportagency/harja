(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhmat-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodi-kyselyt]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(def johto-ja-hallinto-tietomalli-2025 {:johto-ja-hallintokorvaukset-2025 [{:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 10 :kalenterikuukausi "Lokakuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 11 :kalenterikuukausi "Marraskuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 12 :kalenterikuukausi "Joulukuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 1 :kalenterikuukausi "Tammikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 2 :kalenterikuukausi "Helmikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 3 :kalenterikuukausi "Maaliskuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 4 :kalenterikuukausi "Huhtikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 5 :kalenterikuukausi "Toukokuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 6 :kalenterikuukausi "Kesäkuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 7 :kalenterikuukausi "Heinäkuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 8 :kalenterikuukausi "Elokuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 9 :kalenterikuukausi "Syyskuu 2025"}]})

(defn urakkakohtaiset-toimenpideinstanssit-toimenpiteille
  "Tietomallissa toimenpideinstanssi on kovakoodattu. Aseta toimenpideinstanssi-id urakkakohtaisista toimenpiteistä."
  [h-tietomalli urakkakohtaiset-toimenpiteet]
  (assoc h-tietomalli :toimenpiteet
    (map (fn [toimenpide]
           (let [tpi (first (filter #(= (:nimi %) (:nimi toimenpide)) urakkakohtaiset-toimenpiteet))]
             (if tpi
               (assoc toimenpide :toimenpideinstanssi-id (:toimenpideinstanssi-id tpi))
               toimenpide)))
      (:toimenpiteet h-tietomalli))))

(deftest hae-kilpailutettavat-hankinnat-tietokannasta-onnistuneesti
  (testing "Hoitovuosi 2024"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
          hoitovuoden-alkuvuosi 2024
          ;; Päivitä toimenpideinstanssien id:t tietokannasta haetuilla id:illä
          h-tietomalli (urakkakohtaiset-toimenpideinstanssit-toimenpiteille apurit/hankinnat-tietomalli toimenpiteet)
          ;; Poista yhteenvetorivi ennen tallennusta
          h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta h-tietomalli)
          _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
          tiedot {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}
          kilpailutettavat-hankinnat (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot +kayttaja-jvh+ tiedot)
          hankinnat (get-in kilpailutettavat-hankinnat [:kustannussuunnitelma :kilpailutettavat-hankinnat])]

      (is (= (count (:toimenpiteet hankinnat)) 7))
      (is (true? (some #(str/includes? (str/lower-case (:nimi %)) "talvihoito") (:toimenpiteet hankinnat))))
      (is (= (:nimi (last (:toimenpiteet hankinnat))) "Yhteensä"))
      (is (= (:alkukausi (last (:toimenpiteet hankinnat))) 600M))))

  (testing "Pysyvät muutokset 2025"
    (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2025
          tiedot {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}
          kilpailutettavat-hankinnat (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot +kayttaja-jvh+ tiedot)
          pysyvat-muutokset (get-in kilpailutettavat-hankinnat [:kustannussuunnitelma :pysyvat-muutokset])
          pysyvat-muutokset-maara (get-in kilpailutettavat-hankinnat [:kustannussuunnitelma :pysyvat-muutokset-maara])]

      (is (= 1000 pysyvat-muutokset-maara))
      (is (= {:tavoitehinnan-muutos 1000
              :tavoitehinnan-muutos-indeksikorjattu 1374.0} (select-keys (first pysyvat-muutokset)
                                                              [:tavoitehinnan-muutos :tavoitehinnan-muutos-indeksikorjattu]))))))

(deftest tallenna-kilpailutettavat-hankinnat-tietokantaan-onnistuneesti-2021-urakalle
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2024
        ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        tietomallin-alkukausisumma (apply + (map :alkukausi (:toimenpiteet h-tietomalli)))
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        alkukausi-tietokannasta (q-map (format "SELECT SUM(summa) as summa FROM kiinteahintainen_tyo
                                                  WHERE sopimus = %s
                                                    AND vuosi = %s AND kuukausi IN (10,11,12)"
                                         sopimus-id hoitovuoden-alkuvuosi))]

    (is (= (bigdec tietomallin-alkukausisumma) (bigdec (:summa (first alkukausi-tietokannasta)))))))

(deftest tallenna-kilpailutettavat-hankinnat-tietokantaan-onnistuneesti-2019-urakalle
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2020
        ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        tietomallin-alkukausisumma (apply + (map :alkukausi (:toimenpiteet h-tietomalli)))
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        alkukausi-tietokannasta (q-map (format "SELECT SUM(summa) as summa FROM kiinteahintainen_tyo
                                                  WHERE sopimus = %s
                                                    AND vuosi = %s AND kuukausi IN (10,11,12)"
                                         sopimus-id hoitovuoden-alkuvuosi))]

    (is (= (bigdec tietomallin-alkukausisumma) (bigdec (:summa (first alkukausi-tietokannasta)))))))

(deftest tallenna-kilpailutettavat-hankinnat-rajapinnasta-ei-toimi
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+ {:jee "jee"})
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-kilpailutettavat-hankinnat kysely ei ole validi.")))))

(deftest tallenna-kilpailutettavat-hankinnat-rajapinnasta-toimii
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2024
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        ;; Päivitä toimenpideinstanssien id:t tietokannasta haetuilla id:illä
        h-tietomalli (urakkakohtaiset-toimenpideinstanssit-toimenpiteille apurit/hankinnat-tietomalli toimenpiteet)
        ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta h-tietomalli)
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+
                    (merge
                      {:urakka-id urakka-id
                       :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                       :kopioi-tuleville-vuosille? false}
                      h-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        toimenkuvat (butlast (get-in vastaus [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet]))
        alkukausi-summa-vastauksesta (apply + (map :alkukausi toimenkuvat))

        alkukausi-tietokannasta (q-map (format "SELECT * FROM kiinteahintainen_tyo
                                                  WHERE sopimus = %s
                                                    AND vuosi = %s AND kuukausi IN (10,11,12)"
                                         sopimus-id hoitovuoden-alkuvuosi))]
    (is (true? (nil? (:error vastaus))))
    (is (= (bigdec alkukausi-summa-vastauksesta) (bigdec (apply + (map :summa alkukausi-tietokannasta)))))))

(deftest tallenna-kilpailutettavat-hankinnat-rajapinnasta-tuleville-vuosille-toimii
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2021
        ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+
                    (merge
                      {:urakka-id urakka-id
                       :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                       :kopioi-tuleville-vuosille? true}
                      h-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        toimenkuvat (get-in vastaus [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
        alkukausi-vastauksesta (apply + (map :alkukausi toimenkuvat))

        ;; Vastauksesta saatiin kilpailutettavat hankinnat vuodelle 2021
        ;; Haetaan kilpailutettavat hankinnat myös tuleville vuosille ja tarkistetaan, että ne on kopioitu oikein
        alkukausi-tietokannasta (q-map (format "SELECT SUM(summa) as summa FROM kiinteahintainen_tyo
                                                  WHERE sopimus = %s
                                                    AND vuosi = %s AND kuukausi IN (10,11,12)"
                                         sopimus-id (inc hoitovuoden-alkuvuosi)))
        tietokantasumma (bigdec (:summa (first alkukausi-tietokannasta)))]
    (is (= (bigdec alkukausi-vastauksesta) tietokantasumma))))

;; Erillishankinnat
(deftest tallenna-erillishankinnat-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2024
        tietomallin-summa (apply + (map :summa (:erillishankinnat apurit/erillishankinnat-tietomalli)))

        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))

        _ (uusi-kust-kyselyt/tallenna-erillishankinnat db +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        erillishankinnat-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                         FROM kustannusarvioitu_tyo
                                                  WHERE sopimus = %s
                                                    AND toimenpideinstanssi = %s
                                                    AND tehtavaryhma = %s
                                                    AND (
                                                    (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                    (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                sopimus-id hoidonjohto-tpi-id (:id tehtavaryhma-erillishankinnat) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))]

    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first erillishankinnat-tietokannasta)))))))

(deftest tallenna-erillishankinnat-rajapinnasta-ei-toimi
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-erillishankinnat +kayttaja-jvh+ {:jee "jee"})
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-erillishankinnat kysely ei ole validi.")))
    (is (true? (str/includes? (:error vastaus) "failed: (contains? % :erillishankinnat)")))))

(deftest tallenna-erillishankinnat-rajapinnasta-ei-toimi2
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ apurit/erillishankinnat-tietomalli)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (str/includes? (:error vastaus) "Palvelun :tallenna-erillishankinnat kysely ei ole validi."))
    ;; Urakka-id puuttuu
    (is (str/includes? (:error vastaus) "failed: (contains? % :urakka-id)"))))

(deftest tallenna-erillishankinnat-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map :summa (:erillishankinnat apurit/erillishankinnat-tietomalli)))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ (merge apurit/erillishankinnat-tietomalli
                                                                {:urakka-id urakka-id
                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :erillishankinnat])))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec vastaus-summa)))))

(deftest kopioi-erillishankinnat-tuleville-vuosille-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2021
        db (:db jarjestelma)
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db
                              {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))

        tietomallin-summa (apply + (map :summa (:erillishankinnat apurit/erillishankinnat-tietomalli)))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ (merge apurit/erillishankinnat-tietomalli
                                                                {:urakka-id urakka-id
                                                                 :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                                 :kopioi-tuleville-vuosille? true}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :erillishankinnat])))

        ;; Hae seuraavan vuoden summa
        seuraava-vuosi (inc hoitovuoden-alkuvuosi)
        erillishankinnat-seuraava-vuosi (q-map (format "SELECT SUM(summa) as summa
                                                         FROM kustannusarvioitu_tyo
                                                  WHERE sopimus = %s
                                                    AND toimenpideinstanssi = %s
                                                    AND tehtavaryhma = %s
                                                    AND (
                                                    (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                    (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                 sopimus-id hoidonjohto-tpi-id (:id tehtavaryhma)
                                                 seuraava-vuosi (inc seuraava-vuosi)))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec vastaus-summa)))
    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first erillishankinnat-seuraava-vuosi)))))))

(deftest muokkaa-erillishankinnat-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ (merge apurit/erillishankinnat-tietomalli
                                                                {:urakka-id urakka-id
                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-erillishankinnat (into [] (get-in vastaus [:kustannussuunnitelma :erillishankinnat]))

        ;; Varmistetaan, ettei ole erroreita
        _ (is (nil? (:error vastaus)))

        ;; Tallenna muokattu tietomalli
        muokattu-vastaus (-> vastaus-erillishankinnat
                           (assoc-in [0 :summa] 1500)
                           (assoc-in [1 :summa] 2500)
                           (assoc-in [2 :summa] 3500))
        muokattu-summa (apply + (map :summa muokattu-vastaus))
        muokattu-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :tallenna-erillishankinnat +kayttaja-jvh+
                             (merge {:erillishankinnat muokattu-vastaus}
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi 2024}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))
        muokattu-vastaus-summa (apply + (map :summa (get-in muokattu-vastaus [:kustannussuunnitelma :erillishankinnat])))]

    (is (= (bigdec muokattu-summa) (bigdec muokattu-vastaus-summa)))))

;; Hoidonjohtopalkkiot
(deftest tallenna-hoidonjohtopalkkiot-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        hoitovuoden-alkuvuosi 2024
        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot db +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        hoidonjohtopalkkiot-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                            FROM kustannusarvioitu_tyo
                                                           WHERE sopimus = %s
                                                             AND toimenpideinstanssi = %s
                                                             AND tehtava = %s
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                   sopimus-id hoidonjohto-tpi-id (:id tehtava-hoidonjohtopalkkio)
                                                   hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))]

    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first hoidonjohtopalkkiot-tietokannasta)))))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-ei-toimi
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ {:jee "jee"})
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-hoidonjohtopalkkiot kysely ei ole validi.")))
    (is (true? (str/includes? (:error vastaus) "failed: (contains? % :hoidonjohtopalkkiot)")))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-toimii
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava-id (:id (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"})))
        hoitovuoden-alkuvuosi 2021
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+
                    (merge {:urakka-id urakka-id
                            :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                            :kopioi-tuleville-vuosille? false}
                      apurit/hoidonjohtopalkkiot-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))

        hoidonjohtopalkkiot-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                            FROM kustannusarvioitu_tyo
                                                           WHERE sopimus = %s
                                                             AND toimenpideinstanssi = %s
                                                             AND tehtava = %s
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                   sopimus-id hoidonjohto-tpi-id tehtava-id
                                                   hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))]
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first hoidonjohtopalkkiot-tietokannasta)))))))

(deftest tallenna-hoidonjohtopalkkiot-tuleville-vuosille-toimi
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava-id (:id (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"})))
        hoitovuoden-alkuvuosi 2021
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+
                    (merge {:urakka-id urakka-id
                            :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                            :kopioi-tuleville-vuosille? true}
                      apurit/hoidonjohtopalkkiot-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))

        seuraava-vuosi (inc hoitovuoden-alkuvuosi)
        hoidonjohtopalkkiot-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                            FROM kustannusarvioitu_tyo
                                                           WHERE sopimus = %s
                                                             AND toimenpideinstanssi = %s
                                                             AND tehtava = %s
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                   sopimus-id hoidonjohto-tpi-id tehtava-id
                                                   seuraava-vuosi (inc seuraava-vuosi)))]
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first hoidonjohtopalkkiot-tietokannasta)))))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-ei-toimi2
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ apurit/hoidonjohtopalkkiot-tietomalli)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-hoidonjohtopalkkiot kysely ei ole validi.")))
    ;; Urakka-id puuttuu
    (is (true? (str/includes? (:error vastaus) "failed: (contains? % :urakka-id)")))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-toimii-2
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ (merge apurit/hoidonjohtopalkkiot-tietomalli
                                                                   {:urakka-id urakka-id
                                                                    :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :hoidonjohtopalkkiot])))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec vastaus-summa)))))


(deftest muokkaa-hoidonjohtopalkkiot-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ (merge apurit/hoidonjohtopalkkiot-tietomalli
                                                                   {:urakka-id urakka-id
                                                                    :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-hoidonjohtopalkkiot (into [] (get-in vastaus [:kustannussuunnitelma :hoidonjohtopalkkiot]))

        ;; Varmistetaan, ettei ole erroreita
        _ (is (nil? (:error vastaus)))

        ;; Tallenna muokattu tietomalli
        muokattu-vastaus (-> vastaus-hoidonjohtopalkkiot
                           (assoc-in [0 :summa] 1500)
                           (assoc-in [1 :summa] 2500)
                           (assoc-in [2 :summa] 3500))
        muokattu-summa (apply + (map :summa muokattu-vastaus))
        muokattu-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+
                             (merge {:hoidonjohtopalkkiot muokattu-vastaus}
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi 2024}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))
        muokattu-vastaus-summa (apply + (map :summa (get-in muokattu-vastaus [:kustannussuunnitelma :hoidonjohtopalkkiot])))]

    (is (= (bigdec muokattu-summa) (bigdec muokattu-vastaus-summa)))))

;; Johto- ja hallintokorvaukset
(deftest tallenna-johto-ja-hallintokorvaukset-2019-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map
                                     (fn [kuukausi]
                                       (* (or (:tunnit kuukausi) 0) (or (:tuntipalkka kuukausi) 0)))
                                     (flatten (map :kuukaudet (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019)))))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+ (merge apurit/johto-ja-hallinto-tietomalli-2019
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec (round2 tietomallin-summa 2)) (bigdec (round2 vastaus-summa 2))))
    ;; Viimeinen rivi on "Muut kulut"
    (is (= "Muut kulut" (:toimenkuva (last (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))))))

(deftest kopioi-johto-ja-hallintokorvaukset-2019-tuleville-vuosille-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitokauden-alkuvuosi 2021
        tietomallin-summa (apply + (map
                                     (fn [kuukausi]
                                       (* (or (:tunnit kuukausi) 0) (or (:tuntipalkka kuukausi) 0)))
                                     (flatten (map :kuukaudet (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019)))))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+ (merge apurit/johto-ja-hallinto-tietomalli-2019
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi hoitokauden-alkuvuosi
                                                                                 :kopioi-tuleville-vuosille? true}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))

        ;; Hae seuraavan vuoden summa
        seuraava-vuosi (inc hoitokauden-alkuvuosi)
        toimenkuvat-seuraava-vuosi (q-map (format "SELECT *
                                                            FROM johto_ja_hallintokorvaus
                                                           WHERE \"urakka-id\" = %s
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                            urakka-id seuraava-vuosi (inc seuraava-vuosi)))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec (round2 tietomallin-summa 2)) (bigdec (round2 vastaus-summa 2))))
    (is (= (bigdec (round2 2 tietomallin-summa)) (bigdec (round2 2 (apply + (map :tuntipalkka toimenkuvat-seuraava-vuosi))))))
    ;; Viimeinen rivi on "Muut kulut"
    (is (= "Muut kulut" (:toimenkuva (last (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))))))

(deftest muokkaa-johto-ja-hallintokorvaukset-2025-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        hoitovuoden-alkuvuosi 2025
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2025 +kayttaja-jvh+ (merge johto-ja-hallinto-tietomalli-2025
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-toimenkuvat (into [] (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))

        ;; Varmistetaan, ettei ole erroreita
        _ (is (nil? (:error vastaus)))

        ;; Tallenna muokattu tietomalli
        muokattu-vastaus (-> vastaus-toimenkuvat
                           (assoc-in [0 :summa] 1500)
                           (assoc-in [1 :summa] 2500)
                           (assoc-in [2 :summa] 3500))
        muokattu-summa (apply + (map :summa muokattu-vastaus))
        muokattu-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :tallenna-johto-ja-hallintokorvaukset-2025 +kayttaja-jvh+
                             (merge {:johto-ja-hallintokorvaukset-2025 muokattu-vastaus}
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))
        muokattu-vastaus-summa (apply +
                                 (map :summa (get-in muokattu-vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))]

    (is (= (bigdec muokattu-summa) (bigdec muokattu-vastaus-summa)))
    ;; Viimeinen rivi ei ole "Muut kulut" - -25 urakoilla ei ole muita kuluja
    (is (not= "Muut kulut" (:toimenkuva (last (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))))))

(deftest kopioi-johto-ja-hallintokorvaukset-2025-tuleville-vuosille-toimii
  (let [urakka-id (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        hoitovuoden-alkuvuosi 2025
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2025 +kayttaja-jvh+ (merge johto-ja-hallinto-tietomalli-2025
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                                                 :kopioi-tuleville-vuosille? true}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-toimenkuvat (into [] (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))
        vastaus-toimenkuvat (into [] (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))
        vastaus-summa (apply + (map :summa vastaus-toimenkuvat))
        ;; Varmistetaan, ettei ole erroreita
        _ (is (nil? (:error vastaus)))

        ;; Hae seuraavan vuoden summa
        seuraava-vuosi (inc hoitovuoden-alkuvuosi)
        toimenkuvat-seuraava-vuosi (q-map (format "SELECT *
                                                            FROM johto_ja_hallintokorvaus
                                                           WHERE \"urakka-id\" = %s
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                            urakka-id seuraava-vuosi (inc seuraava-vuosi)))]
    (is (= (bigdec (round2 2 vastaus-summa)) (bigdec (round2 2 (apply + (map :tuntipalkka toimenkuvat-seuraava-vuosi))))))))

(deftest muokkaa-johto-ja-hallintokorvaukset-2019-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+ (merge apurit/johto-ja-hallinto-tietomalli-2019
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-hoidonjohtopalkkiot (into [] (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))

        ;; Varmistetaan, ettei ole erroreita
        _ (is (nil? (:error vastaus)))

        ;; Tallenna muokattu tietomalli
        muokattu-vastaus (-> vastaus-hoidonjohtopalkkiot
                           (assoc-in [0 :kuukaudet 0 :tuntipalkka] 1500)
                           (assoc-in [1 :kuukaudet 0 :tuntipalkka] 2500)
                           (assoc-in [2 :kuukaudet 0 :tuntipalkka] 3500))
        muokattu-summa (apply + (map
                                  (fn [rivi]
                                    (* (or (:tunnit rivi) 0) (or (:tuntipalkka rivi) 0)))
                                  (flatten (map :kuukaudet muokattu-vastaus))))
        muokattu-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+
                             (merge {:johto-ja-hallintokorvaukset-2019 muokattu-vastaus}
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi 2024}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))

        muokattu-vastaus-summa (apply +
                                 (map #(or (:yhteensa-kk %) 0)
                                   (flatten (map :kuukaudet (get-in muokattu-vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))))]

    (is (= (bigdec muokattu-summa) (bigdec muokattu-vastaus-summa)))))

(deftest vahvista-tavoite-ja-kattohinta-ei-onnistu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        hoitovuoden-alkuvuosi 2024
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true}

        ;; Poistetaan kaikki tiedot, niin vahvistus ei voi onnistua
        _ (u (format "DELETE FROM kiinteahintainen_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM kustannusarvioitu_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM johto_ja_hallintokorvaus WHERE \"urakka-id\" = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))

        ;; Kustisksen vahvistus vaatii tarjouksen tallentamisen, joten tallennetaan alkuun simppeli tarjous, niin ei jää siitä kiinni
        kattohintakerroin 1.1
        vahvistetut-vuodet #{}

        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt apurit/tarjous-tietomalli-2019 tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        tarjous (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)

        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        _ (is (= (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe]) "Tietoja ei voitu vahvistaa. Kustannustietoja puuttuu. Tarkista Kilpailutettavat hankinnat, Erillishankinnat, Hoidonjohtopalkkiot, Johto-ja-hallintokorvaukset"))

        _ (u (format "update urakka set indeksi = null WHERE id = %s" urakka-id)) ;; Poistetaan urakan indeksi
        vastaus-indeksi (try
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                            :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                          (catch Exception e
                            (println "Tapahtui virhe:" (.getMessage e))
                            {:error (.getMessage e)}))

        _ (is (= (get-in vastaus-indeksi [:kustannussuunnitelma :vahvistus-virhe])
                (format "Indeksit puuttuvat hoitovuodelle %s. Indeksit on lisättävä ennen vahvistusta. Tietoja ei voitu vahvistaa. Kustannustietoja puuttuu. Tarkista Kilpailutettavat hankinnat, Erillishankinnat, Hoidonjohtopalkkiot, Johto-ja-hallintokorvaukset"
                  hoitovuoden-alkuvuosi)))]))

(deftest vahvista-ja-kumoa-tavoite-ja-kattohinta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024

        ;; Lisätään ensin kilpailutettavat hankinnat
        ;; ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Varmista, että kustannussuunnitelmaa ei ole vielä vahvistettu
        kustannussuunnitelma (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot
                               +kayttaja-jvh+
                               {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
        _ (is (false? (get-in kustannussuunnitelma [:kustannussuunnitelma :vahvistettu?])) "Kustannussuunnitelman pitäisi olla vahvistamaton ennen vahvistusta")

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)

        ;; Vahvistetaan tavoite ja kattohinta
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true}
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (nil? (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])) "Vahvistuksessa ei pitäisi olla virhettä")
    (is (not (nil? (get-in vastaus [:tarjous]))) "Vastauksessa pitäisi olla tarjous")
    (is (not (nil? (get-in vastaus [:kustannussuunnitelma]))) "Vastauksessa pitäisi olla kustannussuunnitelma")
    (is (true? (get-in vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")
    (is (= 3 (count (get-in vastaus [:kustannussuunnitelma :rahavaraukset]))) "Rahavarauksia pitäisi olla 3")
    (is (= 7 (count (get-in vastaus [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet]))) "Kilpailutettavia hankintoja pitäisi olla 7")
    (is (= 12 (count (get-in vastaus [:kustannussuunnitelma :erillishankinnat]))) "Erillishankintoja pitäisi olla 12 kuukautta")
    (is (= 10 (count (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))) "Johto- ja hallintokorvauksia pitäisi olla 9 toimenkuvaa")
    (is (= 12 (count (:kuukaudet (first (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))))) "Ensimmäisellä johto- ja hallintokorvauksella / toimenkuvalla pitäisi olla 12 kuukautta")
    (is (= 12 (count (get-in vastaus [:kustannussuunnitelma :hoidonjohtopalkkiot]))) "Hoidonjohtopalkkioissa pitäisi olla 12 kuukautta")

    ;; Kumotaan vahvistus
    (let [kumous-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ (merge tiedot {:vahvista? false}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))]
      (is (false? (get-in kumous-vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla false"))))

(defn- tallenna-kustannussuunnitelma-ja-tarjous!
  "Tallentaa kustannussuunnitelman ja tarjouksen testikäyttöön.
   Palauttaa urakan id:n."
  [urakka-id hoitovuoden-alkuvuosi johto-ja-hallinto-tietomalli tarjous-tietomalli]
  ;; Kilpailutettavat hankinnat
  (let [h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet (:db jarjestelma) {:urakkaid urakka-id})
        h-tietomalli (apurit/paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)
        kayttaja-id (:id +kayttaja-jvh+)
        kattohintakerroin 1.1
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}]
    (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
      hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
    ;; Erillishankinnat
    (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
      (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
    ;; Hoidonjohtopalkkiot
    (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
      (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
    ;; Johto- ja hallintokorvaukset
    (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
      johto-ja-hallinto-tietomalli hoitovuoden-alkuvuosi)
    ;; Tarjous
    (tarjous-kyselyt/tallenna-tarjous-tietokantaan
      (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)))

(defn- vahvista-tai-kumoa-tavoite-ja-kattohinta!
  "Vahvistaa tai kumoaa tavoitteen ja kattohinnan"
  [urakka-id hoitovuoden-alkuvuosi vahvista?]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
    {:urakka-id urakka-id
     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
     :vahvista? vahvista?}))

(deftest laskutusraja-paivittyy-tavoite-ja-kattohinnan-vahvistuksessa
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Tallenna kustannussuunnitelma ja tarjous
    (tallenna-kustannussuunnitelma-ja-tarjous!
      urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 johto-ja-hallinto-tietomalli-2025)
      apurit/tarjous-tietomalli-2025)
    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")
    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    ;; Tarkista että laskutusraja on asetettu
    (let [laskutusraja (hae-urakan-laskutusraja urakka-id)
          tavoitehinta_indeksikorotettu (:tavoitehinta_indeksikorjattu
                                          (first (q-map (format "SELECT tavoitehinta_indeksikorjattu
                                                FROM urakka_tavoite
                                                WHERE urakka = %s AND hoitokausi = 1" urakka-id))))]
      (is (not (nil? laskutusraja)) "Laskutusrajan pitäisi olla asetettu")
      (is (= laskutusraja tavoitehinta_indeksikorotettu) "Laskutusrajan pitäisi olla sama kuin tavoitehinta_indeksikorjattu"))))

(deftest laskutusraja-ei-paivity-kun-laskutusraja_kaytossa-false
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026") ;; -21 alkanut urakka
        hoitovuoden-alkuvuosi 2024]
    ;; Varmista että laskutusraja_kaytossa = FALSE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = FALSE WHERE urakkaid = " urakka-id)
    ;; Tallenna kustannussuunnitelma ja tarjous
    (tallenna-kustannussuunnitelma-ja-tarjous!
      urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2019  apurit/johto-ja-hallinto-tietomalli-2019)
      apurit/tarjous-tietomalli-2019)
    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")
    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL kun laskutusraja_kaytossa = FALSE")))

(deftest laskutusraja-nollataan-kun-vahvistus-kumotaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Varmista että laskutusraja_kaytossa = TRUE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)
    ;; Tallenna kustannussuunnitelma ja tarjous
    (tallenna-kustannussuunnitelma-ja-tarjous!
      urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 johto-ja-hallinto-tietomalli-2025)
      apurit/tarjous-tietomalli-2025)
    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (not (nil? (hae-urakan-laskutusraja urakka-id))) "Laskutusrajan pitäisi olla asetettu")
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ {:urakka-id urakka-id
                                                      :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                      :vahvista? false})
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL vahvistuksen kumouksen jälkeen")))

(deftest vahvista-kattohinta-toimii-tarkennettuna
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-sopimus-id-urakka-idlla urakka-id)
        hoitovuoden-alkuvuosi 2024
        alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
        loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))

        ;; Lisätään ensin kilpailutettavat hankinnat - Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)

        ;; Vahvistetaan tavoite ja kattohinta
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true}
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        _ (is (true? (get-in vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")

        jalkeen-kiinteat-rivit (q-map (format "SELECT summa, summa_indeksikorjattu, vahvistaja, indeksikorjaus_vahvistettu, vuosi, kuukausi, tpi.nimi
                                               FROM kiinteahintainen_tyo kt
                                                    join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id AND tpi.urakka = %s
                                                    JOIN toimenpide t ON tpi.toimenpide = t.id
                                              WHERE kt.sopimus = %s
                                                AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '%s'::DATE AND '%s'::DATE)
                                                AND true = onko_mhu_hankintatoimenpide(t.koodi)
                                              ORDER BY kt.vuosi, kt.kuukausi"
                                        urakka-id sopimus-id alkupvm loppupvm))

        ;; Varmistetaan rivi riviltä, että kaikki kiinteät on vahvistettu.
        ;; Kiinteiden töiden vahvistamisessa on otettava huomioon se, että ihan kaikkia sieltä löytyviä riviä ei vahvisteta. Ainoastaan
        ;; Hankintatoimenpiteet (onko_mhu_hankintatoimenpide(t.koodi) = true).
        _ (is (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) jalkeen-kiinteat-rivit) "Kaikilla kiinteähintaisilla riveillä pitäisi olla indeksi vahvistettu")]))

(deftest testaa-kasin-syotettava-kattohinta-2021-urakalle
  (let [kayttaja-id (:id +kayttaja-jvh+)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024

        ;; Lisätään ensin kilpailutettavat hankinnat - Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.

        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)

        ;; Vahvistetaan tavoite ja kattohinta
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true
                :paivitetty-kattohinta 5} ;; Bäkkärin pitäisi jättää tämä huomioimatta 2024 urakoilla
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        _ (is (true? (get-in vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")

        ;; Haetaan kustannussuunnitelma ja tarkistetaan kattohinta
        kustannussuunnitelma (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-kustannussuunnitelman-tiedot +kayttaja-jvh+
                               {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})

        tavoitehinta (get-in kustannussuunnitelma [:kustannussuunnitelma :hoitovuoden-alun-tavoitehinta])
        kattohinta (get-in kustannussuunnitelma [:kustannussuunnitelma :hoitovuoden-alun-kattohinta])

        ;; Tarkistetaan että kattohinta on 1.1 x tavoitehinta 2021 alkavalla urakalla
        _ (is (false? (get-in kustannussuunnitelma [:kustannussuunnitelma :muokkaa-kattohinta-kasin])) "2024 urakoilla ei voi muokata kattohintaa käsin")
        _ (is (< 0 kattohinta) "Kattohinta pitäisi löytyä")
        odotettu-kattohinta (* kattohintakerroin tavoitehinta)
        _ (is (= (bigdec (round2 kattohinta 2)) (bigdec (round2 odotettu-kattohinta 2)))
            (str "Kattohinnan pitäisi olla " kattohintakerroin " x tavoitehinta. Tavoitehinta: " tavoitehinta ", odotettu kattohinta: " odotettu-kattohinta ", todellinen kattohinta: " kattohinta))]))

(deftest testaa-kasin-syotettava-kattohinta-2019-urakalle
  (let [;; TODO .. 
        db (:db jarjestelma)
        kayttaja-id (:id +kayttaja-jvh+)
        urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
        hoitovuoden-alkuvuosi 2020
        hoitovuosinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        ;; Lisää käsin kattohinta tarjouksen vuosille
        hoitovuoden-kattohinta 21
        kattohinta-rivi {:nimi "Tarjouksen kattohinta" :osio "yhteensa"
                         :hoitovuosittaiset-arvot [{:vuosi 2019 :summa 11}
                                                   {:vuosi 2020 :summa hoitovuoden-kattohinta}
                                                   {:vuosi 2021 :summa 8500}
                                                   {:vuosi 2022 :summa 7000}
                                                   {:vuosi 2023 :summa 8000}]
                         :yhteensa 11532}
        tarjous (update tarjous :tarjous #(conj % kattohinta-rivi))
        ;; 2019-urakoille ei anneta kattohintakerrointa, vaan kattohinta syötetään käsin urakan_parametrit-tauluun
        kattohintakerroin nil
        vahvistetut-vuodet #{}


        ;; Varmista suoraan tietokannasta, että tarjous taulun tarjous_kattohinta kolumniin meni oikeat arvot
        tarjous-tietokannasta (first (q-map (format "SELECT tarjous_kattohinta FROM tarjous WHERE urakka_id = %s AND hoitokauden_alkuvuosi = %s" urakka-id hoitovuoden-alkuvuosi)))
        _ (is (= (bigdec (round2 2 hoitovuoden-kattohinta)) (:tarjous_kattohinta tarjous-tietokannasta)) (format "Tarjous taulun tarjous_kattohinta pitäisi olla %s" hoitovuoden-kattohinta))

        ;; Mahdollistetaan kustiksen vahvistus
        ;; Lisätään ensin kilpailutettavat hankinnat - Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        h-tietomalli (apurit/paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;_ (uusi-kust-kyselyt/paivita-tavoite-ja-kattohinta (:db jarjestelma) kayttaja-id urakka-id hoitovuoden-alkuvuosi)

        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)
        ;; Tavoitehinta lasketaan erillisellä funktiolla, jos kustsutaan tallennuksia suoraan kyselyfunktioilla eikä rajapinnan kautta
        ;_ (uusi-kust-kyselyt/paivita-tavoite-ja-kattohinta (:db jarjestelma) kayttaja-id urakka-id hoitovuoden-alkuvuosi)


        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous vahvistetut-vuodet)


        ;; Syötä kattohinta käsin kustannussuuunnitelmalle.
        ;; Vahvistetaan tavoite ja kattohinta
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true
                :paivitetty-kattohinta 5}

        virheellinen-vastaus (try
                               (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                               (catch Exception e
                                 (println "Tapahtui virhe:" e (.getMessage e))
                                 {:error (.getMessage e)}))

        _ (is (str/includes? virheellinen-vastaus "Annettu kattohinta 5 on pienempi") "Virhe heitettiin")

        kasin-paivitetty-kattohinta 1500000M
        tiedot2 {:urakka-id urakka-id
                 :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                 :vahvista? true
                 :paivitetty-kattohinta kasin-paivitetty-kattohinta}

        toimiva-vastaus (try
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                            :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot2)
                          (catch Exception e
                            (println "Tapahtui virhe:" e (.getMessage e))
                            {:error (.getMessage e)}))



        dbtavoite (first (q-map (format "SELECT * from urakka_tavoite where urakka = %s AND hoitokausi = %s" urakka-id hoitovuosinumero)))]
    (is (true? (get-in toimiva-vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")
    (is (= (:kattohinta dbtavoite) kasin-paivitetty-kattohinta) "urakka_tavoite -taulusta löytyy oikea kattohinta")
    (is (= kasin-paivitetty-kattohinta (get-in toimiva-vastaus [:kustannussuunnitelma :hoitovuoden-alun-kattohinta])) "Käsin asetettu kattohinta täsmää")))

(deftest paivita-tavoite-ja-kattohinta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-q/hae-urakan-tiedot (:db jarjestelma) urakka-id))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet (:db jarjestelma) urakka-id)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet (:db jarjestelma) {:urakkaid urakka-id})
        hoitovuoden-alkuvuosi 2024
        hoitokausinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        hae-tavoitetiedot (fn [urakka-id hoitokausinumero]
                            (q-map (format "SELECT tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu
                                        FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s"
                                     urakka-id hoitokausinumero)))


        ;; Poistetaan kaikki tiedot, jotta tavoitehinnan laskenta menee varmasti uudestaan
        _ (u (format "DELETE FROM kiinteahintainen_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               (hae-sopimus-id-urakka-idlla urakka-id) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM kustannusarvioitu_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               (hae-sopimus-id-urakka-idlla urakka-id) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM johto_ja_hallintokorvaus WHERE \"urakka-id\" = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka-id hoitokausinumero))

        ;; Hankinnat - Päivitä toimenpideinstanssien id:t tietokannasta haetuilla id:illä
        h-tietomalli (urakkakohtaiset-toimenpideinstanssit-toimenpiteille apurit/hankinnat-tietomalli toimenpiteet)
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta h-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        _ (uusi-kust-kyselyt/paivita-tavoite-ja-kattohinta (:db jarjestelma) (:id +kayttaja-jvh+) urakka-id hoitovuoden-alkuvuosi)
        hankinnat-yhteensa (bigdec (apply + (map :yhteensa (:toimenpiteet h-tietomalli))))
        hankinnat-indeksikorjattu-yhteensa (bigdec (indeksi-kyselyt/indeksikorjaa (indeksi-kyselyt/indeksikerroin urakan-indeksit hoitokausinumero) hankinnat-yhteensa))
        tavoitetiedot (first (hae-tavoitetiedot urakka-id hoitokausinumero))]

    (is (= (:tavoitehinta tavoitetiedot) hankinnat-yhteensa) "Tavoitehinnan pitäisi vastata hankintojen summaa")
    (is (= (:tavoitehinta_indeksikorjattu tavoitetiedot) hankinnat-indeksikorjattu-yhteensa)
      "Indeksikorjatun tavoitehinnan pitäisi vastata indeksikorjattujen hankintojen summaa")
    (is (= (:kattohinta tavoitetiedot) (* kattohintakerroin hankinnat-yhteensa)) "Kattohinnan pitäisi vastata hankintojen summaa kerrottuna kattohintakertoimella")
    (is (= (:kattohinta_indeksikorjattu tavoitetiedot) (* kattohintakerroin hankinnat-indeksikorjattu-yhteensa))
      "Indeksikorjatun kattohinnan pitäisi vastata indeksikorjattujen hankintojen summaa kerrottuna kattohintakertoimella")))
