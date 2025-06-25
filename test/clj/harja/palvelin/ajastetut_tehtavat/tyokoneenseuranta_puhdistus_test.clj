(ns harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus :as tyokonehavaintojen-siivous]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component])
            (:use org.httpkit.fake))

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

;; Tyokonehavainto-taulusta poistetaan vanhentuneet rivit
;; Yleensä poistetaan yli 5 h vanhat rivit, mutta valaistusurakoista rivit säilytetään kolme viikkoa.

(deftest tyokonehavainto-siivous-toimii
  (let [testitietokanta (:db jarjestelma)

        ;; Tallennetaan testiä varten neljä riviä, joista kaksi siivotaan ja kaksi pitäisi jäädä tietokantaan
        ;; Testataan urakkatyypit: teiden-hoito ja valaistus
        _ (u "INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, viestitunniste, lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid) VALUES (1, 'Testi-MHU Poistettava', 1, (NOW() - INTERVAL '2 weeks'), (NOW() - INTERVAL '2 weeks'), 'KA', ST_MakePoint(429493, 7207739)::GEOMETRY, (select id from urakka where nimi = 'Oulun MHU 2019-2024'));")
        _ (u "INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, viestitunniste, lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid) VALUES (2, 'Testi-MHU Jätettävä', 2, (NOW() - INTERVAL '4 hours'), (NOW() - INTERVAL '4 hours'), 'KA', ST_MakePoint(429493, 7207739)::GEOMETRY, (select id from urakka where nimi = 'Oulun MHU 2019-2024'));")
        _ (u "INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, viestitunniste, lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid) VALUES (3, 'Testi-Valaistus Poistettava', 3, (NOW() - INTERVAL '4 weeks'), (NOW() - INTERVAL '4 weeks'), 'KA', ST_MakePoint(429493, 7207739)::GEOMETRY, (select id from urakka where nimi = 'Oulun valaistuksen palvelusopimus 2013-2050'));")
        _ (u "INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, viestitunniste, lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid) VALUES (4, 'Testi-Valaistus Jätettävä', 4, (NOW() - INTERVAL '2 weeks'), (NOW() - INTERVAL '2 weeks'), 'KA', ST_MakePoint(429493, 7207739)::GEOMETRY, (select id from urakka where nimi = 'Oulun valaistuksen palvelusopimus 2013-2050'));")

        ;; Kutsu siivoustehtävää ja tarkista tulos
        _ (tyokonehavaintojen-siivous/poista-vanhat-tyokonesijainnit testitietokanta)
        tulos-jatettavat (q "SELECT * FROM tyokonehavainto WHERE jarjestelma IN ('Testi-MHU Jätettävä', 'Testi-Valaistus Jätettävä') ")
        tulos-poistettavat (q "SELECT * FROM tyokonehavainto WHERE jarjestelma IN ('Testi-MHU Poistettava', 'Testi-Valaistus Poistettava') ")]

        (is (= (count tulos-jatettavat)  2) "Tuoreet kirjaukset löytyvät.")
        (is (empty? tulos-poistettavat) "Vanhentuneet kirjaukset on siivottu.")))



