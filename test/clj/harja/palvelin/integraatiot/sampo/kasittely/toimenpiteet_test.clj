(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot]))

(deftest tarkista-toimenpiteen-tallentuminen
  (tuo-toimenpide)
  (is (= 1 (count (hae-toimenpiteet))) "Luonnin jälkeen toimenpide löytyy Sampo id:llä.")
  (poista-toimenpide))

(deftest tarkista-toimenpiteen-paivittaminen
  (tuo-toimenpide)
  (tuo-toimenpide)
  (is (= 1 (count (hae-toimenpiteet))) "Tuotaessa sama toimenpide uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-toimenpide))

(deftest tarkista-urakan-asettaminen-toimenpiteelle-urakka-ensin
  (tuo-urakka)
  (tuo-toimenpide)
  (is (onko-urakka-sidottu-toimenpiteeseen?) "Toimenpide viittaa oikeaan urakkaan, kun urakka on tuotu ensin.")
  (poista-toimenpide)
  (poista-urakka))

(deftest tarkista-urakan-asettaminen-toimenpiteelle-toimenpide-ensin
  (tuo-toimenpide)
  (tuo-urakka)
  (is (onko-urakka-sidottu-toimenpiteeseen?) "Toimenpide viittaa oikeaan urakkaan, kun toimenpide on tuotu ensin.")
  (poista-toimenpide)
  (poista-urakka))

(deftest tarkista-maksuerien-ja-kustannussuunnitelmien-perustaminen
  (tuo-hanke)
  (tuo-urakka)
  (tuo-toimenpide)

  (is (= 8 (count (hae-maksuerat))) "Maksueriä on 8 kpl per toimenpideinstanssi.")
  (is (= 8 (count (hae-kustannussuunnitelmat))) "Jokaiselle maksuerälle on perustettu kustannussuunnitelma.")

  (tuo-toimenpide)
  (is (= 8 (count (hae-maksuerat))) "Toimenpiteen päivitys ei saa lisätä uusia maksueriä.")
  (is (= 8 (count (hae-kustannussuunnitelmat))) "Toimenpiteen päivitys ei saa lisätä uusia kustannussuunnitelmia.")

  (let [nimet (hae-maksuerien-nimet)]
    (is (some #(= (first %) "Talvihoito: Kokonaishintaiset") nimet))
    (is (some #(= (first %) "Talvihoito: Yksikköhintaiset") nimet))
    (is (some #(= (first %) "Talvihoito: Lisätyöt") nimet))
    (is (some #(= (first %) "Talvihoito: Indeksit") nimet))
    (is (some #(= (first %) "Talvihoito: Bonukset") nimet))
    (is (some #(= (first %) "Talvihoito: Sakot") nimet))
    (is (some #(= (first %) "Talvihoito: Äkilliset hoitotyöt") nimet))
    (is (some #(= (first %) "Talvihoito: Muut") nimet))
    (println "nimet: " nimet))

  (poista-toimenpide))

(deftest tarkista-duplikaatti-toimenpiteiden-perustaminen
  (tuo-toimenpide)
  (is (= 1 (count (hae-toimenpiteet))) "Tuonnin jälkeen löytyy toimenpide.")
  (try+
    (do
      (tuo-duplikaatti-toimenpide)
      (is false "Duplikaatin perustaminen pitäisi aiheuttaa poikkeuksen"))
    (catch [:type virheet/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus]}
      (is (.contains kuittaus "Project: TESTIURAKKA already has operation: 22111") "Oikea virhe palautetaan kuittauksessa")
      (is (= "Sampon projektille (id: TESTIURAKKA) on jo perustettu toimenpidekoodi: 22111" (:virhe (first virheet))))))
  (is (= 1 (count (hae-toimenpiteet))) "Uutta toimenpidettä jo olemassa olevalla toimenpidekoodilla ei tuotu urakalle.")
  (poista-toimenpide))

(deftest tarkista-toimenpidekoodittoman-toimenpiteiden-perustaminen
  (try+
    (do
      (tuo-toimenpidekooditon-toimenpide)
      (is false "Toimenpidekoodittoman toimenpiteen perustaminen pitäisi aiheuttaa poikkeuksen"))
    (catch [:type virheet/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus]}
      (is (.contains kuittaus "No operation code provided.") "Oikea virhe palautetaan kuittauksessa")
      (is (= "Toimenpiteelle ei ole annettu toimenpidekoodia (vv_operation)" (:virhe (first virheet))))))

  (is (= 0 (count (q "select id from toimenpideinstanssi where sampoid = 'TESTITPKTPI';")))
      "Toimenpidekooditonta toimenpidettä ei perusteta."))