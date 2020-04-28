(ns harja.palvelin.palvelut.tarkastukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [<!!]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta.tarkastukset :as t]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :karttakuvat (component/using
                                       (karttakuvat/luo-karttakuvat)
                                       [:http-palvelin :db])
                        :tarkastukset (component/using
                                        (t/->Tarkastukset)
                                        [:http-palvelin :db :karttakuvat])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(def soratietarkastus ;; soratietarkastus
  {:uusi? true
   :aika #inst "2006-07-06T09:43:00.000-00:00"
   :tarkastaja "Jalmari Järjestelmävastuuhenkilö"
   :sijainti nil
   :tr {:alkuosa 2, :numero 1, :alkuetaisyys 3, :loppuetaisyys 5, :loppuosa 4}
   :tyyppi :soratie
   :soratiemittaus {:polyavyys 4
                    :hoitoluokka 1
                    :sivukaltevuus 5
                    :tasaisuus 1
                    :kiinteys 3}
   :havainnot "kuvaus tähän"
   :laadunalitus true})

(use-fixtures :each (compose-fixtures jarjestelma-fixture tietokanta-fixture))

(deftest tallenna-ja-paivita-soratietarkastus
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        kuvaus (str "kuvaus nyt " (System/currentTimeMillis))
        soratietarkastus (assoc-in soratietarkastus [:havainnot] kuvaus)
        hae-tarkastukset #(kutsu-http-palvelua :hae-urakan-tarkastukset +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :alkupvm #inst "2005-10-01T00:00:00.000-00:00"
                                                :loppupvm #inst "2006-09-30T00:00:00.000-00:00"
                                                :tienumero %
                                                :vain-laadunalitukset? false})
        tarkastuksia-ennen-kaikki (count (hae-tarkastukset nil))
        tarkastuksia-ennen-tie1 (count (hae-tarkastukset 1))
        tarkastuksia-ennen-tie2 (count (hae-tarkastukset 2))
        tarkastus-id (atom nil)]

    (testing "Soratietarkastuksen tallennus"
      (let [vastaus (kutsu-http-palvelua :tallenna-tarkastus +kayttaja-jvh+
                                         {:urakka-id urakka-id
                                          :tarkastus soratietarkastus})
            id (:id vastaus)]

        (is (number? id) "Tallennus palauttaa uuden id:n")

        ;; kaikki ja tie 1 listauksissa määrä kasvanut yhdellä
        (is (= (count (hae-tarkastukset nil)) (inc tarkastuksia-ennen-kaikki)))

        (let [listaus-tie1 (hae-tarkastukset 1)]
          (is (= (count listaus-tie1) (inc tarkastuksia-ennen-tie1)))
          (is (= :soratie
                 (:tyyppi (first (filter #(= (:id %) id) listaus-tie1))))))


        ;; tie 2 tarkastusmäärä ei ole kasvanut
        (is (= (count (hae-tarkastukset 2)) tarkastuksia-ennen-tie2))

        (reset! tarkastus-id id)))

    (testing "Tarkastuksen haku ja muokkaus"
      (let [tarkastus (kutsu-http-palvelua :hae-tarkastus +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :tarkastus-id @tarkastus-id})]
        (is (= kuvaus (:havainnot tarkastus)))

        (testing "Muokataan tarkastusta"
          (let [muokattu-tarkastus (kutsu-http-palvelua :tallenna-tarkastus +kayttaja-jvh+
                                                        {:urakka-id urakka-id
                                                         :tarkastus (-> tarkastus
                                                                        (assoc-in [:soratiemittaus :tasaisuus] 5)
                                                                        (assoc-in [:havainnot] "MUOKATTU KUVAUS"))})]

            ;; id on edelleen sama
            (is (= (:id muokattu-tarkastus) @tarkastus-id))

            ;; muokatut kentät tallentuivat
            (is (= "MUOKATTU KUVAUS" (get-in muokattu-tarkastus [:havainnot])))
            (is (= 5 (get-in muokattu-tarkastus [:soratiemittaus :tasaisuus])))))))))

(deftest hae-urakan-tarkastukset
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-tarkastukset +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :alkupvm (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppupvm (pvm/luo-pvm (+ 1900 110) 8 30)
                                 :tienumero nil
                                 :tyyppi nil
                                 :vain-laadunalitukset? false})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))
    (let [tarkastus (first vastaus)]
      (is (= #{:ok? :jarjestelma :havainnot :laadunalitus
               :vakiohavainnot :aika :soratiemittaus
               :tr :tekija :id :tyyppi :tarkastaja
               :talvihoitomittaus :yllapitokohde
               :nayta-urakoitsijalle :liitteet}
             (into #{} (keys tarkastus)))))))

(deftest hae-urakan-tarkastukset-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-tarkastukset +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :alkupvm (pvm/luo-pvm (+ 1900 100) 9 1)
                                 :loppupvm (pvm/luo-pvm (+ 1900 130) 8 30)
                                 :tienumero nil
                                 :tyyppi nil
                                 :vain-laadunalitukset? false})]
    (is (not (empty? vastaus)))
    (is (= (count vastaus) 1))))

(deftest hae-tarkastus
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :tarkastus-id 1})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest hae-tarkastus-joka-ei-nay-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :tarkastus-id (ffirst (q "SELECT id FROM tarkastus
                                                           WHERE havainnot != 'Tämä tarkastus näkyy myös urakoitsijalle';"))})]
    (is (empty? vastaus))))

(deftest hae-tarkastus-joka-nakyy-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tarkastus +kayttaja-urakan-vastuuhenkilo+
                                {:urakka-id urakka-id
                                 :tarkastus-id (ffirst (q "SELECT id FROM tarkastus
                                                           WHERE havainnot = 'Tämä tarkastus näkyy myös urakoitsijalle';"))})]
    (is (not (empty? vastaus)))
    (is (= (:havainnot vastaus) "Tämä tarkastus näkyy myös urakoitsijalle"))))

(deftest nayta-tarkastus-urakoitsijalle
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopivat-tarkastukset (q-map "SELECT id, havainnot, nayta_urakoitsijalle FROM tarkastus
                                     WHERE luoja IN (SELECT id FROM kayttaja WHERE jarjestelma IS TRUE)
                                     AND nayta_urakoitsijalle IS FALSE
                                     AND urakka = " urakka-id)]

    (is (> (count sopivat-tarkastukset) 1))

    (doseq [tarkastus-ennen sopivat-tarkastukset]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :nayta-tarkastus-urakoitsijalle
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :tarkastus-id (:id tarkastus-ennen)})
            tarkastus-jalkeen (first (q-map "SELECT nayta_urakoitsijalle, havainnot FROM tarkastus WHERE id = " (:id tarkastus-ennen) ";"))]


        (is (false? (:nayta_urakoitsijalle tarkastus-ennen)))
        (is (= (:havainnot tarkastus-jalkeen) (:havainnot tarkastus-ennen)))
        (is (true? (:nayta_urakoitsijalle tarkastus-jalkeen)))))))

(deftest nayta-tarkastus-urakoitsijalle-ei-toimi-vaaraan-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        eri-urakan-tarkastus (first (q-map "SELECT id FROM tarkastus WHERE urakka != " urakka-id ";"))]
    (is (thrown? SecurityException
              (kutsu-palvelua (:http-palvelin jarjestelma)
                              :nayta-tarkastus-urakoitsijalle
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :tarkastus-id (:id eri-urakan-tarkastus)})))))

(deftest nayta-tarkastus-urakoitsijalle-ei-toimi-ilman-oikeuksia
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastus (first (q-map "SELECT id FROM tarkastus WHERE urakka = " urakka-id ";"))]
    (is (thrown? Exception
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :nayta-tarkastus-urakoitsijalle
                                 +kayttaja-tero+
                                 {:urakka-id urakka-id
                                  :tarkastus-id (:id tarkastus)})))))

(deftest hae-tarkastusreitit-kartalle
  (let [extent [237856 7077888 500000 7340032]
        params {:havaintoja-sisaltavat? false, :urakka-id 4, :loppupvm #inst "2016-12-31T21:59:59.000-00:00", :vain-laadunalitukset? false, :tekija nil, :alkupvm #inst "2016-11-30T22:00:00.000-00:00", :valittu {:id nil}, :tyyppi nil, :tienumero nil}]
    (let [vastaus (<!! (t/hae-tarkastusreitit-kartalle (:db jarjestelma)
                                                       +kayttaja-jvh+
                                                       {:extent extent
                                                        :parametrit params}))
          sijainti (:sijainti vastaus)
          reitti (:reitti vastaus)
          sijainnin-eka-viiva (first (:lines sijainti))
          reitin-eka-viiva (first (:lines reitti))]
      (is (true? (:ok? vastaus)))
      (is (= :tarkastus (:tyyppi-kartalla vastaus)))
      (is (nil? (:talvihoitomittaus vastaus)))
      (is (= :laatu (:tyyppi vastaus)))
      (is (= false (:laadunalitus vastaus)))
      (is (= :tilaaja (:tekija vastaus)))
      (is (= #{"Lumista"} (:vakiohavainnot vastaus)))

      (is (map? sijainti))
      (is (= :multiline (:type sijainti)))
      (is (= 13 (count (:lines sijainti))))
      (is (= {:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]}
             sijainnin-eka-viiva))
      (is (= :line (:type sijainnin-eka-viiva)))
      (is (= 2 (count (:points sijainnin-eka-viiva))))


      (is (map? reitti))
      (is (= :multiline (:type reitti)))
      (is (= 13 (count (:lines reitti))))
      (is (= {:type :line, :points [[426948.180407029 7212765.48225361] [430650.8691 7212578.8262]]} reitin-eka-viiva))
      (is (contains? (:selite vastaus) :teksti))
      (is (contains? (:selite vastaus) :vari)))))