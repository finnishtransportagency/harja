(ns harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat :as tg]
            [harja.testi :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(def +testiurl+ "http://example.com")

(def +testivastaus+ "{
 \"type\": \"FeatureCollection\",
 \"totalFeatures\": 34967,
 \"features\": [
   {
     \"type\": \"Feature\",
     \"id\": \"turvalaitteet.fid-552e7bd6_15b85715fb3_e36\",
     \"geometry\": {
       \"type\": \"Point\",
       \"coordinates\": [
         484088.458858226,
         6696406.72164135
       ]
     },
     \"geometry_name\": \"SHAPE\",
     \"properties\": {
       \"TLNUMERO\": 2639,
       \"OMISTAJA\": \"Liikennevirasto\",
       \"TILA\": \"VAHVISTETTU\",
       \"PATA_TYYP\": 63,
       \"TKLNUMERO\": 56,
       \"TY_JNR\": 10,
       \"TOTI_TYYP\": 1,
       \"PAKO_TYYP\": 5,
       \"NAVL_TYYP\": 5,
       \"RAKT_TYYP\": null,
       \"RAK_VUOSI\": null,
       \"SUBTYPE\": \"KELLUVA\",
       \"SIJAINTIS\": \"N. 850 m Koukkusaaren I-puolella.\",
       \"SIJAINTIR\": \"Ca 850 m O om Krokö.\",
       \"NIMIS\": \"Koukkusaaren matala\",
       \"NIMIR\": \"Koukkusaaren matala\",
       \"MITT_PVM\": null,
       \"VAHV_PVM\": null,
       \"PAIV_PVM\": \"2008-08-19Z\",
       \"VALAISTU\": \"E\",
       \"FASADIVALO\": 0,
       \"HUIPPUMERK\": 0,
       \"TUTKAHEIJ\": 0,
       \"VAYLAT\": \"5485\",
       \"IRROTUS_PVM\": \"2017-04-15T23:02:54\"
     }
   },
   {
     \"type\": \"Feature\",
     \"id\": \"turvalaitteet.fid-552e7bd6_15b85715fb3_e37\",
     \"geometry\": {
       \"type\": \"Point\",
       \"coordinates\": [
         541146.009870127,
         6706001.39829
       ]
     },
     \"geometry_name\": \"SHAPE\",
     \"properties\": {
       \"TLNUMERO\": 2672,
       \"OMISTAJA\": \"Liikennevirasto\",
       \"TILA\": \"VAHVISTETTU\",
       \"PATA_TYYP\": 63,
       \"TKLNUMERO\": 56,
       \"TY_JNR\": 10,
       \"TOTI_TYYP\": 1,
       \"PAKO_TYYP\": 5,
       \"NAVL_TYYP\": 4,
       \"RAKT_TYYP\": null,
       \"RAK_VUOSI\": null,
       \"SUBTYPE\": \"KELLUVA\",
       \"SIJAINTIS\": \"Heposaaren I-puolella.\",
       \"SIJAINTIR\": \"O om Heposaari.\",
       \"NIMIS\": \"Puuluoto etelä\",
       \"NIMIR\": \"Puuluoto södra\",
       \"MITT_PVM\": null,
       \"VAHV_PVM\": null,
       \"PAIV_PVM\": \"2014-03-19Z\",
       \"VALAISTU\": \"E\",
       \"FASADIVALO\": 0,
       \"HUIPPUMERK\": 0,
       \"TUTKAHEIJ\": 0,
       \"VAYLAT\": \"5945\",
       \"IRROTUS_PVM\": \"2017-04-15T23:02:54\"
     }
   }
 ]} ")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea nil))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-paivitysehdot
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (u "INSERT INTO geometriapaivitys (nimi) VALUES ('turvalaitteet') ON CONFLICT(nimi) DO NOTHING;")

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = NULL WHERE nimi = 'turvalaitteet';")
    (is (tg/paivitys-tarvitaan? db 10) "Päivitys tarvitaan, kun sitä ei ole koskaan tehty")

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '10' day WHERE nimi = 'turvalaitteet';")
    (is (tg/paivitys-tarvitaan? db 10) "Päivitys tarvitaan, kun se on viimeksi tehty tarpeeksi kauan sitten") ;

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '1' day WHERE nimi = 'turvalaitteet';")
    (is (false? (tg/paivitys-tarvitaan? db 10)) "Päivitystä ei tarvita, kun se on tehty tarpeeksi vasta")))


(deftest tekstiviestin-lahetys
  (with-fake-http [+testiurl+ +testivastaus+]
    (let [hae-turvalaitteet #(q "SELECT id FROM turvalaite;")]
      (is (= 0 (count (hae-turvalaitteet))) "Aluksi ei ole ainuttakaan turvalaitetta")
      (tg/paivita-turvalaitteet (:integraatioloki jarjestelma) (:db jarjestelma) +testiurl+)
      (is (= 2 (count (hae-turvalaitteet))) "Päivityksen jälkeen löytyy 2 turvalaitetta")
      (is (not
            (nil?
              (ffirst (q "SELECT viimeisin_paivitys FROM geometriapaivitys WHERE nimi = 'turvalaitteet';"))))
          "Geometriapäivitys on lokitettu"))))






