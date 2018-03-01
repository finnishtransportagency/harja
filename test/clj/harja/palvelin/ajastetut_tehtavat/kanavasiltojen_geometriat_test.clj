(ns harja.palvelin.ajastetut-tehtavat.kanavasiltojen-geometriat-test
  (:require [clojure.test :as t]
            [harja.palvelin.ajastetut-tehtavat.kanavasiltojen-geometriat :as kanavasilta-tuonti]
            [harja.kyselyt.kanavat.kanavasillat :as q-kanavasillat]
            [harja.testi :as ht]
            [harja.kyselyt.konversio :as konv]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

(t/use-fixtures :each (ht/laajenna-integraatiojarjestelmafixturea "jvh"))


(def referenssi-tulos
  {:lukumaara 3
   :sivu 1
   :aloitus 1
   :ajankohta 1517899053259
   :tulokset [{:siltanro 666666
               :siltanimi "Kuuskanavan silta"
               :tunnus_prefix "V"
               :d_kayttotar_koodi ["11"]
               :elinkaaritila "käytössä"
               :siltapit 24
               :rakennety ["Teräksinen läppäsilta, teräskantinen"]
               :tieosoitteet [{"tie" 712, "osa" 2, "etaisyys" 159, "ajorata" 1}]
               :sijainti_n 6990108
               :sijainti_e 332310
               :avattuliikenteellepvm -1073088000000
               :muutospvm 1515522968901
               :trex_oid "1.2.246.578.1.15.1000666"
               :sivu 1}
              {:siltanro 666667
               :siltanimi "Itikan silta"
               :tunnus_prefix "V"
               :d_kayttotar_koodi ["11"]
               :elinkaaritila "käytössä"
               :siltapit 24
               :rakennety ["Ei relevantti rakennetyyppi"]
               :tieosoitteet [{"tie" 712, "osa" 2, "etaisyys" 159}]
               :sijainti_n 6990108
               :sijainti_e 332310
               :avattuliikenteellepvm -1073088000000
               :muutospvm 1515522968901
               :trex_oid "1.2.246.578.1.15.1000666"
               :sivu 1}
              {:siltanro 666668
               :siltanimi "Ei relevantti nimi"
               :tunnus_prefix "V"
               :d_kayttotar_koodi ["11"]
               :elinkaaritila "käytössä"
               :siltapit 24
               :rakennety ["Ei relevantti rakennetyyppi"]
               :tieosoitteet [{"tie" 712, "osa" 2, "etaisyys" 159}]
               :sijainti_n 6990108
               :sijainti_e 332310
               :avattuliikenteellepvm -1073088000000
               :muutospvm 1515522968901
               :trex_oid "1.2.246.578.1.15.1000666"
               :sivu 1}]})

(def referenssi-kanavasilta
  {:siltanro 666666
   :siltanimi "Kuuskanavan silta"
   :tunnus_prefix "V"
   :d_kayttotar_koodi ["11"]
   :elinkaaritila "käytössä"
   :siltapit 24
   :rakennety ["Teräksinen läppäsilta, teräskantinen"]
   :tieosoitteet [{"tie" 712, "osa" 2, "etaisyys" 159} {"tie" 1, "osa" 1, "etaisyys" 1, "ajorata" 1}]
   :sijainti_n 6990108
   :sijainti_e 332310
   :avattuliikenteellepvm -1073088000000
   :muutospvm 1515522968901
   :trex_oid "1.2.246.578.1.15.1000666"
   :sivu 1})

(t/deftest tarkista-paivitysehdot
  (ht/u "INSERT INTO geometriapaivitys (nimi) VALUES ('kanavasillat') ON CONFLICT(nimi) DO NOTHING;")

  (ht/u "UPDATE geometriapaivitys SET viimeisin_paivitys = NULL WHERE nimi = 'kanavasillat';")
  (t/is (kanavasilta-tuonti/paivitys-tarvitaan? (:db ht/jarjestelma) 60) "Päivitys tarvitaan, kun sitä ei ole koskaan tehty")

  (ht/u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '61' day WHERE nimi = 'kanavasillat';")
  (t/is (kanavasilta-tuonti/paivitys-tarvitaan? (:db ht/jarjestelma) 60) "Päivitys tarvitaan, kun se on viimeksi tehty tarpeeksi kauan sitten") ;

  (ht/u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '59' day WHERE nimi = 'kanavasillat';")
  (t/is (false? (kanavasilta-tuonti/paivitys-tarvitaan? (:db ht/jarjestelma) 60)) "Päivitystä ei tarvita, kun se on tehty tarpeeksi vasta"))

(t/deftest muodosta-sivutettu-url
  (t/is "http://testi.solita.fi/rajapinta/1.2/hae?sivu=23&tuloksia-per-sivu=1000"
        (kanavasilta-tuonti/muodosta-sivutettu-url "http://testi.solita.fi/rajapinta/1.2/hae?sivu=%1&tuloksia-per-sivu=1000" 23)))

(t/deftest tallenna-ja-paivita-kanavasilta

  ; Uusi silta
  (kanavasilta-tuonti/tallenna-kanavasilta (:db ht/jarjestelma) referenssi-kanavasilta 1)
  (t/is (= (ffirst (ht/q "SELECT nimi FROM kan_silta where siltanro = 666666;")) "Kuuskanavan silta"))
  (t/is (= (ffirst (ht/q "SELECT nimi FROM kan_kohteenosa where lahdetunnus = 666666;")) "Kuuskanavan silta"))
  (t/is (= (ffirst (ht/q "SELECT poistettu FROM kan_kohteenosa where lahdetunnus = 666666;")) false))

  ; Päivittynyt silta
  (let [paivitetty-silta (assoc referenssi-kanavasilta :siltanimi "Kuusikko")]
    (kanavasilta-tuonti/tallenna-kanavasilta (:db ht/jarjestelma) paivitetty-silta 2))
  (t/is (= (ffirst (ht/q "SELECT nimi FROM kan_silta where siltanro = 666666;")) "Kuusikko"))
  (t/is (= (ffirst (ht/q "SELECT nimi FROM kan_kohteenosa where lahdetunnus = 666666;")) "Kuusikko"))
  (t/is (= (ffirst (ht/q "SELECT poistettu FROM kan_kohteenosa where lahdetunnus = 666666;")) false))

  ; Poistettu silta
  (let [poistettu-silta (assoc referenssi-kanavasilta :elinkaaritila "purettu")]
    (kanavasilta-tuonti/tallenna-kanavasilta (:db ht/jarjestelma) poistettu-silta 3))
  (t/is (= (ffirst (ht/q "SELECT tila FROM kan_silta where siltanro = 666666;")) "purettu"))
  (t/is (= (ffirst (ht/q "SELECT poistettu FROM kan_silta where siltanro = 666666;")) true))
  (t/is (= (ffirst (ht/q "SELECT poistettu FROM kan_kohteenosa where lahdetunnus = 666666;")) true)))


(t/deftest suodata-sillat

  ; Rakennustyypin perusteteella suodatettava palautuu, palautuu 1
  (t/is (= 1 (count (kanavasilta-tuonti/suodata-avattavat-sillat-rakennetyypin-mukaan referenssi-tulos))))
  (t/is (= 666666 (:siltanro (first (kanavasilta-tuonti/suodata-avattavat-sillat-rakennetyypin-mukaan referenssi-tulos)))))

  ; Numeron perusteella suodatettava palautuu, palautuu 1
  (t/is (= 1 (count (kanavasilta-tuonti/suodata-sillat-nimen-mukaan referenssi-tulos))))
  (t/is (= 666667 (:siltanro (first (kanavasilta-tuonti/suodata-sillat-nimen-mukaan referenssi-tulos)))))

  ; Sekä rakennustyypin että nimen perusteella suodatettava palautuu, palautuuu 2
  (t/is (= 2 (count (kanavasilta-tuonti/suodata-sillat referenssi-tulos))))
  (t/is (= 666666 (:siltanro (first (kanavasilta-tuonti/suodata-sillat referenssi-tulos)))))
  (t/is (= 666667 (:siltanro (second (kanavasilta-tuonti/suodata-sillat referenssi-tulos))))))


