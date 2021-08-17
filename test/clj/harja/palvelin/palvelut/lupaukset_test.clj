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
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-lupaustiedot (component/using
                                                   (->Lupaukset)
                                                   [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(deftest urakan-lupaustietojen-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-lupaustiedot +kayttaja-jvh+
                                {:urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
                                 :urakan-alkuvuosi 2021})
        sitoutuminen (:lupaus-sitoutuminen vastaus)
        ryhmat (:lupausryhmat vastaus)
        ryhma-1 (first (filter #(= 1 (:jarjestys %)) ryhmat))
        ryhma-2 (first (filter #(= 2 (:jarjestys %)) ryhmat))
        ryhma-3 (first (filter #(= 3 (:jarjestys %)) ryhmat))
        ryhma-4 (first (filter #(= 4 (:jarjestys %)) ryhmat))
        ryhma-5 (first (filter #(= 5 (:jarjestys %)) ryhmat))
        lupaukset (:lupaukset vastaus)]
    (is (= 1 (:id sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 76 (:pisteet sitoutuminen)) "luvattu-pistemaara oikein")
    (is (= 5 (count ryhmat)) "lupausryhmien määrä")
    (is (= 16 (:pisteet ryhma-1)) "ryhmä 1 pisteet")
    (is (= 14 (:kyselypisteet ryhma-1)) "ryhmä 1 kyselypisteet")
    (is (= 10 (:pisteet ryhma-2)) "ryhmä 2 pisteet")
    (is (= 0 (:kyselypisteet ryhma-2)) "ryhmä 2 kyselypisteet")
    (is (= 20 (:pisteet ryhma-3)) "ryhmä 3 pisteet")
    (is (= 0 (:kyselypisteet ryhma-3)) "ryhmä 3 kyselypisteet")
    (is (= 15 (:pisteet ryhma-4)) "ryhmä 4 pisteet")
    (is (= 0 (:kyselypisteet ryhma-4)) "ryhmä 4 kyselypisteet")
    (is (= 25 (:pisteet ryhma-5)) "ryhmä 5 pisteet")
    (is (= 0 (:kyselypisteet ryhma-5)) "ryhmä 5 kyselypisteet")
    (is (= 5 (count lupaukset)) "lupausten määrä")))


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

(defn- vastaa-lupaukseen [lupaus-vastaus]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :vastaa-lupaukseen
                  +kayttaja-jvh+
                  lupaus-vastaus))

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
        "Tallennetut arvot palautetaan.")
    (is (thrown? AssertionError (vastaa-lupaukseen
                                  {:id 9873456387435
                                   :vastaus false
                                   :lupaus-vaihtoehto-id nil}))
        "Olematon lupaus-vastaus-id heittää poikkeuksen.")))

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

(defn- kommentit [tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :kommentit
                  +kayttaja-jvh+
                  tiedot))

(defn- lisaa-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :lisaa-kommentti
                  kayttaja
                  tiedot))

(defn- poista-kommentti [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :poista-kommentti
                  kayttaja
                  tiedot))

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
