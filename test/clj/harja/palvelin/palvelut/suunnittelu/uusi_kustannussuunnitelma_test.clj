(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]))

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


(deftest hae-kilpailutettavat-hankinnat-tietokannasta-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2024
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi hankinnat-tietomalli)
        tiedot {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi}
        kilpailutettavat-hankinnat (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot +kayttaja-jvh+ tiedot)
        hankinnat (get-in kilpailutettavat-hankinnat [:kustannussuunnitelma :kilpailutettavat-hankinnat])]

    (is (= (count (:toimenpiteet hankinnat)) 8))
    (is (= (:nimi (first (:toimenpiteet hankinnat))) "Talvihoito laaja TPI"))
    (is (= (:nimi (last (:toimenpiteet hankinnat))) "Yhteensä"))))

(deftest tallenna-kilpailutettavat-hankinnat-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        hoitovuoden-alkuvuosi 2024
        tietomallin-alkukausisumma (apply + (map :alkukausi (butlast (:toimenpiteet hankinnat-tietomalli))))
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
        alkukausi-tietokannasta (q-map (format "SELECT SUM(summa) as summa FROM kiinteahintainen_tyo
                                                  WHERE sopimus = %s
                                                    AND vuosi = %s AND kuukausi IN (10,11,12)"
                                         sopimus-id hoitovuoden-alkuvuosi))]
                                         sopimus-id hoitovuoden-alkuvuosi))]

    (is (= (bigdec tietomallin-alkukausisumma) (bigdec (:summa (first alkukausi-tietokannasta)))))))

(deftest tallenna-kilpailutettavat-hankinnat-rajapinnasta-ei-toimi
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+ {:jee "jee"})
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]

    (is (= (str/includes? (:error vastaus) "Palvelun :tallenna-kilpailutettavat-hankinnat kysely ei ole validi.")))))

(deftest tallenna-kilpailutettavat-hankinnat-rajapinnasta-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kilpailutettavat-hankinnat +kayttaja-jvh+
                    (merge
                      {:urakka-id urakka-id}
                      hankinnat-tietomalli))
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]
    (is (= (nil? (:error vastaus))))))
