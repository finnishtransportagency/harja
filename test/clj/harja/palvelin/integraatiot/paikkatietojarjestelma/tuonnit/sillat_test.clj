(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt
             [konversio :as konv]
             [siltatarkastukset :as q-tarkastukset]
             [sillat :as q-sillat]]
            [harja.geo :as geo]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as sillat])
  (:import (com.vividsolutions.jts.geom Point Coordinate GeometryFactory)
           (com.vividsolutions.jtsexample.geom ExtendedCoordinateSequence)
           (java.util Date)))

(use-fixtures :once tietokanta-fixture)

(def silta-tuonti
  {:y 7056414
   :kuntakoodi 762
   :siltanimi "Testisilta"
   :ajr 0
   :tie 22
   :aosa 1
   :aet 0
   :ely_lyhenn "Pop"
   :trex_oid "123456789"
   :loppupvm ""
   :rakennety "Jämäkkä silta"
   :the_geom (Point. (ExtendedCoordinateSequence. (into-array Coordinate [(Coordinate. 428022.4972067006 7210433.780978538)])) (GeometryFactory.))
   :muutospvm ""
   :loc_error "NO ERROR"
   :status 1
   :siltanro 123
   :silta_id 123
   :lakkautpvm ""
   :ualue (ffirst (q "SELECT alueurakkanro FROM alueurakka WHERE nimi='Oulu';"))})

(def odotettu-vastaus
  (doall {:siltanimi "Testisilta"
          :yhdistelmapaino nil
          :trex_oid "123456789"
          :lakkautuspvm nil
          :loppupvm nil
          :ajoneuvopaino nil
          :luoja 1
          :siltatunnus "O-123"
          :alue {:type :point
                 :coordinates [428022.4972067006 7210433.780978538]}
          :askelipaino nil
          :muutospvm nil
          :tr_loppuosa nil
          :muokkaaja nil
          :tr_numero 22
          :status 1
          :siltaid 123
          :urakat (vec (first (q "SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'")))
          :siltanro 123
          :telipaino nil
          :tr_loppuetaisyys nil
          :tr_alkuetaisyys 0
          :tyyppi "Jämäkkä silta"
          :muokattu nil
          :tr_alkuosa 1}))

(deftest luo-ja-paivita-silta
  (let [sillat-ennen (ffirst (q "SELECT count(*) FROM silta;"))]
    (testing "Luodaan silta"
      (let [lisaa-silta (-> (sillat/vie-silta-entry ds silta-tuonti)
                            (update :alue #(geo/pg->clj %))
                            (update :urakat #(konv/pgarray->vector %))
                            (dissoc :luotu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= lisaa-silta odotettu-vastaus))
        (is (= sillat-ennen (dec sillat-jalkeen)))))

    (testing "Päivitetään silta"
      (let [paivita-silta (sillat/vie-silta-entry ds (assoc silta-tuonti :siltanimi "Testisilta päivitetty"))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= paivita-silta 1))
        (is (= sillat-ennen (dec sillat-jalkeen)))))

    (testing "Sillan aktiivinen urakka vaihtuu"
      (let [silta-tuonti (assoc silta-tuonti :ualue (ffirst (q "SELECT alueurakkanro FROM alueurakka WHERE nimi='Oulu Testi';")))
            paivita-silta (sillat/vie-silta-entry ds silta-tuonti)
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE siltanro = " (:siltanro silta-tuonti))))
                          (update :alue #(geo/pg->clj %))
                          (update :urakat #(into #{} (konv/pgarray->vector %)))
                          (dissoc :luotu :muokattu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= hae-silta (-> odotettu-vastaus (assoc :urakat (into #{} (mapcat identity (q "SELECT id FROM urakka WHERE nimi IN ('Aktiivinen Oulu Testi', 'Oulun alueurakka 2005-2012')")))
                                                     :muokkaaja 1)
                             (dissoc :muokattu))))
        (is (= paivita-silta 1))
        (is (= sillat-ennen (dec sillat-jalkeen)))))

    (testing "Päivitetään silta uudestaan"
      (let [silta-tuonti (assoc silta-tuonti :ualue (ffirst (q "SELECT alueurakkanro FROM alueurakka WHERE nimi='Oulu Testi';"))
                                             :siltanimi "Testisilta päivitetty")
            paivita-silta (sillat/vie-silta-entry ds silta-tuonti)
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE siltanro = " (:siltanro silta-tuonti))))
                          (update :alue #(geo/pg->clj %))
                          (update :urakat #(into #{} (konv/pgarray->vector %)))
                          (dissoc :luotu :muokattu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= hae-silta (-> odotettu-vastaus
                             (assoc :urakat (into #{} (mapcat identity (q "SELECT id FROM urakka WHERE nimi IN ('Aktiivinen Oulu Testi', 'Oulun alueurakka 2005-2012')")))
                                    :muokkaaja 1
                                    :siltanimi "Testisilta päivitetty")
                             (dissoc :muokattu))))
        (is (= paivita-silta 1))
        (is (= sillat-ennen (dec sillat-jalkeen)))))))

(deftest urakan-vaihtaminen
  (let [sillat-ennen (ffirst (q "SELECT count(*) FROM silta;"))]
    (testing "Siirrä silta toiseen urakkaan"
      (let [uusi-urakka (first (q-map "SELECT nimi, urakkanro FROM urakka WHERE nimi='Aktiivinen Kajaani Testi';"))
            silta-tuonti (assoc silta-tuonti :ualue (:urakkanro uusi-urakka))
            paivita-silta (sillat/vie-silta-entry ds silta-tuonti)
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE siltanro = " (:siltanro silta-tuonti))))
                          (update :alue #(geo/pg->clj %))
                          (update :urakat #(into #{} (konv/pgarray->vector %)))
                          (dissoc :luotu :muokattu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= hae-silta (-> odotettu-vastaus
                             (assoc :urakat (into #{} (mapcat identity (q (str "SELECT id FROM urakka WHERE nimi IN ('" (:nimi uusi-urakka) "', 'Oulun alueurakka 2005-2012')"))))
                                    :muokkaaja 1)
                             (dissoc :muokattu))))
        (is (= paivita-silta 1))
        (is (= sillat-ennen sillat-jalkeen))))

    (testing "Siirrä silta toiseen urakkaan kun sillalle on merkattu tarkastuksia entiseen urakkaan"
      (q-tarkastukset/luo-siltatarkastus<! ds {:silta (:silta-id (first (q-sillat/hae-sillan-tiedot ds {:trex-oid (:trex_oid odotettu-vastaus) :siltatunnus nil :siltaid nil})))
                                               :urakka (ffirst (q "SELECT id FROM urakka WHERE nimi='Aktiivinen Kajaani Testi';"))
                                               :tarkastusaika (Date.)
                                               :tarkastaja "Foo"
                                               :luoja 1
                                               :ulkoinen_id "123"
                                               :lahde "harja-ui"})
      (let [uusi-urakka (first (q-map "SELECT nimi, urakkanro FROM urakka WHERE nimi='Aktiivinen Oulu Testi';"))
            silta-tuonti (assoc silta-tuonti :ualue (:urakkanro uusi-urakka))
            paivita-silta (sillat/vie-silta-entry ds silta-tuonti)
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE siltanro = " (:siltanro silta-tuonti))))
                          (update :alue #(geo/pg->clj %))
                          (update :urakat #(into #{} (konv/pgarray->vector %)))
                          (dissoc :luotu :muokattu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= hae-silta (-> odotettu-vastaus
                             (assoc :urakat (into #{} (mapcat identity (q (str "SELECT id FROM urakka WHERE nimi IN ('Aktiivinen Kajaani Testi', 'Oulun alueurakka 2005-2012')"))))
                                    :muokkaaja 1)
                             (dissoc :muokattu))))
        (is (= paivita-silta 1))
        (is (= sillat-ennen sillat-jalkeen))))))