(ns harja.palvelin.palvelut.valikatselmus.paatokset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.kyselyt.erilliskustannus-kyselyt :as erilliskustannus-kyselyt]
            [harja.kyselyt.sanktiot :as sanktio-kyselyt]
            [harja.kyselyt.valikatselmus :as valikatselmus-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :db-replica (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :valikatselmus (component/using
                           (valikatselmukset/->Valikatselmukset)
                           [:http-palvelin :db :db-replica])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; Testaa kaikki uuden tyyppiset päätökset

(defn lupauspaatos [urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet lupausbonus
                    lupaussanktio erilliskustannus-id sanktio-id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :tyyppi tyyppi
   :tavoitehinta tavoitehinta
   :luvatut_pisteet luvatut-pisteet
   :toteutuneet_pisteet toteutuneet-pisteet
   :lupausbonus lupausbonus
   :lupaussanktio lupaussanktio
   :erilliskustannus_id erilliskustannus-id
   :sanktio_id sanktio-id
   :luoja luoja})

(defn tavoitehinnan-muutospaatos [urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :versio versio
   :tavoitehinta tavoitehinta
   :kattohinta kattohinta
   :luoja luoja})

(defn tavoitehinnan-alituspaatos [urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                                  alituksen-maara siirron-maara tavoitepalkkio kulu-id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :versio versio
   :tavoitehinta tavoitehinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :alituksen_maara alituksen-maara
   :siirron_maara siirron-maara
   :tavoitepalkkio tavoitepalkkio
   :kulu_id kulu-id
   :luoja luoja})

(defn tavoitehinnan-ylityspaatos [urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                                  ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                                  urakoitsija-maksaa siirto kulu-id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :versio versio
   :tavoitehinta tavoitehinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :ylityksen_maara ylityksen-maara
   :tilaajan_prosentti tilaajan-prosentti
   :urakoitsijan_prosentti urakoitsijan-prosentti
   :tilaaja_maksaa tilaaja-maksaa
   :urakoitsija_maksaa urakoitsija-maksaa
   :siirto siirto
   :kulu_id kulu-id
   :luoja luoja})

(defn kattohinnan-ylityspaatos [urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :kattohinta kattohinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :ylityksen_maara ylityksen-maara
   :urakoitsija_maksaa urakoitsija-maksaa
   :siirrettava_maara siirrettava-maara
   :kulu_id kulu-id
   :luoja luoja})

(defn testaa-lupauspaatostiedot [paatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
                                 lupausbonus lupaussanktio erilliskustannus-id sanktio-id luoja]
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= luoja (:luoja paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= tyyppi (:tyyppi paatos)))
  (is (= luvatut-pisteet (:luvatut_pisteet paatos)))
  (is (= toteutuneet-pisteet (:toteutuneet_pisteet paatos)))
  (is (= lupausbonus (:lupausbonus paatos)))
  (is (= lupaussanktio (:lupaussanktio paatos)))
  (is (= erilliskustannus-id (:erilliskustannus_id paatos)))
  (is (= sanktio-id (:sanktio_id paatos)))
  (is (= urakkaid (:urakkaid paatos))))

(defn testaa-tavoitehinnan-muutospaatos [paatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= versio (:versio paatos)))
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= kattohinta (:kattohinta paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-tavoitehinnan-alitus [paatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                                   alituksen-maara siirron-maara tavoitepalkkio kulu-id luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= versio (:versio paatos)))
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= alituksen-maara (:alituksen_maara paatos)))
  (is (= siirron-maara (:siirron_maara paatos)))
  (is (= tavoitepalkkio (:tavoitepalkkio paatos)))
  (is (= kulu-id (:kulu_id paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-tavoitehinnan-ylityspaatos [paatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                                         ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                                         urakoitsija-maksaa siirto kulu-id luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= versio (:versio paatos)))
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= ylityksen-maara (:ylityksen_maara paatos)))
  (is (= tilaajan-prosentti (:tilaajan_prosentti paatos)))
  (is (= urakoitsijan-prosentti (:urakoitsijan_prosentti paatos)))
  (is (= tilaaja-maksaa (:tilaaja_maksaa paatos)))
  (is (= urakoitsija-maksaa (:urakoitsija_maksaa paatos)))
  (is (= siirto (:siirto paatos)))
  (is (= kulu-id (:kulu_id paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-kattohinnan-ylityspaatos [paatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                       ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= kattohinta (:kattohinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= ylityksen-maara (:ylityksen_maara paatos)))
  (is (= urakoitsija-maksaa (:urakoitsija_maksaa paatos)))
  (is (= siirrettava-maara (:siirrettava_maara paatos)))
  (is (= kulu-id (:kulu_id paatos)))
  (is (= luoja (:luoja paatos))))

;; Aloitetaan lupauksista
(deftest kysely-tee-lupauksetpaatos-bonus-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "bonus"
        tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 100M
        lupaussanktio nil
        erilliskustannus-id 1
        sanktio-id 1
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        vastaus (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) urakkaid lupauspaatos)]
    (testaa-lupauspaatostiedot vastaus urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
      lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)))

(deftest rajapinta-tee-lupauksetpaatos-bonus-onnistuu-test
  (let [urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "bonus"
        tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 1500M
        lupaussanktio nil
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        tallennettu-paatos (try
                             (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                           ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                           lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                                  {:lupaus-sitoutuminen {:pisteet 50}
                                                                                                   :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                                :pisteet {:maksimi 100
                                                                                                                          :ennuste 100
                                                                                                                          :toteuma 100}
                                                                                                                :bonus-tai-sanktio {:bonus lupausbonus}
                                                                                                                :tavoitehinta tavoitehinta
                                                                                                                :odottaa-kannanottoa 0
                                                                                                                :merkitsevat-odottaa-kannanottoa 0}})
                                           ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                             (catch Exception e e))
        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupausbonus
        erilliskustannus-bonus (first (erilliskustannus-kyselyt/hae-erilliskustannus (:db jarjestelma) {:urakka-id urakkaid
                                                                                                        :id (:erilliskustannus_id tallennettu-paatos)}))]
    (is (= lupausbonus (:lupausbonus tallennettu-paatos)) "Lupausbonuspäätöslukemat täsmää validoinnin jälkeen")
    (is (= lupausbonus (:rahasumma erilliskustannus-bonus)))))

(deftest rajapinta-tee-lupauksetpaatos-sanktio-onnistuu-test
  (let [urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "sakko"
        tavoitehinta 5M
        luvatut-pisteet 50
        toteutuneet-pisteet 10
        lupausbonus nil
        lupaussanktio 1500M
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        tallennettu-paatos (try
                             (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                           ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                           lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                                  {:lupaus-sitoutuminen {:pisteet 50}
                                                                                                   :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                                :pisteet {:maksimi 100
                                                                                                                          :ennuste 100
                                                                                                                          :toteuma 100}
                                                                                                                :bonus-tai-sanktio {:sanktio lupaussanktio}
                                                                                                                :tavoitehinta tavoitehinta
                                                                                                                :odottaa-kannanottoa 0
                                                                                                                :merkitsevat-odottaa-kannanottoa 0}})
                                           ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                             (catch Exception e e))
        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupaussanktio
        sanktio (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id tallennettu-paatos)))]
    (is (= lupaussanktio (:lupaussanktio tallennettu-paatos)) "Lupaussanktiopäätöslukemat täsmää validoinnin jälkeen")
    (is (= lupaussanktio (:maara sanktio)))))

;; Haetaan lupauspaatos
(deftest kysely-lupausbonus-haku-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "bonus"
        tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 100M
        lupaussanktio nil
        erilliskustannus-id 1
        sanktio-id 1
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet
                       toteutuneet-pisteet lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) urakkaid lupauspaatos)
        ;; Määrittele haettavat päätökset - Luetaan vain lupauspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}]
        vastaus (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (testaa-lupauspaatostiedot (first vastaus) urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
      lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)))

;; Poistetaan lupauspaatos
(deftest kysely-lupausbonus-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "bonus"
        tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 100M
        lupaussanktio nil
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet
                       toteutuneet-pisteet lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) urakkaid lupauspaatos)
        ;; Määrittele haettavat päätökset - Luetaan vain lupauspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}]
        lupausbonus-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))
        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-lupauspaatos (:db jarjestelma) urakkaid kayttajaid (:id lupausbonus-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-poista-lupauksetpaatos-sanktio-onnistuu-test
  (let [urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tyyppi "sakko"
        tavoitehinta 5M
        luvatut-pisteet 50
        toteutuneet-pisteet 10
        lupausbonus nil
        lupaussanktio 1500M
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio erilliskustannus-id sanktio-id kayttajaid)

        rajapinta-lupauspaatos (try
                                 (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                               ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                               lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                                      {:lupaus-sitoutuminen {:pisteet 50}
                                                                                                       :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                                    :pisteet {:maksimi 100
                                                                                                                              :ennuste 100
                                                                                                                              :toteuma 100}
                                                                                                                    :bonus-tai-sanktio {:sanktio lupaussanktio}
                                                                                                                    :tavoitehinta tavoitehinta
                                                                                                                    :odottaa-kannanottoa 0
                                                                                                                    :merkitsevat-odottaa-kannanottoa 0}})
                                               ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                               valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                                   (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                                 (catch Exception e e))

        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupaussanktio
        lupauspaatoksen-sanktio (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id rajapinta-lupauspaatos)))
        ;; Poistetaan päätös
        poistettu-paatos (kutsu-palvelua (:http-palvelin jarjestelma) :poista-lupauspaatos +kayttaja-jvh+ rajapinta-lupauspaatos)
        ;; Päätöksen poistamisen jälkeen enää ei pitäisi löytyä sanktiota
        lupauspaatoksen-poistettu-sanktio (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id rajapinta-lupauspaatos)))]
    (is (not (nil? lupauspaatoksen-sanktio)))
    (is (= lupaussanktio (:maara lupauspaatoksen-sanktio)))
    (is (nil? lupauspaatoksen-poistettu-sanktio))
    (is (= poistettu-paatos (:id rajapinta-lupauspaatos)))))

;; Testaa tavoitehinnan muutospäätöksen lisäys
(deftest kysely-tavoitehinnan-muutos-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "1a"
        tavoitehinta 5M
        kattohinta 5M
        paatos (tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) urakkaid paatos)]
    (testaa-tavoitehinnan-muutospaatos vastaus urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta kayttajaid)))

(deftest rajapinta-tavoitehinnan-muutos-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "1a"
        tavoitehinta 5M
        kattohinta 5M
        paatos (tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta kayttajaid)
        ;; Tarvitaanko feikattuja kyselyitä?
        tallennettu-paatos (try
                             (with-redefs [
                                           ;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                           valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                           kattohinta)
                                           ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-muutospaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")))

;; Poistetaan tavoitehinnan muutospäätös
(deftest tavoitehinnan-muutos-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio 1
        tavoitehinta 5M
        kattohinta 5M
        paatos (tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) urakkaid paatos)

        ;; Määrittele haettavat päätökset - Luetaan vain tavoitehinnan alituspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Tavoitehinnan muutokset" :tyyppi "A" :jarjestys 4}]
        paatos-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))

        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-tavoitehinnan-muutospaatos (:db jarjestelma) urakkaid kayttajaid (:id paatos-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-tavoitehinnan-muutospaatos-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "1a"
        tavoitehinta 5M
        kattohinta 5M
        paatos (tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta kattohinta kayttajaid)
        tallennettu-paatos (try
                             (with-redefs [
                                           ;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                           valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                           kattohinta)
                                           ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-muutospaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))
        _ (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
        _ (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")

        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-muutospaatos +kayttaja-jvh+ tallennettu-paatos)]
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

;; Testaa tavoitehinnan alituspäätöksen lisäsy
(deftest kysely-tavoitehinnan-alitus-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tavoitehinta 5M
        versio "1a"
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        kulu-id 1
        paatos (tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio kulu-id kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) urakkaid paatos)]
    (testaa-tavoitehinnan-alitus vastaus urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
      alituksen-maara siirron-maara tavoitepalkkio kulu-id kayttajaid)))

(deftest rajapinta-tavoitehinnan-alitus-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tavoitehinta 5M
        versio "1a"
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        kulu-id nil
        paatos (tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio kulu-id kayttajaid)
        tallennettu-paatos (try
                             (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-alituspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")))

;; Poistetaan tavoitehinnan alituspäätös
(deftest kysely-tavoitehinnanalitus-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tavoitehinta 5M
        versio 1
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        kulu-id 1
        paatos (tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio kulu-id kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) urakkaid paatos)

        ;; Määrittele haettavat päätökset - Luetaan vain tavoitehinnan alituspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Tavoitehinnan alitus" :tyyppi nil :jarjestys 9}]
        paatos-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))

        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-tavoitehinnan-alituspaatos (:db jarjestelma) urakkaid kayttajaid (:id paatos-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-tavoitehinnan-alitus-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        tavoitehinta 5M
        versio "1a"
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        kulu-id nil
        paatos (tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio kulu-id kayttajaid)
        tallennettu-paatos (try
                             (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-alituspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))
        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-alituspaatos +kayttaja-jvh+ tallennettu-paatos)]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

;; Tavoitehinnan ylitys lisäys
(deftest kysely-tavoitehinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "A"
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti 30 ;; Versio A 30/70, B 50/50, V 25/75
        urakoitsijan-prosentti 70
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id 1
        paatos (tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos (:db jarjestelma) urakkaid paatos)]
    (testaa-tavoitehinnan-ylityspaatos vastaus urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
      ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa urakoitsija-maksaa siirto kulu-id kayttajaid)))

(deftest rajapinta-tavoitehinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "A"
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti 30 ;; Versio A 30/70, B 50/50, V 25/75
        urakoitsijan-prosentti 70
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id nil
        paatos (tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id kayttajaid)
        tallennettu-paatos (try
                             (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-ylityspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")))

;; Tavoitehinnan ylitys - Poisto
(deftest kysely-tavoitehinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "A"
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti 30 ;; Versio A 30/70, B 50/50, V 25/75
        urakoitsijan-prosentti 70
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id 1
        paatos (tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos (:db jarjestelma) urakkaid paatos)

        ;; Määrittele haettavat päätökset - Luetaan vain tavoitehinnan alituspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Tavoitehinnan ylitys" :tyyppi "A" :jarjestys 10}]
        paatos-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))

        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-tavoitehinnan-ylityspaatos (:db jarjestelma) urakkaid kayttajaid (:id paatos-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-tavoitehinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        versio "A"
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti 30 ;; Versio A 30/70, B 50/50, V 25/75
        urakoitsijan-prosentti 70
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id nil
        paatos (tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi versio tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id kayttajaid)
        tallennettu-paatos (try
                             (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                           valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-ylityspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))
        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-ylityspaatos +kayttaja-jvh+ tallennettu-paatos)]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

;; Kattohinnan ylitys lisäys
(deftest kysely-kattohinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        kulu-id 1
        paatos (kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id kayttajaid)

        vastaus (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) urakkaid paatos)]
    (testaa-kattohinnan-ylityspaatos vastaus urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
      ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id kayttajaid)))

(deftest rajapinta-kattohinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        kulu-id nil
        paatos (kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id kayttajaid)

        tallennettu-paatos (try
                             (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                           valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                           kattohinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))]

    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")))

;; Kattohinnan ylitys lisäys - Poisto
(deftest kysely-kattohinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        kulu-id nil
        paatos (kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id kayttajaid)
        _ (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) urakkaid paatos)

        ;; Määrittele haettavat päätökset - Luetaan vain tavoitehinnan alituspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Kattohinnan ylitys" :tyyppi "A" :jarjestys 10}]
        paatos-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))

        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-kattohinnan-ylityspaatos (:db jarjestelma) urakkaid kayttajaid (:id paatos-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-kattohinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        kulu-id nil
        paatos (kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id kayttajaid)

        tallennettu-paatos (try
                             (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                           valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                           kattohinta)]
                               (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                             (catch Exception e e))
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-kattohinnan-ylityspaatos +kayttaja-jvh+ tallennettu-paatos)]

    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))
