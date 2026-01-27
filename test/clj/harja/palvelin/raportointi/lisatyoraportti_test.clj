(ns harja.palvelin.raportointi.lisatyoraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.konversio :as konversio]
            [harja.testi :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

#_(deftest lisatyo-raportti-palauttaa-tyhjan-raportin-kun-parametrit-puuttuvat
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :lisatyo
                   :konteksti "urakka"
                   :urakka-id 12
                   :parametrit {}})
        taulukko (apurit/taulukko-otsikolla? vastaus "Lisätöiden kulukohdistukset")]

    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Lisätyöraportti")
    (is (= "Ei laskuja aikavälille" (:tyhja (meta taulukko))))))

(defn luo-kulu
  "Luo tällä hetkellä aina tavoitehintaisen kulun. Lisää uusi parametri, jos se on ongelma."
  [urakka-id tyyppi erapaiva kohdistustyyppi koontilaskun-kuukausi summa toimenpideinstanssi-id tehtavaryhma-id tehtava-id rahavaraus]
  {:id nil
   :urakka urakka-id
   :viite "123456781"
   :erapaiva erapaiva
   :kokonaissumma summa
   :tyyppi tyyppi
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa summa
                   :toimenpideinstanssi toimenpideinstanssi-id
                   :tehtavaryhma tehtavaryhma-id
                   :tehtava tehtava-id
                   :tyyppi kohdistustyyppi
                   :rahavaraus rahavaraus
                   :tavoitehintainen :true}]
   :koontilaskun-kuukausi koontilaskun-kuukausi})

(def uusi-kulu
  {:id nil
   :urakka (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
   :viite "6666668"
   :erapaiva #inst "2021-12-15T21:00:00.000-00:00"
   :kokonaissumma 7777
   :tyyppi "laskutettava"
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa 1337
                   :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                   :tehtava (hae-tehtavan-id-nimella "Runkopuiden poisto")
                   :tavoitehintainen :true
                   :tyyppi "lisatyo"}
                  {:kohdistus-id nil
                   :rivi 2
                   :summa 1337
                   :toimenpideinstanssi (hae-oulun-maanteiden-hoitourakan-toimenpideinstanssi "23116")
                   :tehtavaryhma (hae-tehtavaryhman-id "V - Vesakonraivaukset ja puun poisto")
                   :tehtava {:id (hae-tehtavan-id-nimella "Runkopuiden poisto")}
                   :tavoitehintainen :true
                   :tyyppi "lisatyo"}]
   :liitteet [{:liite-id 1
               :liite-tyyppi "image/png"
               :liite-nimi "pensas-2021-01.jpg"
               :liite-koko nil
               :liite-oid nil}
              {:liite-id 2
               :liite-tyyppi "image/png"
               :liite-nimi "pensas-2021-02.jpg"
               :liite-koko nil
               :liite-oid nil}]
   :koontilaskun-kuukausi "joulukuu/3-hoitovuosi"})

(deftest lisatyo-raportti-palauttaa-oikeat-tiedot-useilla-riveilla
  (let [
        tallennettu-kulu
        (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
           :kulu-kohdistuksineen uusi-kulu})
        tallennettu-id (:id tallennettu-kulu)
        _ (println "tallennettu-kulu-id:" tallennettu-id)



        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)

        korvausinvestointi (first (q-map (format "SELECT id, toimenpide FROM toimenpideinstanssi WHERE nimi = '%s' and urakka = %s"
                                           "Oulu MHU MHU Korvausinvestointi TP" urakka-id)))

        hk_alkupvm "2021-10-01"
        hk_loppupvm "2022-09-30"
        _ (lisaa-kulu-urakalle 444 "2022-10-02" urakka-id (:id korvausinvestointi) nil "lisatyo")
        hk_alkupvm2 "2022-10-01"
        aikavali_alkupvm "2019-10-01"
        hk_loppupvm2  "2023-09-30"
        aikavali_loppupvm "2020-09-30"
        _ (println "id urakka:" urakka-id)
        toimenpide-id-1 47
        toimenpide-id-2 48
        ;;toimenpide-nimi-1 (hae-toimenpide-nimi toimenpide-id-1)
        ;;toimenpide-nimi-2 (hae-toimenpide-nimi toimenpide-id-2)

        erapaiva (pvm/->pvm "16.10.2022")
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        _ (println "toimenpideinstanssi-id:" toimenpideinstanssi-id)
        tehtavaryhma-id (hae-tehtavaryhman-id "A - Talvihoito")
        _ (println "tehtavaryhma-id:" tehtavaryhma-id)
        tehtava-id nil
        talvihoitosumma 1234M

        talvihoitokulu (luo-kulu urakka-id "lisatyo" erapaiva "lisatyo" koontilaskun-kuukausi talvihoitosumma
                         toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)

        #_ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})
        query "SELECT rivi, kulu, k.urakka, summa, toimenpideinstanssi, maksueratyyppi, erapaiva, lisatyon_lisatieto, tp.nimi AS toimenpide
        FROM kulu_kohdistus kk JOIN kulu k ON kk.kulu  = k.id JOIN toimenpide tp ON tp.id = kk.toimenpideinstanssi
        WHERE maksueratyyppi = 'lisatyo' AND k.urakka = ('%s') AND erapaiva BETWEEN '%s'::DATE AND '%s'::DATE ORDER BY erapaiva;"
        ;_ (first (first (q (format "SELECT hae_seuraava_vapaa_viestinumero('%s')" puhelinnumero))))
        ;;_ (first (first (q (format "SELECT rivi, kulu, k.urakka, summa, toimenpideinstanssi, maksueratyyppi, erapaiva, lisatyon_lisatieto, tp.nimi AS toimenpide\nFROM kulu_kohdistus kk JOIN kulu k ON kk.kulu  = k.id JOIN toimenpide tp ON tp.id = kk.toimenpideinstanssi\nWHERE maksueratyyppi = 'lisatyo' AND k.urakka = :urakka AND erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE ORDER BY erapaiva;" puhelinnumero))))
        raportti (q (format query urakka-id hk_alkupvm2 hk_loppupvm2))]
    (println "Raportti:" raportti)
    ))
