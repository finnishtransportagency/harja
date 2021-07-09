(ns harja.kyselyt.kanavat.kanavan-hairiotilanne-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kanavan-hairiotilanne :as hairiotilanne-q]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]))

(use-fixtures :once tietokantakomponentti-fixture)

(defn poista-muokkaustiedot [hairiotilanne]
  (dissoc hairiotilanne
          ::hairiotilanne/id
          ::muokkaustiedot/luoja-id
          ::muokkaustiedot/luotu
          ::muokkaustiedot/muokkaaja-id
          ::muokkaustiedot/muokattu
          ::muokkaustiedot/poistettu?
          ::muokkaustiedot/poistettu
          ::muokkaustiedot/poistaja-id))

(defn keywordit-stringeina [hairiotilanne]
  (assoc hairiotilanne
    ::hairiotilanne/korjauksen-tila (name (::hairiotilanne/korjauksen-tila hairiotilanne))
    ::hairiotilanne/vikaluokka (name (::hairiotilanne/vikaluokka hairiotilanne))))

(deftest tallenna-kanavan-toimenpide
  (let [db (:db jarjestelma)
        hae-maara #(count (q "select id from kan_hairio where poistettu is not true;"))
        maara-alussa (hae-maara)
        kayttaja-id (:id +kayttaja-jvh+)
        urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id)
        uusi {::hairiotilanne/sopimus-id sopimus-id
              ::hairiotilanne/kohde-id kohde-id
              ::hairiotilanne/paikallinen-kaytto? true
              ::hairiotilanne/vikaluokka :sahkotekninen_vika
              ::hairiotilanne/korjaustoimenpide "Vähennetään sähköä"
              ::hairiotilanne/korjauksen-tila :kesken
              ::hairiotilanne/havaintoaika (pvm/luo-pvm 2017 11 17)
              ::hairiotilanne/urakka-id urakka-id
              ::hairiotilanne/sijainti nil
              ::hairiotilanne/kuittaaja-id (:id +kayttaja-jvh+)
              ::hairiotilanne/huviliikenne-lkm 1
              ::hairiotilanne/korjausaika-h 1
              ::hairiotilanne/syy "Liikaa sähköä"
              ::hairiotilanne/odotusaika-h 4
              ::hairiotilanne/ammattiliikenne-lkm 2}
        uusi-tallennettu (hairiotilanne-q/tallenna-hairiotilanne db kayttaja-id uusi)
        maara-uuden-luonnin-jalkeen (hae-maara)
        tallennettu-id (::hairiotilanne/id uusi-tallennettu)]

    (is (= (+ maara-alussa 1) maara-uuden-luonnin-jalkeen))
    (is (= (keywordit-stringeina uusi) (poista-muokkaustiedot uusi-tallennettu)))
    (is (= kayttaja-id (::muokkaustiedot/luoja-id uusi-tallennettu)))
    (is (not (nil? (::muokkaustiedot/luotu uusi-tallennettu))))

    (let [syy "Aivan liikaa sähköä"
          muokattu (assoc uusi-tallennettu ::hairiotilanne/syy syy)
          _ (hairiotilanne-q/tallenna-hairiotilanne db kayttaja-id muokattu)
          muokattu-tallennettu (first (hairiotilanne-q/hae-hairiotilanteet db {::hairiotilanne/id tallennettu-id}))
          maara-muokkauksen-jalkeen (hae-maara)]
      (is (= maara-uuden-luonnin-jalkeen maara-muokkauksen-jalkeen))
      (is (= syy (::hairiotilanne/syy muokattu-tallennettu)))
      (is (= kayttaja-id (::muokkaustiedot/muokkaaja-id muokattu-tallennettu)))
      (is (not (nil? (::muokkaustiedot/muokattu muokattu-tallennettu))))

      (let [poistettu (assoc muokattu ::muokkaustiedot/poistettu? true)
            _ (hairiotilanne-q/tallenna-hairiotilanne db kayttaja-id poistettu)
            poistettu-tallennettu (first (hairiotilanne-q/hae-hairiotilanteet db {::hairiotilanne/id tallennettu-id}))
            maara-poiston-jalkeen (hae-maara)]
        (is (= maara-alussa maara-poiston-jalkeen))
        (is (::muokkaustiedot/poistettu? poistettu-tallennettu))))))
