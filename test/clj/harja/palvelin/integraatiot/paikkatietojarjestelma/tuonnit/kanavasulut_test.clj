(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut-test
  (:require [harja.testi :as ht]
            [clojure.test :as t]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut :as kanavasulku-tuonti]
            [harja.kyselyt.kanavat.kanavasulut :as q-kanavasulut]))

(t/use-fixtures :each (ht/laajenna-integraatiojarjestelmafixturea "jvh"))

(def referenssi-kanavasulku-shapefilesta
  {
   :numero 6666
   :aluenro 216737
   :nimi "Iskrovka (Särkijärvi)"
   :kanava_ty "Sulkukanava"
   :alue_ty "Sulku"
   :kiinnit "Liikkuvat pollarit"
   :portti_ty "Salpaus + Nosto/Lasku"
   :kaytto_ty "Kaukokäyttö"
   :sulku_lev 1.1
   :sulku_pit 1.2
   :alus_lev 12.06
   :alus_pit 82.05
   :alus_syv 4.04
   :alus_kor 24.05
   :sulkuja 1
   :putousk_1 1.1
   :putousk_2 11.38
   :ala_ver_1 "9,82"
   :ala_ver_2 "10"
   :yla_ver_1 "21,28"
   :yla_ver_2 "21,28"
   :kynnys_1 "4,42"
   :kynnys_2 "15,73"
   :vesisto "Vuoksen vesistö"
   :kanavakok "Saimaan kanava"
   :kanava_pit 85
   :kanava_lev 13.02
   :mista "Brusnitchnoe"
   :mihin "Mal. Cvetotchnoe"
   :omistaja "Liikennevirasto"
   :the_geom "POLYGON((594378.7923042110633105 6745379.7973106112331152, 594416.31681462517008185 6745289.93295709136873484, 594404.13800845539662987 6745285.05637172050774097, 594366.80162501567974687 6745374.78863043710589409, 594378.7923042110633105 6745379.7973106112331152))"})

(def referenssi-kanavasulku-tietokannasta
  {:kanavanro 6666
   :aluenro 216737
   :nimi "Iskrovka (Särkijärvi)"
   :kanavatyyppi "Sulkukanava"
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
   :alakanavan_alavertaustaso "9,82"
   :alakanavan_ylavertaustaso "10"
   :ylakanavan_alavertaustaso "21,28"
   :ylakanavan_ylavertaustaso "21,28"
   :kynnys_1 "4,42"
   :kynnys_2 "15,73"
   :vesisto "Vuoksen vesistö"
   :kanavakokonaisuus "Saimaan kanava"
   :kanava_pituus 85
   :kanava_leveys 13.02
   :lahtopaikka "Brusnitchnoe"
   :kohdepaikka "Mal. Cvetotchnoe"
   :omistaja "Liikennevirasto"
   :luoja "Integraatio"})


(t/deftest vie-kanava-tietokantaan
    (kanavasulku-tuonti/vie-kanavasulku-entry (:db ht/jarjestelma) referenssi-kanavasulku-shapefilesta)
    (let [tallentunut-kanava  (first(q-kanavasulut/hae-kanavasulku-tunnuksella (:db ht/jarjestelma) {:kanavanumero 6666}))]
      (ht/tarkista-map-arvot referenssi-kanavasulku-tietokannasta tallentunut-kanava)))

;;TODO: tarkista triggerin toiminta testissä