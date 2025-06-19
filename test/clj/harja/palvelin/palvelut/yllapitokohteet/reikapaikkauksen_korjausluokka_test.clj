(ns harja.palvelin.palvelut.yllapitokohteet.reikapaikkauksen-korjausluokka-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset :as reikapaikkaus-palvelu]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.tyokalut.paikkaus-test :refer :all]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :reikapaikkaukset (component/using
                              (reikapaikkaus-palvelu/->Reikapaikkaukset)
                              [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn default-reikapaikkaus [ulkoinen-id urakka-id]
  {:luoja-id 1
   :urakka-id urakka-id
   :tunniste ulkoinen-id
   :tie 20
   :aosa 1
   :aet 1200
   :losa 1
   :luotu nil
   :alkuaika nil
   :loppuaika nil
   :let 1300
   :yksikko "m2"
   :tyomenetelma-id 1
   :maara 123
   :kustannus 1234.5})

;; Tehdään reikäpaikkaus, josta tiedetään, että se on 100 metriä korjausluokan päällä ja verrataan geometrioista, että tuleeko sama mitta
(deftest reikapaikkauksen-pkluokka-testi
  (let [;; Luodaan feikki korjausluokka tauluun - Määritellään sen pituudeksi annetulla tiellä 400m
        pk {:tie 18747 :aosa 1 :losa 1 :aet 0 :let 4000}
        ;; Generoidaan geometria itse keksitylle korjausluokalle
        pkgeom_format (:tieosoitteelle_viiva (first (q-map (format "(SELECT tieosoitteelle_viiva(%s::INTEGER,  %s::INTEGER, %s::INTEGER, %s::INTEGER, %s::INTEGER)::geometry)",
                                                             (:tie pk) (:aosa pk) (:aet pk) (:losa pk) (:let pk)))))

        ;; Lisätään itse keksitty päällysteen_korjausluokka tietokantaan
        _ (u (format "INSERT INTO paallysteen_korjausluokka (tie, aosa, aet, losa, let, korjausluokka, paivitetty, geometria)
         VALUES (%s, %s, %s, %s, %s, '%s', NOW(), '%s'::geometry)",
               (:tie pk) (:aosa pk) (:aet pk) (:losa pk) (:let pk) "PK1" pkgeom_format))

        haku-sql (format "SELECT ST_BUFFER(ST_UNION('%s'::geometry), 10, 'endcap=flat')
                    FROM paallysteen_korjausluokka p
                         WHERE p.korjausluokka = 'PK1'
                                AND p.tie = %s
                                       AND p.aosa = %s
                                              AND p.losa = %s;"
                   pkgeom_format (:tie pk) (:aosa pk) (:losa pk))
        _ (q-map haku-sql)

        ;; Luodaan reikäpaikkaus, joka käyttää tuota tieosoitetta ja korjausluokkaa
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        ulkoinen-id (rand-int 1000000)
        reikapaikkaus (-> (default-reikapaikkaus ulkoinen-id urakka-id)
                        (assoc :tie (:tie pk)
                          :aosa (:aosa pk)
                          :losa (:losa pk)
                          :aet (:aet pk)
                          :let (:let pk)))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-reikapaikkaus
                                    +kayttaja-jvh+
                                    reikapaikkaus)

        ;; Hae reikäpaikaukset
        haku-params {:tr nil
                     :aikavali nil
                     :urakka-id urakka-id}
        haetut-reikapaikkaukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-reikapaikkaukset
                                  +kayttaja-jvh+
                                  haku-params)
        ;; Filtteröidään haluttu reikäpaikkaus vastauksesta
        tallennettu-reikapaikkaus (first (filter #(= ulkoinen-id (:tunniste %)) haetut-reikapaikkaukset))]

    (is (= "PK1" (:pkluokka tallennettu-reikapaikkaus)))))
