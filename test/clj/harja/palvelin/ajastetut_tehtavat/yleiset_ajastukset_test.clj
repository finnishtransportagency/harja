(ns harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset :as yleiset-ajastukset]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-paattyneet-urakat-poistaa-lisaoikeudet
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        kayttaja-id (hae-kayttajan-id-kayttajanimella "jvh")
        integraatio-id (hae-kayttajan-id-kayttajanimella "Integraatio")]

    (testing "Lisätään käyttäjälle lisäoikeus urakkaan"
      (u (format "INSERT INTO kayttajan_lisaoikeudet_urakkaan (kayttaja, urakka, luoja, luotu)
                  VALUES (%s, %s, %s, NOW())" kayttaja-id urakka-id integraatio-id))
      (let [lisaoikeus (q-map (format "SELECT * FROM kayttajan_lisaoikeudet_urakkaan
                                        WHERE kayttaja = %s AND urakka = %s AND (poistettu IS NULL OR poistettu = FALSE)"
                                kayttaja-id urakka-id))]
        (is (= 1 (count lisaoikeus))
          "Käyttäjällä pitäisi olla lisäoikeus urakkaan")))

    (testing "Asetetaan urakan loppupvm 120 päivää sitten (osuu 90-180 pv haarukkaan)"
      (u (format "UPDATE urakka SET loppupvm = current_date - INTERVAL '120 days' WHERE id = %s" urakka-id)))

    (testing "Ajetaan ajastettu tarkistus, joka poistaa lisäoikeudet päättyneiltä urakoilta"
      (yleiset-ajastukset/tarkista-paattyneet-urakat db))

    (testing "Lisäoikeus on poistettu käyttäjältä"
      (let [lisaoikeus (q-map (format "SELECT * FROM kayttajan_lisaoikeudet_urakkaan
                                        WHERE kayttaja = %s AND urakka = %s AND (poistettu IS NULL OR poistettu = FALSE)"
                                kayttaja-id urakka-id))]
        (is (= 0 (count lisaoikeus))
          "Käyttäjän lisäoikeus urakkaan pitäisi olla poistettu")))))

