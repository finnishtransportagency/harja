(ns harja.palvelin.palvelut.lupaukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lupaukset :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :as testi]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [clj-time.coerce :as tc]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-lupaustiedot (component/using
                                                   (->Lupaukset {:kehitysmoodi true})
                                                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(defn hae-urakan-lupaustiedot [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-lupaustiedot
                  kayttaja
                  tiedot))

(defn- vastaa-lupaukseen [lupaus-vastaus]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :vastaa-lupaukseen
                  +kayttaja-jvh+
                  lupaus-vastaus))

(defn- kommentit [tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :lupauksen-kommentit
                  +kayttaja-jvh+
                  tiedot))

(defn- lisaa-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :lisaa-lupauksen-kommentti
                  kayttaja
                  tiedot))

(defn- poista-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :poista-lupauksen-kommentti
                  kayttaja
                  tiedot))

(defn- etsi-lupaus [lupaukset id]
  (->> lupaukset
       vals
       flatten
       (filter #(= id (:lupaus-id %)))
       first))

(defn- etsi-ryhma [ryhmat jarjestys-numero]
  (first (filter #(= jarjestys-numero (:jarjestys %)) ryhmat)))

(deftest urakan-lupaustietojen-haku-toimii
  (let [vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  {:urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                   :urakan-alkuvuosi 2021})
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (etsi-ryhma ryhmat 1)
        ryhma-2 (etsi-ryhma ryhmat 2)
        ryhma-3 (etsi-ryhma ryhmat 3)
        ryhma-4 (etsi-ryhma ryhmat 4)
        ryhma-5 (etsi-ryhma ryhmat 5)
        lupaukset (:lupaukset vastaus)]
    (is (= 1 (:id sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 76 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")

    (is (= 16 (:pisteet ryhma-1)) "ryhmä 1 pisteet")
    (is (= 14 (:kyselypisteet ryhma-1)) "ryhmä 1 kyselypisteet")
    (is (= 30 (:pisteet-max ryhma-1)) "ryhmä 1 maksimipisteet")
    (is (= 30 (:pisteet-ennuste ryhma-1)) "ryhmä 1 piste-ennuste")

    (is (= 10 (:pisteet ryhma-2)) "ryhmä 2 pisteet")
    (is (= 0 (:kyselypisteet ryhma-2)) "ryhmä 2 kyselypisteet")
    (is (= 10 (:pisteet-max ryhma-2)) "ryhmä 2 maksimipisteet")
    (is (= 10 (:pisteet-ennuste ryhma-2)) "ryhmä 2 piste-ennuste")

    (is (= 20 (:pisteet ryhma-3)) "ryhmä 3 pisteet")
    (is (= 0 (:kyselypisteet ryhma-3)) "ryhmä 3 kyselypisteet")
    (is (= 20 (:pisteet-max ryhma-3)) "ryhmä 3 maksimipisteet")
    (is (= 20 (:pisteet-ennuste ryhma-3)) "ryhmä 3 piste-ennuste")

    (is (= 15 (:pisteet ryhma-4)) "ryhmä 4 pisteet")
    (is (= 0 (:kyselypisteet ryhma-4)) "ryhmä 4 kyselypisteet")
    (is (= 15 (:pisteet-max ryhma-4)) "ryhmä 4 maksimipisteet")
    (is (= 15 (:pisteet-ennuste ryhma-4)) "ryhmä 4 piste-ennuste")

    (is (= 25 (:pisteet ryhma-5)) "ryhmä 5 pisteet")
    (is (= 0 (:kyselypisteet ryhma-5)) "ryhmä 5 kyselypisteet")
    (is (= 25 (:pisteet-max ryhma-5)) "ryhmä 5 maksimipisteet")
    (is (= 25 (:pisteet-ennuste ryhma-5)) "ryhmä 5 piste-ennuste")

    (is (= 100 (->> ryhmat (map :pisteet-max) (reduce +))))
    (is (= 100 (get-in vastaus [:yhteenveto :pisteet :maksimi]))
        "koko hoitovuoden piste-maksimi")
    (is (= 100 (get-in vastaus [:yhteenveto :pisteet :ennuste]))
        "koko hoitovuoden piste-ennuste")

    (is (= 5 (count lupaukset)) "lupausten määrä")))

(deftest piste-ennuste
  (let [paivitys-tulos (vastaa-lupaukseen {:id 2
                                           :vastaus false})
        vastaus (hae-urakan-lupaustiedot
                  +kayttaja-jvh+
                  {:urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                   :urakan-alkuvuosi 2021
                   :valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                                        #inst "2022-09-30T20:59:59.000-00:00"]})
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (etsi-ryhma ryhmat 1)
        lupaukset (:lupaukset vastaus)
        lupaus-2 (etsi-lupaus lupaukset 2)
        lupaus-3 (etsi-lupaus lupaukset 3)]
    (is paivitys-tulos)
    (is (= 30 (:pisteet-max ryhma-1)) "ryhmä 1 maksimipisteet")
    (is (= 0 (:pisteet-ennuste lupaus-2)) "lupauksen 2 piste-ennuste")
    (is (= 14 (:pisteet-ennuste lupaus-3)) "lupauksen 3 piste-ennuste")
    (is (= 22 (:pisteet-ennuste ryhma-1)) "ryhmä 1 piste-ennuste")
    (is (= 92 (get-in vastaus [:yhteenveto :pisteet :ennuste]))
        "koko hoitovuoden piste-ennuste")))

(deftest urakan-lupauspisteiden-tallennus-toimii-insert
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:pisteet 67
                                 :id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        lupaukset (:lupaukset vastaus)]
    (is (= 67 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")
    (is (= 5 (count lupaukset)) "lupausten määrä")))

(deftest urakan-lupauspisteiden-tallennus-vaatii-oikean-urakkaidn
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-luvatut-pisteet +kayttaja-jvh+
                                {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                 :pisteet 67, :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-luvatut-pisteet +kayttaja-jvh+
                                                         {:id (hae-iin-maanteiden-hoitourakan-lupaussitoutumisen-id)
                                                          :pisteet 167
                                                          :urakka-id (hae-muhoksen-paallystysurakan-id)})))]))

(deftest lisaa-lupaus-vastaus
  (let [lupaus-vastaus {:lupaus-id 6
                        :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                        :kuukausi 12
                        :vuosi 2021
                        :paatos false
                        :vastaus true
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))        ; Ei piitata muista avaimista.
           lupaus-vastaus)
        "Tallennetut arvot ovat palautetaan")
    (is (thrown? Exception (vastaa-lupaukseen lupaus-vastaus))
        "Samalle lupaus-urakka-kuukaus-vuosi -yhdistelmälle ei voi lisätä toista vastausta.")))

(deftest paivita-lupaus-vastaus
  (let [lupaus-vastaus {:id 2
                        :vastaus false
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))        ; Ei piitata muista avaimista.
           lupaus-vastaus)
        "Tallennetut arvot palautetaan."))

  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:id 9873456387435
                                 :vastaus false
                                 :lupaus-vaihtoehto-id nil}))
      "Olematon lupaus-vastaus-id heittää poikkeuksen.")

  (let [lupaus-vastaus {:id 2
                        :vastaus nil
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))
           lupaus-vastaus)
        "Boolean-vastauksen voi asettaa takaisin nil-arvoon."))

  (let [lupaus-vastaus {:id 3
                        :vastaus nil
                        :lupaus-vaihtoehto-id nil}
        tulos (vastaa-lupaukseen lupaus-vastaus)]
    (is (= (select-keys tulos (keys lupaus-vastaus))
           lupaus-vastaus)
        "Monivalintavastauksen voi asettaa takaisin nil-arvoon.")))

(deftest tarkista-sallitut-kuukaudet
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 1
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 6
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
      "Lupaus 1:lle ei voi lisätä kirjausta kuukaudelle 6 (vain päätöksen)")
  (is (vastaa-lupaukseen
        {:lupaus-id 1
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 6
         :vuosi 2021
         :paatos true
         :vastaus true
         :lupaus-vaihtoehto-id nil})
      "Lupaus 1:lle voi lisätä päätöksen kuukaudelle 6 (ei kirjausta)")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 6
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 6
                                 :vuosi 2021
                                 :paatos true
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
      "Lupaus 6:lle ei voi lisätä päätöstä kuukaudelle 6 (vain kirjauksen)")
  (is (vastaa-lupaukseen
        {:lupaus-id 6
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 6
         :vuosi 2021
         :paatos false
         :vastaus true
         :lupaus-vaihtoehto-id nil})
      "Lupaus 6:lle voi lisätä kirjauksen kuukaudelle 6 (ei päätöstä)")
  (is (vastaa-lupaukseen
        {:lupaus-id 4
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 1
         :vuosi 2021
         :paatos true
         :vastaus true
         :lupaus-vaihtoehto-id nil})
      "Lupaus 4:lle voi lisätä päätöksen mille tahansa kuukaudelle (paatos-kk = 0)"))

#_(deftest tarkista-kuukausi-menneisyydessa
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 1
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 6
                                 :vuosi 2021
                                 :nykyhetki (pvm/suomen-aikavyohykkeessa
                                              (tc/from-string "2021-06-30"))
                                 :paatos true
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
      "Lupaus 1:lle ei voi lisätä kirjausta kuukaudelle 6 (vain päätöksen)")
  (is (vastaa-lupaukseen
        {:lupaus-id 1
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 6
         :vuosi 2021
         :paatos true
         :vastaus true
         :lupaus-vaihtoehto-id nil})
      "Lupaus 1:lle voi lisätä päätöksen kuukaudelle 6 (ei kirjausta)"))

(deftest tarkista-monivalinta-vastaus
  (is (vastaa-lupaukseen
        {:lupaus-id 5
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 10
         :vuosi 2021
         :paatos false
         :vastaus nil
         :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 5))})
      "Lupaus 5:lle voi antaa sille kuuluvan vaihtoehdon.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 5
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus nil
                                 :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 3))}))
      "Lupaus 5:lle ei voi antaa lupaus 3:n vaihtoehtoa.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 5
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus true
                                 :lupaus-vaihtoehto-id nil}))
      "Lupaus 5:lle ei voi antaa boolean-vastausta."))

(deftest tarkista-boolean-vastaus
  (is (vastaa-lupaukseen
        {:lupaus-id 6
         :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
         :kuukausi 10
         :vuosi 2021
         :paatos false
         :vastaus true
         :lupaus-vaihtoehto-id nil})
      "Lupaus 6:lle voi antaa boolean-vastauksen.")
  (is (thrown? AssertionError (vastaa-lupaukseen
                                {:lupaus-id 6
                                 :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :kuukausi 10
                                 :vuosi 2021
                                 :paatos false
                                 :vastaus nil
                                 :lupaus-vaihtoehto-id (ffirst (hae-lupaus-vaihtoehdot 3))}))
      "Lupaus 6:lle ei voi antaa monivalinta-vaihtoehtoa."))

(deftest kommentti-test
  (let [lupaus-tiedot {:lupaus-id 4
                       :urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                       :kuukausi 4
                       :vuosi 2021}]
    (is (empty? (kommentit lupaus-tiedot))
        "Lupauksella ei ole vielä kommentteja.")
    (let [kommentti-str-a "Ensimmäinen kommentti"
          kommentti-a (merge lupaus-tiedot
                     {:kommentti kommentti-str-a})
          kommentti-str-b "Toinen kommentti"
          kommentti-b (merge lupaus-tiedot
                      {:kommentti kommentti-str-b})
          tulos-a (lisaa-kommentti +kayttaja-jvh+ kommentti-a)
          ;; TODO: urakoitsijan käyttäjä toimimaan testissä
          ;tulos-b (lisaa-kommentti +kayttaja-yit_uuvh+ kommentti-b)
          tulos-b (lisaa-kommentti +kayttaja-jvh+ kommentti-b)
          listaus (kommentit lupaus-tiedot)]
      (is (number? (:kommentti-id tulos-a)))
      (is (number? (:kommentti-id tulos-b)))
      (is (= kommentti-a (select-keys tulos-a (keys kommentti-a)))
          "Kommentti A tallentuu oikein.")
      (is (= kommentti-b (select-keys tulos-b (keys kommentti-b)))
          "Kommentti B tallentuu oikein.")
      (is (= 2 (count listaus))
          "Listaus palauttaa kaksi kommenttia.")
      (is (= [kommentti-str-a kommentti-str-b]
             (map :kommentti listaus))
          "Kommentit on järjestetty vanhimmasta uusimpaan.")
      (is (thrown? SecurityException (poista-kommentti +kayttaja-yit_uuvh+ {:id (:id tulos-a)}))
          "Toisen tekemää kommenttia ei saa poistaa.")
      (is (poista-kommentti +kayttaja-jvh+ {:id (:id tulos-a)})
          "Oman kommentin poisto onnistuu.")
      (is (= [true false]
             (map :poistettu (kommentit lupaus-tiedot)))
          "Kommentti A on poistettu."))))
