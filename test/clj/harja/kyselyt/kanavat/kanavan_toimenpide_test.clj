(ns harja.kyselyt.kanavat.kanavan-toimenpide-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as kanava-q]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]))

(deftest hae-kanavan-toimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        vastaus (kanava-q/hae-sopimuksen-toimenpiteet-aikavalilta
                  db
                  {:urakka (hae-saimaan-kanavaurakan-id)
                   :sopimus (hae-saimaan-kanavaurakan-paasopimuksen-id)
                   :alkupvm (harja.pvm/luo-pvm 2016 1 1)
                   :loppupvm (harja.pvm/luo-pvm 2018 1 1)
                   :toimenpidekoodi 597
                   :tyyppi "kokonaishintainen"})]
    (is (every? ::kanavan-toimenpide/id vastaus))
    (is (every? ::kanavan-toimenpide/kohde vastaus))
    (is (every? ::kanavan-toimenpide/toimenpidekoodi vastaus))
    (is (every? ::kanavan-toimenpide/huoltokohde vastaus))))


(deftest tallenna-kanavan-toimenpide
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        maara-alussa (count (q "select id from kan_toimenpide"))
        kayttaja (ffirst (q "select id from kayttaja limit 1"))
        kohde (ffirst (q "select id from kan_kohde limit 1"))
        huoltokohde (ffirst (q "select id from kan_huoltokohde limit 1"))
        urakka (ffirst (q "select id from urakka where nimi = 'Saimaan kanava' limit 1"))
        sopimus (ffirst (q "select id from sopimus where nimi = 'Saimaan huollon pääsopimus';"))
        toimenpideinstanssi (ffirst (q "select id from toimenpideinstanssi where nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP';"))
        tallennettava {:harja.domain.kanavat.kanavan-toimenpide/suorittaja "suorittaja"
                       :harja.domain.kanavat.kanavan-toimenpide/muu-toimenpide "muu"
                       :harja.domain.kanavat.kanavan-toimenpide/kuittaaja-id kayttaja
                       :harja.domain.kanavat.kanavan-toimenpide/sopimus-id sopimus
                       :harja.domain.kanavat.kanavan-toimenpide/toimenpideinstanssi-id toimenpideinstanssi
                       :harja.domain.kanavat.kanavan-toimenpide/lisatieto "lisätieto"
                       :harja.domain.kanavat.kanavan-toimenpide/tyyppi :kokonaishintainen
                       :harja.domain.muokkaustiedot/luoja-id kayttaja
                       :harja.domain.kanavat.kanavan-toimenpide/kohde-id kohde
                       :harja.domain.kanavat.kanavan-toimenpide/pvm (pvm/nyt)
                       :harja.domain.kanavat.kanavan-toimenpide/huoltokohde-id huoltokohde
                       :harja.domain.kanavat.kanavan-toimenpide/urakka-id urakka}
        tallennettu (kanava-q/tallenna-toimenpide db tallennettava)
        maara-lisayksen-jalkeen (count (q "select id from kan_toimenpide"))
        paivitettava (assoc tallennettu :harja.domain.kanavat.kanavan-toimenpide/lisatieto "lisätieto on muuttunut")
        paivitetty (kanava-q/tallenna-toimenpide db paivitettava)
        maara-paivityksen-jalkeen (count (q "select id from kan_toimenpide"))]

    (is (= (+ 1 maara-alussa) maara-lisayksen-jalkeen))
    (is (= tallennettava (dissoc tallennettu :harja.domain.kanavat.kanavan-toimenpide/id)))

    (is (= maara-lisayksen-jalkeen maara-paivityksen-jalkeen))
    (is (= paivitettava paivitetty))))
