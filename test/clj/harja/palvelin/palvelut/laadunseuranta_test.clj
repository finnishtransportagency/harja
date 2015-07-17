(ns harja.palvelin.palvelut.laadunseuranta-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :laadunseuranta (component/using
                                          (ls/->Laadunseuranta)
                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(def soratietarkastus                                       ;; soratietarkastus
  {:uusi?          true
   :aika           #inst "2006-07-06T09:43:00.000-00:00"
   :tarkastaja     "Jalmari Järjestelmävastuuhenkilö"
   :sijainti       nil
   :tr             {:alkuosa 2, :numero 1, :alkuetaisyys 3, :loppuetaisyys 5, :loppuosa 4}
   :tyyppi         :soratie
   :soratiemittaus {:polyavyys     4
                    :hoitoluokka   1
                    :sivukaltevuus 5
                    :tasaisuus     1
                    :kiinteys      3}
   :havainto       {:kuvaus "kuvaus tähän"
                    :tekija :urakoitsija}})

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-ja-paivita-soratietarkastus
  (let [urakka-id (hae-oulun-alueurakan-id)
        kuvaus (str "kuvaus nyt " (System/currentTimeMillis))
        soratietarkastus (assoc-in soratietarkastus [:havainto :kuvaus] kuvaus)
        hae-tarkastukset #(kutsu-http-palvelua :hae-urakan-tarkastukset +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :alkupvm   #inst "2005-10-01T00:00:00.000-00:00"
                                                :loppupvm  #inst "2006-09-30T00:00:00.000-00:00"
                                                :tienumero %})
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
                                           {:urakka-id    urakka-id
                                            :tarkastus-id @tarkastus-id})]
        (is (= kuvaus (get-in tarkastus [:havainto :kuvaus])))

        (testing "Muokataan tarkastusta"
          (let [muokattu-tarkastus (kutsu-http-palvelua :tallenna-tarkastus +kayttaja-jvh+
                                                        {:urakka-id urakka-id
                                                         :tarkastus (-> tarkastus
                                                                        (assoc-in [:soratiemittaus :tasaisuus] 5)
                                                                        (assoc-in [:havainto :kuvaus] "MUOKATTU KUVAUS"))})]

            ;; id on edelleen sama
            (is (= (:id muokattu-tarkastus) @tarkastus-id))

            ;; muokatut kentät tallentuivat
            (is (= "MUOKATTU KUVAUS" (get-in muokattu-tarkastus [:havainto :kuvaus])))
            (is (= 5 (get-in muokattu-tarkastus [:soratiemittaus :tasaisuus])))))))))

(deftest hae-havainnon-tiedot []
  (let [urakka-id (hae-oulun-alueurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-havainnon-tiedot +kayttaja-jvh+ {:urakka-id   urakka-id
                                                                      :havainto-id 1})]
    (is (not (empty? vastaus)))
    (is (string? (:kuvaus vastaus)))
    (is (>= (count (:kuvaus vastaus)) 10))))

(deftest hae-urakan-havainnot []
  (let [urakka-id (hae-oulun-alueurakan-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-havainnot +kayttaja-jvh+ {:listaus :kaikki
                                                                      :urakka-id    urakka-id
                                                                      :alku (java.sql.Date. 100 9 1)
                                                                      :loppu (java.sql.Date. 110 8 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 10))))

(deftest hae-urakan-sanktiot []
  (let [urakka-id (hae-oulun-alueurakan-id)
        tpi-idt (hae-oulun-alueurakan-toimenpideinstanssien-idt)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-sanktiot +kayttaja-jvh+ {:tpi (first tpi-idt)
                                                                      :urakka-id    urakka-id
                                                                      :alku (java.sql.Date. 100 9 1)
                                                                      :loppu (java.sql.Date. 110 8 30)})]
    (is (not (empty? vastaus)))
    (is (>= (count vastaus) 1))))

; FIXME Testi ei toimi jostain syystä
#_(deftest hae-urakan-sanktiot-test
  (is (oikeat-sarakkeet-palvelussa?
        [:id :perintapvm :summa :laji :indeksi :suorasanktio :toimenpideinstanssi
         [:havainto :id] [:havainto :kohde] [:havainto :aika] [:havainto :tekija] [:havainto :urakka]
         [:havainto :tekijanimi] [:havainto :kuvaus] [:havainto :sijainti] [:havainto :tarkastuspiste]
         [:havainto :selvityspyydetty] [:havainto :selvitysannettu]

         [:havainto :paatos :kasittelyaika] [:havainto :paatos :paatos] [:havainto :paatos :kasittelytapa]
         [:havainto :paatos :muukasittelytapa] [:havainto :paatos :perustelu]

         [:havainto :tr :numero] [:havainto :tr :alkuosa] [:havainto :tr :loppuosa]
         [:havainto :tr :alkuetaisyys] [:havainto :tr :loppuetaisyys]]
        :hae-urakan-sanktiot
        {:urakka-id @oulun-alueurakan-id
         :alku      (java.sql.Date. 104 9 1)
         :loppu    (java.sql.Date. 105 8 30)
         :tpi 1})))
