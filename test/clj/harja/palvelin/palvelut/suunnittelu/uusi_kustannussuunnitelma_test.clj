(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
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
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]))

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

(def hankinnat-tietomalli {:toimenpiteet [{:nimi "Talvihoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 90 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Liikenneympäristön hoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 91 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Soratien hoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 92 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Päällysteiden paikkaus (hoidon ylläpito)", :osio "hankintakustannukset" :toimenpideinstanssi-id 93 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "MHU Ylläpito", :osio "hankintakustannukset" :toimenpideinstanssi-id 94 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "MHU Korvausinvestointi", :osio "hankintakustannukset" :toimenpideinstanssi-id 95 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "MHU ja HJU Hoidon johto", :osio "hankintakustannukset" :toimenpideinstanssi-id 96 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Yhteensä", :osio "hankintakustannukset" :toimenpideinstanssi-id 0 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 700 :alkukausi-indeksikorjattu 777 :loppukausi 2100 :loppukausi-indeksikorjattu 2331 :yhteensa 2800 :yhteensa-indeksikorjattu 3108}]})

(def erillishankinnat-tietomalli {:erillishankinnat [{:summa 1000 :summa_indeksikorjattu 1111 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 10 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Lokakuu 2024"}
                                                     {:summa 2000 :summa_indeksikorjattu 2222 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 11 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Marraskuu 2024"}
                                                     {:summa 3000 :summa_indeksikorjattu 3333 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 12 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Joulukuu 2024"}
                                                     {:summa 4000 :summa_indeksikorjattu 4444 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 1 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Tammikuu 2025"}
                                                     {:summa 5000 :summa_indeksikorjattu 5555 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 2 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Helmikuu 2025"}
                                                     {:summa 6000 :summa_indeksikorjattu 6666 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 3 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Maaliskuu 2025"}
                                                     {:summa 7000 :summa_indeksikorjattu 7777 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 4 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Huhtikuu 2025"}
                                                     {:summa 8000 :summa_indeksikorjattu 8888 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 5 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Toukokuu 2025"}
                                                     {:summa 9000 :summa_indeksikorjattu 9999 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 6 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Kesäkuu 2025"}
                                                     {:summa 10000 :summa_indeksikorjattu 11111 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 7 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Heinäkuu 2025"}
                                                     {:summa 11000 :summa_indeksikorjattu 12121 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 8 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Elokuu 2025"}
                                                     {:summa 12000 :summa_indeksikorjattu 13333 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 9 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Syyskuu 2025"}]})

(def hoidonjohtopalkkiot-tietomalli {:hoidonjohtopalkkiot [{:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 10 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Lokakuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 11 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Marraskuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 12 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Joulukuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 1 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Tammikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 2 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Helmikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 3 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Maaliskuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 4 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Huhtikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 5 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Toukokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 6 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Kesäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 7 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Heinäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 8 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Elokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 9 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Syyskuu 2025"}]})

(def johto-ja-hallinto-tietomalli-2019 {:johto-ja-hallintokorvaukset-2019
                                        [{:id 1 :toimenkuva "sopimusvastaava"
                                          :kuukaudet [{:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 10 :kalenterikuukausi "Lokakuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 11 :kalenterikuukausi "Marraskuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 12 :kalenterikuukausi "Joulukuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 1 :kalenterikuukausi "Tammikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 2 :kalenterikuukausi "Helmikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 3 :kalenterikuukausi "Maaliskuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 4 :kalenterikuukausi "Huhtikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 5 :kalenterikuukausi "Toukokuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 6 :kalenterikuukausi "Kesäkuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 7 :kalenterikuukausi "Heinäkuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 8 :kalenterikuukausi "Elokuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 9 :kalenterikuukausi "Syyskuu 2025"}]}]})

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

(defn poista-yhteenvetorivi [tietomalli]
  {:toimenpiteet (filter #(not= (:nimi %) "Yhteensä") (:toimenpiteet tietomalli))})

(deftest hae-kilpailutettavat-hankinnat-tietokannasta-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024
        ;; Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (poista-yhteenvetorivi hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
        tiedot {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}
        kilpailutettavat-hankinnat (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot +kayttaja-jvh+ tiedot)
        hankinnat (get-in kilpailutettavat-hankinnat [:kustannussuunnitelma :kilpailutettavat-hankinnat])]

    (is (= (count (:toimenpiteet hankinnat)) 7))
    (is (true? (some #(= (:nimi %) "Talvihoito laaja TPI") (:toimenpiteet hankinnat))))
    (is (= (:nimi (last (:toimenpiteet hankinnat))) "Yhteensä"))))

(deftest tallenna-kilpailutettavat-hankinnat-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2024
        ;; Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (poista-yhteenvetorivi hankinnat-tietomalli)
        tietomallin-alkukausisumma (apply + (map :alkukausi (:toimenpiteet hankinnat-tietomalli)))
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
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
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024
        ;; Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (poista-yhteenvetorivi hankinnat-tietomalli)
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+
                    (merge
                      {:urakka-id urakka-id
                       :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}
                      hankinnat-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]
    (is (true? (nil? (:error vastaus))))))

;; Erillishankinnat
(deftest tallenna-erillishankinnat-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        hoitovuoden-alkuvuosi 2024
        tietomallin-summa (apply + (map :summa (:erillishankinnat erillishankinnat-tietomalli)))

        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))

        _ (uusi-kust-kyselyt/tallenna-erillishankinnat db +kayttaja-jvh+ urakka-id (:erillishankinnat erillishankinnat-tietomalli))
        erillishankinnat-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                         FROM kustannusarvioitu_tyo
                                                  WHERE sopimus = %s
                                                    AND toimenpideinstanssi = %s
                                                    AND tehtavaryhma = 28
                                                    AND (
                                                    (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                    (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                sopimus-id hoidonjohto-tpi-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))]

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
                    :tallenna-erillishankinnat +kayttaja-jvh+ erillishankinnat-tietomalli)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (str/includes? (:error vastaus) "Palvelun :tallenna-erillishankinnat kysely ei ole validi."))
    ;; Urakka-id puuttuu
    (is (str/includes? (:error vastaus) "failed: (contains? % :urakka-id)"))))

(deftest tallenna-erillishankinnat-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map :summa (:erillishankinnat erillishankinnat-tietomalli)))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ (merge erillishankinnat-tietomalli
                                                                {:urakka-id urakka-id
                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :erillishankinnat])))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec tietomallin-summa) (bigdec vastaus-summa)))))

(deftest muokkaa-erillishankinnat-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-erillishankinnat +kayttaja-jvh+ (merge erillishankinnat-tietomalli
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
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli)))
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot db +kayttaja-jvh+ urakka-id (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli))
        hoidonjohtopalkkiot-tietokannasta (q-map (format "SELECT SUM(summa) as summa
                                                            FROM kustannusarvioitu_tyo
                                                           WHERE sopimus = %s
                                                             AND toimenpideinstanssi = %s
                                                             AND tehtava = 3061
                                                             AND (
                                                                  (vuosi = %s AND kuukausi in (10,11,12)) OR
                                                                  (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
                                                   sopimus-id hoidonjohto-tpi-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))]

    (is (= (bigdec tietomallin-summa) (bigdec (:summa (first hoidonjohtopalkkiot-tietokannasta)))))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-ei-toimi
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ {:jee "jee"})
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-hoidonjohtopalkkiot kysely ei ole validi.")))
    (is (true? (str/includes? (:error vastaus) "failed: (contains? % :hoidonjohtopalkkiot)")))))

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-ei-toimi2
  (let [vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ hoidonjohtopalkkiot-tietomalli)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (true? (str/includes? (:error vastaus) "Palvelun :tallenna-hoidonjohtopalkkiot kysely ei ole validi.")))
    ;; Urakka-id puuttuu
    (is (true? (str/includes? (:error vastaus) "failed: (contains? % :urakka-id)")))))

(deftest tallenna-johto-ja-hallintokorvaukset-2019-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map
                                     (fn [kuukausi]
                                       (* (or (:tunnit kuukausi) 0) (or (:tuntipalkka kuukausi) 0)))
                                     (flatten (map :kuukaudet (:johto-ja-hallintokorvaukset-2019 johto-ja-hallinto-tietomalli-2019)))))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+ (merge johto-ja-hallinto-tietomalli-2019
                                                                                {:urakka-id urakka-id
                                                                                 :hoitovuoden-alkuvuosi 2024}))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        vastaus-summa (apply + (map :summa (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset])))]

    ;; Ei ole erroreita
    (is (nil? (:error vastaus)))
    (is (= (bigdec (round2 tietomallin-summa 2)) (bigdec (round2 vastaus-summa 2))))))

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

    (is (= (bigdec muokattu-summa) (bigdec muokattu-vastaus-summa)))))

(deftest muokkaa-johto-ja-hallintokorvaukset-2019-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Tallenna ensin tietomallin tiedot
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-johto-ja-hallintokorvaukset-2019 +kayttaja-jvh+ (merge johto-ja-hallinto-tietomalli-2019
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

(deftest tallenna-hoidonjohtopalkkiot-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tietomallin-summa (apply + (map :summa (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli)))
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ (merge hoidonjohtopalkkiot-tietomalli
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
                    :tallenna-hoidonjohtopalkkiot +kayttaja-jvh+ (merge hoidonjohtopalkkiot-tietomalli
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

(deftest vahvista-tavoite-ja-kattohinta-ei-onnistu
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
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

        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        _ (is (= (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe]) "Kustannustietoja puuttuu. Tarkista Kilpailutettavat hankinnat, Erillishankinnat, Hoidonjohtopalkkiot"))

        _ (u (format "update urakka set indeksi = null WHERE id = %s" urakka-id)) ;; Poistetaan urakan indeksi
        vastaus-indeksi (try
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                            :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                          (catch Exception e
                            (println "Tapahtui virhe:" (.getMessage e))
                            {:error (.getMessage e)}))

        _ (is (= (get-in vastaus-indeksi [:kustannussuunnitelma :vahvistus-virhe])
                (format "Indeksit puuttuvat hoitovuodelle %s. Indeksit on lisättävä ennen vahvistusta. Kustannustietoja puuttuu. Tarkista Kilpailutettavat hankinnat, Erillishankinnat, Hoidonjohtopalkkiot"
                  hoitovuoden-alkuvuosi)))]))

(deftest vahvista-ja-kumoa-tavoite-ja-kattohinta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024

        ;; Lisätään ensin kilpailutettavat hankinnat
        ;; ;; Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (poista-yhteenvetorivi hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id (:erillishankinnat erillishankinnat-tietomalli))
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli))
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id (:johto-ja-hallintokorvaukset-2019 johto-ja-hallinto-tietomalli-2019))

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous)

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
    (is (= 9 (count (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))) "Johto- ja hallintokorvauksia pitäisi olla 9 toimenkuvaa")
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

(deftest vahvista-kattohinta-toimii-tarkennettuna
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-sopimus-id-urakka-idlla urakka-id)
        hoitovuoden-alkuvuosi 2024
        alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
        loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))

        ;; Lisätään ensin kilpailutettavat hankinnat - Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (poista-yhteenvetorivi hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id (:erillishankinnat erillishankinnat-tietomalli))
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli))
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id (:johto-ja-hallintokorvaukset-2019 johto-ja-hallinto-tietomalli-2019))

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
            (:db jarjestelma) urakka-id kayttaja-id kattohintakerroin tarjous)

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
