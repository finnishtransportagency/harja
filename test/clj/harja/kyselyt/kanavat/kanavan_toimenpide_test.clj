(ns harja.kyselyt.kanavat.kanavan-toimenpide-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as kanava-q]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [specql.op :as op]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest hae-kanavan-toimenpiteet
  (let [db (:db jarjestelma)
        vastaus (kanava-q/hae-kanavatomenpiteet-jeesql
                  db
                  {:urakka (hae-saimaan-kanavaurakan-id)
                   :sopimus (hae-saimaan-kanavaurakan-paasopimuksen-id)
                   :alkupvm (harja.pvm/luo-pvm 2016 1 1)
                   :loppupvm (harja.pvm/luo-pvm 2018 1 1)
                   :toimenpidekoodi 597
                   :tyyppi "kokonaishintainen"
                   :kohde nil})]
    (is (every? ::kanavan-toimenpide/id vastaus))
    (is (every? ::kanavan-toimenpide/kohde vastaus))
    (is (every? ::kanavan-toimenpide/toimenpidekoodi vastaus))
    (is (every? ::kanavan-toimenpide/huoltokohde vastaus))))


(deftest tallenna-kanavan-toimenpide
  (let [db (:db jarjestelma)
        hae-maara #(count (q "select id from kan_toimenpide where poistettu is not true"))
        maara-alussa (hae-maara)
        kayttaja-id (ffirst (q "select id from kayttaja limit 1"))
        kohde-id (ffirst (q "select id from kan_kohde limit 1"))
        huoltokohde-id (ffirst (q "select id from kan_huoltokohde limit 1"))
        urakka-id (ffirst (q "select id from urakka where nimi = 'Saimaan kanava' limit 1"))
        sopimus-id (ffirst (q "select id from sopimus where nimi = 'Saimaan huollon pääsopimus';"))
        toimenpideinstanssi-id (ffirst (q "select id from toimenpideinstanssi where nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP';"))
        tallennettava {::kanavan-toimenpide/suorittaja "suorittaja"
                       ::kanavan-toimenpide/muu-toimenpide "muu"
                       ::kanavan-toimenpide/kuittaaja-id kayttaja-id
                       ::kanavan-toimenpide/sopimus-id sopimus-id
                       ::kanavan-toimenpide/toimenpideinstanssi-id toimenpideinstanssi-id
                       ::kanavan-toimenpide/lisatieto "lisätieto"
                       ::kanavan-toimenpide/tyyppi :kokonaishintainen
                       ::kanavan-toimenpide/kohde-id kohde-id
                       ::kanavan-toimenpide/pvm (pvm/nyt)
                       ::kanavan-toimenpide/huoltokohde-id huoltokohde-id
                       ::kanavan-toimenpide/urakka-id urakka-id}
        tallennettu (kanava-q/tallenna-toimenpide db kayttaja-id tallennettava)
        maara-lisayksen-jalkeen (hae-maara)
        paivitettava (assoc tallennettu ::kanavan-toimenpide/lisatieto "lisätieto on muuttunut")
        _ (kanava-q/tallenna-toimenpide db kayttaja-id paivitettava)
        paivitetty (first (kanava-q/hae-kanavatoimenpiteet-specql
                            db
                            (op/and
                              (op/or {::muokkaustiedot/poistettu? op/null?} {::muokkaustiedot/poistettu? false})
                              {::kanavan-toimenpide/id (::kanavan-toimenpide/id tallennettu)})))
        maara-paivityksen-jalkeen (hae-maara)
        poistettava (assoc paivitettava ::muokkaustiedot/poistettu? true)
        _ (kanava-q/tallenna-toimenpide db kayttaja-id poistettava)
        maara-poiston-jalkeen (hae-maara)]
    (is (= (+ 1 maara-alussa) maara-lisayksen-jalkeen))
    (is (= kayttaja-id (::muokkaustiedot/luoja-id tallennettu)))
    (is (not (nil? (::muokkaustiedot/luotu tallennettu))))
    (is (= "kokonaishintainen" (::kanavan-toimenpide/tyyppi tallennettu)))
    (is (= (dissoc tallennettava ::kanavan-toimenpide/tyyppi)
           (dissoc tallennettu
                   ::kanavan-toimenpide/id
                   ::kanavan-toimenpide/tyyppi
                   ::muokkaustiedot/luoja-id
                   ::muokkaustiedot/luotu)))

    (is (= maara-lisayksen-jalkeen maara-paivityksen-jalkeen))

    (is (= "lisätieto on muuttunut" (::kanavan-toimenpide/lisatieto paivitetty)))
    (is (= kayttaja-id (::muokkaustiedot/muokkaaja-id paivitetty)))
    (is (not (nil? (::muokkaustiedot/muokattu paivitetty))))
    
    (is (and (= maara-alussa maara-poiston-jalkeen)
             (= (- maara-paivityksen-jalkeen 1) maara-poiston-jalkeen)))))
