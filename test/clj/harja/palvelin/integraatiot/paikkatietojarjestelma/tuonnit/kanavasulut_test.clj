(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut-test
  (:require [harja.testi :as ht]
            [clojure.test :as t]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut :as kanavasulku-tuonti]
            [harja.kyselyt.kanavat.kanavasulut :as q-kanavasulut]))

(t/use-fixtures :each (ht/laajenna-integraatiojarjestelmafixturea "jvh"))

(def referenssi-kanavasulku-shapefilesta
  {:numero 6666
   :id 216737
   :nimi "Juankoski"
   :kanavaalue "Sulku"
   :kiinnittym "Liikkuvat pollarit"
   :porttiseli "Salpaus + Nosto/Lasku"
   :kayttoseli "Kaukokäyttö"
   :sulkuleve0 1.1
   :sulkulevey 1.2
   :alusleveys 12.06
   :aluspituus 82.05
   :alussyvyys 4.04
   :aluskorkeu 24.05
   :sulkuja 1
   :putouskork 1.1
   :putouskor0 11.38
   :vesisto "Vuoksen vesistö"
   :kanavapitu 85
   :kanavaleve 13.02
   :mista "Brusnitchnoe"
   :mihin "Mal. Cvetotchnoe"
   :omistaja "Liikennevirasto"
   :the_geom "MULTIPOLYGON (((594392.7239020942 6745281.275919718, 594423.7049784237 6745294.499646643, 594431.0724755075 6745280.3314706115, 594398.5801220272 6745267.296652856, 594392.7239020942 6745281.275919718)))"})

(def referenssi-kanavasulku-tietokannasta
  {:kanavanro 6666
   :aluenro 216737
   :nimi "Juankoski"
   :aluetyyppi "Sulku"
   :kiinnitys "Liikkuvat pollarit"
   :porttityyppi "Salpaus + Nosto/Lasku"
   :kayttotapa "Kaukokäyttö"
   :sulku_leveys 1.1
   :sulku_pituus 1.2
   :alus_leveys 12.06
   :alus_pituus 82.05
   :alus_syvyys 4.04
   :alus_korkeus 24.05
   :sulkumaara 1
   :putouskorkeus_1 1.1
   :putouskorkeus_2 11.38
   :vesisto "Vuoksen vesistö"
   :kanava_pituus 85
   :kanava_leveys 13.02
   :lahtopaikka "Brusnitchnoe"
   :kohdepaikka "Mal. Cvetotchnoe"
   :omistaja "Liikennevirasto"
   :luoja "Integraatio"
   :poistettu false})

(t/deftest vie-kanava-tietokantaan

  ;; Uusi kanava
  (kanavasulku-tuonti/vie-kanavasulku-entry (:db ht/jarjestelma) referenssi-kanavasulku-shapefilesta)
  (let [tallentunut-kanava (first (q-kanavasulut/hae-kanavasulku-tunnuksella (:db ht/jarjestelma) {:kanavanumero 6666}))

        _ (println "\n tall: " tallentunut-kanava)]
    (ht/tarkista-map-arvot referenssi-kanavasulku-tietokannasta tallentunut-kanava))
  (t/is (= (ffirst(ht/q "SELECT count(id) FROM kan_kohteenosa where lahdetunnus = 6666;")) 1))
  (t/is (= (ffirst(ht/q "SELECT count(id) FROM kan_kohde where id = (select \"kohde-id\" from kan_kohteenosa where lahdetunnus = 6666);")) 1))
  (t/is (= (ffirst(ht/q "SELECT count(id) FROM kan_kohdekokonaisuus where id = (select \"kohdekokonaisuus-id\" from kan_kohde where id = (select \"kohde-id\" from kan_kohteenosa where lahdetunnus = 6666));")) 1))

  ;; Päivitetty kanava
  (let [paivitetty-kanava (assoc referenssi-kanavasulku-shapefilesta :nimi "Juankoski" :kayttoseli "Itsepalvelu")
        paivitetty-kanava-tietokannasta  (assoc referenssi-kanavasulku-tietokannasta :nimi "Juankoski" :kayttotapa "Itsepalvelu")]
    (kanavasulku-tuonti/vie-kanavasulku-entry (:db ht/jarjestelma) paivitetty-kanava)
    (ht/tarkista-map-arvot paivitetty-kanava-tietokannasta (first (q-kanavasulut/hae-kanavasulku-tunnuksella (:db ht/jarjestelma) {:kanavanumero 6666}))))
  (t/is (= (ffirst(ht/q "SELECT count(id) FROM kan_kohteenosa where lahdetunnus = 6666;")) 1))
  (t/is (= (ffirst(ht/q "SELECT oletuspalvelumuoto FROM kan_kohteenosa where lahdetunnus = 6666;")) "itse"))
  (t/is (= (ffirst(ht/q "SELECT nimi FROM kan_kohde where id = (select \"kohde-id\" from kan_kohteenosa where lahdetunnus = 6666);")) "Juankoski"))
  (t/is (= (ffirst(ht/q "SELECT nimi FROM kan_kohdekokonaisuus where id = (select \"kohdekokonaisuus-id\" from kan_kohde where id = (select \"kohde-id\" from kan_kohteenosa where lahdetunnus = 6666));")) "Kuopio - Syväri reitti")))
