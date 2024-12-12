(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteen-korjausluokka-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet]
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
          :paikkauskohteet (component/using
                             (paikkauskohteet/->Paikkauskohteet)
                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn default-paikkauskohde [ulkoinen-id urakka-id]
  {:urakka-id urakka-id
   :ulkoinen-id ulkoinen-id
   :nimi "testinimi"
   :alkupvm (pvm/->pvm "01.01.2020")
   :loppupvm (pvm/->pvm "01.02.2020")
   :paikkauskohteen-tila "valmis"
   :tie 22
   :aosa 1
   :losa 1
   :aet 10
   :let 20
   :ajorata 0
   :yksikko "jm"
   :suunniteltu-hinta 1000.00
   :suunniteltu-maara 100
   :tyomenetelma 8})

;; Tehdään paikkauskohde, josta tiedetään, että se on 100 metriä korjausluokan päällä ja verrataan geometrioista, että tuleeko sama mitta
(deftest paikkauskohteen-pkluokka-testi
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

        ;; Luodaan paikkauskohde, joka käyttää tuota tieosoitetta ja korjausluokkaa
        _ (hae-kemin-paallystysurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        ulkoinen-id (rand-int 1000000)
        paikkauskohde (-> (default-paikkauskohde ulkoinen-id urakka-id)
                        (assoc :tie (:tie pk)
                          :aosa (:aosa pk)
                          :losa (:losa pk)
                          :aet (:aet pk)
                          :let (:let pk)))
        tallennettu-paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-paikkauskohde-urakalle
                                    +kayttaja-jvh+
                                    paikkauskohde)]


    (is (= "PK1" (:pkluokka tallennettu-paikkauskohde)))))
