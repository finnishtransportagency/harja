(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt
             [konversio :as konv]
             [siltatarkastukset :as q-tarkastukset]
             [sillat :as q-sillat]
             [urakat :as q-urakka]]
            [harja.geo :as geo]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as sillat]
            [harja.pvm :as pvm])
  (:import (org.locationtech.jts.geom Point Coordinate GeometryFactory)
           (org.locationtech.jts.geom.impl CoordinateArraySequence)
           (java.util Date)))

(use-fixtures :once tietokanta-fixture)

(def silta-tuonti
  {:nimi "Testisilta"
   :tieosoite {:tr_numero 22
               :tr_alkuosa 1
               :tr_alkuetaisyys 0}
   :oid "123456789"
   :loppupvm ""
   :janteet "numero:1,rakennetyypit:nimi:Jämäkkä silta,lyhenne:Jsi"
   :the_geom (Point. (CoordinateArraySequence. (into-array Coordinate [(Coordinate. 428022.4972067006 7210433.780978538)])) (GeometryFactory.))
   :paivitetty "2015-01-05"
   :tila "kaytossa"
   :nykyinen_o "nimi:Väylävirasto,ytunnus:1010547-1"
   :tunnus "O-123"
   :nykyinenku "nimi:Pohjois-Pohjanmaan ELY-keskus"})

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
          :muutospvm (pvm/->pvm "05.01.2015")
          :tr_loppuosa nil
          :muokkaaja nil
          :tr_numero 22
          :status nil
          :siltaid nil
          :poistettu false
          :urakat (vec (first (q "SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'")))
          :siltanro 123
          :telipaino nil
          :tr_loppuetaisyys nil
          :tr_alkuetaisyys 0
          :tyyppi "Jämäkkä silta"
          :muokattu nil
          :tr_alkuosa 1
          :kunnan_vastuulla false}))

(defn- vie-silta-urakalle [db silta urakka-id]
  (with-redefs [q-urakka/hae-urakka-sijainnilla (fn [_db _opts] [{:id urakka-id}])]
    (sillat/vie-silta-entry db silta)))

(deftest luo-ja-paivita-silta
  (let [sillat-ennen (ffirst (q "SELECT count(*) FROM silta;"))]
    (testing "Luodaan silta"
      (let [lisaa-silta (-> (vie-silta-urakalle ds silta-tuonti (ffirst (q "SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'")))
                            (update :alue #(geo/pg->clj %))
                            (update :urakat #(konv/pgarray->vector %))
                            (dissoc :luotu :id))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= lisaa-silta odotettu-vastaus))
        (is (= sillat-ennen (dec sillat-jalkeen)))))

    (testing "Päivitetään silta"
      (let [paivita-silta (vie-silta-urakalle ds (assoc silta-tuonti :nimi "Testisilta päivitetty") (ffirst (q "SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'")))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta;"))]
        (is (= paivita-silta 1))
        (is (= sillat-ennen (dec sillat-jalkeen)))))

    (testing "Sillan aktiivinen urakka vaihtuu"
      (let [paivita-silta (vie-silta-urakalle ds silta-tuonti (ffirst (q "SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'")))
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid silta-tuonti) "'")))
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
      (let [silta-tuonti (assoc silta-tuonti :nimi "Testisilta päivitetty")
            paivita-silta (vie-silta-urakalle ds silta-tuonti (ffirst (q "SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'")))
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid silta-tuonti) "'")))
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
      (let [uusi-urakka (first (q-map "SELECT nimi, id FROM urakka WHERE nimi='Aktiivinen Kajaani Testi';"))
            paivita-silta (vie-silta-urakalle ds silta-tuonti (:id uusi-urakka))
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid silta-tuonti) "'")))
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
      (q-tarkastukset/luo-siltatarkastus<! ds {:silta (:silta-taulun-id (first (q-sillat/hae-sillan-tiedot ds {:trex-oid (:trex_oid odotettu-vastaus) :siltatunnus (:siltatunnus odotettu-vastaus) :siltanimi (:siltanimi odotettu-vastaus)})))
                                               :urakka (ffirst (q "SELECT id FROM urakka WHERE nimi='Aktiivinen Kajaani Testi';"))
                                               :tarkastusaika (Date.)
                                               :tarkastaja "Foo"
                                               :luoja 1
                                               :ulkoinen_id "123"
                                               :lahde "harja-ui"})
      (let [uusi-urakka (first (q-map "SELECT nimi, id FROM urakka WHERE nimi='Aktiivinen Oulu Testi';"))
            paivita-silta (vie-silta-urakalle ds silta-tuonti (:id uusi-urakka))
            hae-silta (-> (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid silta-tuonti) "'")))
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

(deftest kasittele-poistettu-silta
  (testing "Ei poisteta siltaa, jos sillä on siltatarkastus aktiivisessa urakassa"
    (let [aktiivinen-urakka (first (q-map "SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'"))
          luotava-silta (assoc silta-tuonti :oid "54350234")
          _tee-silta (vie-silta-urakalle ds luotava-silta (:id aktiivinen-urakka))
          tehty-silta (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid luotava-silta) "'")))
          _tarkastus (q-tarkastukset/luo-siltatarkastus<! ds {:silta (:silta-taulun-id (first (q-sillat/hae-sillan-tiedot ds {:trex-oid (:oid luotava-silta) :siltatunnus (:tunnus luotava-silta) :siltanimi (:nimi luotava-silta)})))
                                                              :urakka (:id aktiivinen-urakka)
                                                              :tarkastusaika (Date.)
                                                              :tarkastaja "Foo"
                                                              :luoja 1
                                                              :ulkoinen_id "123"
                                                              :lahde "harja-ui"})
          siltoja-ennen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu=false"))
          _paivita-silta (vie-silta-urakalle ds (assoc luotava-silta :tila "poistettu") (:id aktiivinen-urakka))
          paivitetty-silta (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid luotava-silta) "'")))
          siltoja-jalkeen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu=false"))]
      (is (= siltoja-ennen siltoja-jalkeen))
      (is (not (:poistettu tehty-silta)))
      (is (not (:poistettu paivitetty-silta)))
      (is (some? (:loppupvm paivitetty-silta)))
      (is (= siltoja-ennen siltoja-jalkeen))))

  (testing "Poistetaan silta, vaikka sillä on siltatarkastus päättyneessä urakasssa"
    (let [paattynyt-urakka (first (q-map "SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'"))
          aktiivinen-urakka (first (q-map "SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'"))
          luotava-silta (assoc silta-tuonti :oid "9876543")
          _tee-silta (vie-silta-urakalle ds luotava-silta (:id paattynyt-urakka))
          tehty-silta (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid luotava-silta) "'")))
          _tarkastus (q-tarkastukset/luo-siltatarkastus<! ds {:silta (:silta-taulun-id (first (q-sillat/hae-sillan-tiedot ds {:trex-oid (:oid luotava-silta) :siltanimi (:nimi luotava-silta) :siltatunnus (:tunnus luotava-silta)})))
                                                             :urakka (:id paattynyt-urakka)
                                                             :tarkastusaika (Date.)
                                                             :tarkastaja "Foo"
                                                             :luoja 1
                                                             :ulkoinen_id "123"
                                                             :lahde "harja-ui"})
          siltoja-ennen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu=false"))
          _paivita-silta (vie-silta-urakalle ds (assoc luotava-silta :tila "poistettu") (:id aktiivinen-urakka))
          paivitetty-silta (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '" (:oid luotava-silta) "'")))
          siltoja-jalkeen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu=false"))]
         (is (= (dec siltoja-ennen) siltoja-jalkeen))
         (is (false? (:poistettu tehty-silta)))
         (is (:poistettu paivitetty-silta)))))

(deftest hoida-kunnalle-kuuluva-silta
  (let [sillat-ennen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu IS NOT TRUE;"))
        aktiivinen-urakka (first (q-map "SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'"))]
    (testing "uuden kuntasillan tuominen"
      (let [silta-tuonti (assoc silta-tuonti :oid "987654321" :nykyinenku "Raahen kunta")
            lisaa-silta (vie-silta-urakalle ds silta-tuonti (:id aktiivinen-urakka))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu IS NOT TRUE;"))]
        (is (nil? lisaa-silta))
        (is (= sillat-ennen sillat-jalkeen))))
    (testing "Sillan siirtyminen kunnalle"
      (let [silta-tuonti (assoc silta-tuonti :oid "35234525" :tunnus "O-234")
            lisaa-silta (vie-silta-urakalle ds silta-tuonti (:id aktiivinen-urakka))
            silta-kunnalla (assoc silta-tuonti :nykyinenku "Oulun kaupunki")
            paivita-silta (vie-silta-urakalle ds silta-kunnalla (:id aktiivinen-urakka))
            hae-silta (first (q-map (str "SELECT * FROM silta WHERE trex_oid = '35234525'")))
            sillat-jalkeen (ffirst (q "SELECT count(*) FROM silta WHERE poistettu IS NOT TRUE;"))]
        (is (= (:tunnus silta-tuonti) (:siltatunnus lisaa-silta)))
        (is (= 1 paivita-silta))
        (is (:poistettu hae-silta))
        (is (= sillat-ennen sillat-jalkeen))))))
