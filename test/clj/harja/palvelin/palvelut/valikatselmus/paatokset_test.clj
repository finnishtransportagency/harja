(ns harja.palvelin.palvelut.valikatselmus.paatokset-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.palvelin.palvelut.kulut.kulu-apurit :as kuluapurit]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.kyselyt.erilliskustannus-kyselyt :as erilliskustannus-kyselyt]
            [harja.kyselyt.sanktiot :as sanktio-kyselyt]
            [harja.kyselyt.valikatselmus :as valikatselmus-kyselyt]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-kyselyt]
            [harja.kyselyt.jarjestelman-tila :as jarjestelma-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]
            [taoensso.timbre :as log]))

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
                           [:http-palvelin :db :db-replica])
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn valitse-paatos
  "ValikatselmusPalvelu palauttaa aina listan päätöksiä, joiden avaimista pitää päätellä, että mikä päätös on kyseessä."
  [paatokset avain]
  (get (first (filter #(= (ffirst %) avain) paatokset)) avain))

;; Testaa kaikki uuden tyyppiset päätökset

(defn poytakirjan-raporttipaatos [urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :tarkistettu tarkistettu
   :luoja kayttajaid})

(defn testaa-lupauspaatostiedot [paatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                                 lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id luoja]
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= tarjous-tavoitehinta (:tarjous_tavoitehinta paatos)))
  (is (= luoja (:luoja paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= tyyppi (:tyyppi paatos)))
  (is (= luvatut-pisteet (:luvatut_pisteet paatos)))
  (is (= toteutuneet-pisteet (:toteutuneet_pisteet paatos)))
  (is (= lupausbonus (:lupausbonus paatos)))
  (is (= lupaussanktio (:lupaussanktio paatos)))
  (is (= bonusprosentti (:bonusprosentti paatos)))
  (is (= sanktioprosentti (:sanktioprosentti paatos)))
  (is (= indeksi (:indeksi paatos)))
  (is (= indeksikorotus (:indeksikorotus paatos)))
  (is (= erilliskustannus-id (:erilliskustannus_id paatos)))
  (is (= sanktio-id (:sanktio_id paatos)))
  (is (= urakkaid (:urakkaid paatos))))

(defn testaa-tavoitehinnan-muutospaatos [paatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= muokkaa-kattohinta (:muokkaa_kattohinta paatos)))
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= kattohinta (:kattohinta paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-tavoitehinnan-alitus [paatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                                   alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id
                                   viimeinen_hoitokausi luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= hoitokauden-alun-tavoitehinta (:hoitokauden_alun_tavoitehinta paatos)))
  (is (= hoitokauden-lopun-tavoitehinta (:hoitokauden_lopun_tavoitehinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= alituksen-maara (:alituksen_maara paatos)))
  (is (= siirron-maara (:siirron_maara paatos)))
  (is (= tavoitepalkkio (:tavoitepalkkio paatos)))
  (is (= kulu-id (:kulu_id paatos)))
  (is (= tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti paatos)))
  (is (= tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi_prosentti paatos)))
  (is (= viimeinen_hoitokausi (:viimeinen_hoitokausi paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-tavoitehinnan-ylityspaatos [paatos urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                                         ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                                         urakoitsija-maksaa siirto kulu-id viimeinen_hoitokausi luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= tavoitehinta (:tavoitehinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= ylityksen-maara (:ylityksen_maara paatos)))
  (is (= (bigint tilaajan-prosentti) (bigint (:tilaajan_prosentti paatos))))
  (is (= (bigint urakoitsijan-prosentti) (bigint (:urakoitsijan_prosentti paatos))))
  (is (= tilaaja-maksaa (:tilaaja_maksaa paatos)))
  (is (= urakoitsija-maksaa (:urakoitsija_maksaa paatos)))
  #_(is (= siirto (:siirto paatos))) ;; Siirron rooli vähän epäselvä, ei vielä varmisteta
  (is (= kulu-id (:kulu_id paatos)))
  (is (= viimeinen_hoitokausi (:viimeinen_hoitokausi paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-kattohinnan-ylityspaatos [paatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                       ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi
                                       maksimi-siirrettava-maara siirtorajoitus-prosentti luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= kattohinta (:kattohinta paatos)))
  (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
  (is (= ylityksen-maara (:ylityksen_maara paatos)))
  (is (= urakoitsija-maksaa (:urakoitsija_maksaa paatos)))
  (is (= siirrettava-maara (:siirrettava_maara paatos)))
  (is (= kulu-id (:kulu_id paatos)))
  (is (= viimeinen_hoitokausi (:viimeinen_hoitokausi paatos)))
  (is (= maksimi-siirrettava-maara (:maksimi_siirrettava_maara paatos)))
  (is (= siirtorajoitus-prosentti (:siirtorajoitus_prosentti paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-indeksikorjauspaatos [paatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                                   hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                                   pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus
                                   hoitokauden-lopun-indeksikorjaus luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= hv_alun_indkorj_tavoitehinta (:hv_alun_indkorj_tavoitehinta paatos)))
  (is (= tavoitehinnan-muutokset (:tavoitehinnan_muutokset paatos)))
  (is (= hv_lopun_tavoitehinta_ennen_indkorj (:hv_lopun_tavoitehinta_ennen_indkorj paatos)))
  (is (= hoitokauden-kuukaudet (:hoitokauden_kuukaudet paatos)))
  (is (= (bigdec kuukausien-keskiarvo) (bigdec (:kuukausien_keskiarvo paatos))))
  (is (= (bigdec alkuperainen-pisteluku) (:alkuperainen_pisteluku paatos)))
  (is (= alkuperaisen-pisteluvun-kuukausi (:alkuperaisen_pisteluvun_kuukausi paatos)))
  (is (= (bigdec pistelukujen-muutos) (:pistelukujen_muutos paatos)))
  (is (= (bigdec pistelukujen-muutos-prosentteina) (:pistelukujen_muutos_prosentteina paatos)))
  (is (= (bigdec indeksikorotuksen-prosenttiosuus) (:indeksikorotuksen_prosenttiosuus paatos)))
  (is (= hoitokauden-lopun-indeksikorjaus (:hoitokauden_lopun_indeksikorjaus paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-lopun-hintapaatos [paatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                                tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= tavoitehinta_ennen (:tavoitehinta_ennen paatos)))
  (is (= hoitokauden-lopun-indeksikorjaus (:hoitokauden_lopun_indeksikorjaus paatos)))
  (is (= tavoitehinnan_muutokset (:tavoitehinnan_muutokset paatos)))
  (is (= tavoitehinta_jalkeen (:tavoitehinta_jalkeen paatos)))
  (is (= (int kattohinta) (int (:kattohinta paatos))))
  (is (= kattohintakerroin (:kattohintakerroin paatos)))
  (is (= lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_lopunindeksikorjaus paatos)))
  (is (= kayttajaid (:luoja paatos))))

(defn testaa-hoidojohtopalkkiomuutospaatos [paatos urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
                                            muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  (is (= hv_lopun_indkorjaamaton_tavoitehinta (:hv_lopun_indkorjaamaton_tavoitehinta paatos)))
  (is (= tarjouksen_tavoitehinta (:tarjouksen_tavoitehinta paatos)))
  (is (= muutosprosentti (:muutosprosentti paatos)))
  (is (= hoidonjohtopalkkio (:hoidonjohtopalkkio paatos)))
  (is (= hoidonjohtopalkkio_muutos (:hoidonjohtopalkkio_muutos paatos)))
  (is (= luoja (:luoja paatos))))

(defn testaa-poytakirjan-raporttipaatos [paatos urakkaid hoitokauden-alkuvuosi tarkistettu luoja]
  (is (= urakkaid (:urakkaid paatos)))
  (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)))
  ;; Jätetään millisekuntivertailut pois, koska testin ajamisessa menee hetki. Päivän tarkkuus riittää.
  (is (= (pvm/sql-aika->pvm-str tarkistettu) (pvm/sql-aika->pvm-str (:tarkistettu paatos))))
  (is (= luoja (:luoja paatos))))

;; Aloitetaan lupauksista
(deftest kysely-tee-lupauksetpaatos-bonus-onnistuu-test
  (testing "2024 vuoden urakalle onnistuu"
    (let [;; Hae vaativa mhu urakka
          paatos-pvm (pvm/->pvm "12.05.2024")
          urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
          indeksi (:indeksi urakan-tiedot)
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
          kayttajaid (:id +kayttaja-jvh+)
          hoitokauden-alkuvuosi 2024
          tyyppi "bonus"
          tavoitehinta 5M
          tarjous-tavoitehinta 5M
          luvatut-pisteet 5
          toteutuneet-pisteet 10
          lupausbonus 100M
          indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
          lupaussanktio nil
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
          erilliskustannus-id 1
          sanktio-id 1
          lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                         lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

          vastaus (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)]
      (testaa-lupauspaatostiedot vastaus urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
        lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)))
  (testing "2020 vuoden urakalle onnistuu"
    (let [paatos-pvm (pvm/->pvm "12.05.2020")
          urakkaid (hae-urakan-id-nimella "Oulun MHU 2019-2024")
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
          urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
          indeksi (:indeksi urakan-tiedot)
          kayttajaid (:id +kayttaja-jvh+)
          hoitokauden-alkuvuosi 2020
          tyyppi "bonus"
          tavoitehinta 150000M
          tarjous-tavoitehinta 5M
          luvatut-pisteet 5
          toteutuneet-pisteet 10
          lupausbonus 100M
          indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
          _ (is (> indeksikorotus 0) "Indeksikorotus ei voi olla nolla tai nil 2020 alkavalla urakalla.")
          lupaussanktio nil
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
          erilliskustannus-id 1
          sanktio-id 1
          lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                         lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

          vastaus (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)]
      (testaa-lupauspaatostiedot vastaus urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
        lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid))))

(deftest rajapinta-tee-lupauksetpaatos-bonus-onnistuu-test
  (let [paatos-pvm (pvm/->pvm "12.05.2024")
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tyyppi "bonus"
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 1500M
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
        lupaussanktio nil
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)
        vastaus (try
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
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :lupaukset)
        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupausbonus
        erilliskustannus-bonus (first (erilliskustannus-kyselyt/hae-erilliskustannus (:db jarjestelma) {:urakka-id urakkaid
                                                                                                        :id (:erilliskustannus_id tallennettu-paatos)}))]
    (is (= lupausbonus (:lupausbonus tallennettu-paatos)) "Lupausbonuspäätöslukemat täsmää validoinnin jälkeen")
    (is (= lupausbonus (:rahasumma erilliskustannus-bonus)))))

(deftest rajapinta-tee-lupauksetpaatos-sanktio-onnistuu-test
  (let [paatos-pvm (pvm/->pvm "12.05.2024")
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tyyppi "sanktio"
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        luvatut-pisteet 50
        toteutuneet-pisteet 10
        lupausbonus nil
        lupaussanktio 1500M
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupaussanktio true)
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id nil
        sanktio-id nil
        tallennettava-sanktio (atom nil)
        lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

        vastaus (try
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
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)

                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])
                                ;; Feikataan sanktio tallentaminen, koska testidatassa ei ole lupaussanktio-lajia urakan profiilissa
                                laadunseuranta/tallenna-suorasanktio (fn [db kayttaja sanktio laatupoikkeama urakka konteksti]
                                                                       (reset! tallennettava-sanktio sanktio)
                                                                       1)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                  (catch Exception e (do (log/error "Testissä virhe:" (.getMessage e) "Cause:" (.getCause e)) e)))

        _ (when (instance? Throwable vastaus) (throw vastaus))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :lupaukset)]
    (is (= lupaussanktio (:lupaussanktio tallennettu-paatos)) "Lupaussanktiopäätöslukemat täsmää validoinnin jälkeen")
    (is (= lupaussanktio (:summa @tallennettava-sanktio)))))

;; Haetaan lupauspaatos
(deftest kysely-lupausbonus-haku-onnistuu-test
  (let [paatos-pvm (pvm/->pvm "12.05.2024")
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tyyppi "bonus"
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 100M
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
        lupaussanktio nil
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id 1
        sanktio-id 1
        lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet
                       toteutuneet-pisteet lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

        _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)
        ;; Määrittele haettavat päätökset - Luetaan vain lupauspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}]
        vastaus (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (testaa-lupauspaatostiedot (first vastaus) urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta
      luvatut-pisteet toteutuneet-pisteet lupausbonus lupaussanktio bonusprosentti sanktioprosentti  indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)))

;; Poistetaan lupauspaatos
(deftest kysely-lupausbonus-poisto-onnistuu-test
  (let [paatos-pvm (pvm/->pvm "12.05.2024")
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tyyppi "bonus"
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        luvatut-pisteet 5
        toteutuneet-pisteet 10
        lupausbonus 100M
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
        lupaussanktio nil
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id 1
        sanktio-id nil
        lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet
                       toteutuneet-pisteet lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

        _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)
        ;; Määrittele haettavat päätökset - Luetaan vain lupauspäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}]
        lupausbonus-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))
        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-lupauspaatos (:db jarjestelma) urakkaid kayttajaid (:id lupausbonus-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-poista-lupauksetpaatos-sanktio-onnistuu-test
  (let [paatos-pvm (pvm/->pvm "12.05.2024")
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tyyppi "sanktio"
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        luvatut-pisteet 50
        toteutuneet-pisteet 10
        lupausbonus nil
        lupaussanktio 1500M
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupaussanktio true)
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id nil
        sanktio-id nil
        lupauspaatos (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

        vastaus (try
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
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :lupaukset)

        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupaussanktio
        lupauspaatoksen-sanktio (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id tallennettu-paatos)))
        ;; Poistetaan päätös
        poisto-vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                     jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                        (kutsu-palvelua (:http-palvelin jarjestelma) :poista-lupauspaatos +kayttaja-jvh+ tallennettu-paatos))
        poistettu-paatos (valitse-paatos (:paatokset poisto-vastaus) :lupaukset)
        ;; Päätöksen poistamisen jälkeen enää ei pitäisi löytyä sanktiota
        lupauspaatoksen-poistettu-sanktio (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id tallennettu-paatos)))]
    (is (not (nil? lupauspaatoksen-sanktio)))
    (is (= lupaussanktio (:maara lupauspaatoksen-sanktio)))
    (is (nil? lupauspaatoksen-poistettu-sanktio))
    (is (= "Lupaukset" (:nimi poistettu-paatos)))))

(deftest tee-lupauspaatos-tyyppi-tarkistus-test
  (testing "Varmistetaan, että bonus/sanktio tallennetaan vain kun :tyyppi on oikea - negatiivinen regressiotesti"
    (let [paatos-pvm (pvm/->pvm "12.05.2024")
          urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
          indeksi (:indeksi urakan-tiedot)
          kayttajaid (:id +kayttaja-jvh+)
          hoitokauden-alkuvuosi 2024
          tavoitehinta 5M
          tarjous-tavoitehinta 5M
          luvatut-pisteet 5
          toteutuneet-pisteet 10
          lupausbonus 1500M
          lupaussanktio 2000M
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
          indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakkaid paatos-pvm indeksi lupausbonus false)
          
          ;; Testi 1: Bonus-päätös (tyyppi="bonus") - datassa on sekä bonus että sanktio, mutta vain bonus saa tallentua
          lupauspaatos-bonus (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi "bonus" tavoitehinta tarjous-tavoitehinta
                               luvatut-pisteet toteutuneet-pisteet lupausbonus lupaussanktio 
                               bonusprosentti sanktioprosentti indeksi indeksikorotus nil nil kayttajaid)
          vastaus-bonus (try
                          (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                        lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle 
                                        (fn [db hakuparametrit]
                                          {:lupaus-sitoutuminen {:pisteet 50}
                                           :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                        :pisteet {:maksimi 100 :ennuste 100 :toteuma 100}
                                                        :bonus-tai-sanktio {:bonus lupausbonus}
                                                        :tavoitehinta tavoitehinta
                                                        :odottaa-kannanottoa 0
                                                        :merkitsevat-odottaa-kannanottoa 0}})
                                        valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta 
                                        (fn [db hakuparametrit] tavoitehinta)
                                        jarjestelma-kyselyt/hae-jarjestelman-asetukset 
                                        (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                            (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos-bonus))
                          (catch Exception e e))
          tallennettu-bonus (valitse-paatos (:paatokset vastaus-bonus) :lupaukset)
          
          erilliskustannus-bonus (when (:erilliskustannus_id tallennettu-bonus)
                                   (first (erilliskustannus-kyselyt/hae-erilliskustannus 
                                            (:db jarjestelma) 
                                            {:urakka-id urakkaid :id (:erilliskustannus_id tallennettu-bonus)})))
          
          ;; Testi 2: Sanktio-päätös (tyyppi="sanktio") - datassa on sekä bonus että sanktio, mutta vain sanktio saa tallentua
          lupauspaatos-sanktio (paatos-apurit/lupauspaatos urakkaid hoitokauden-alkuvuosi "sanktio" tavoitehinta tarjous-tavoitehinta
                                 luvatut-pisteet 5 lupausbonus lupaussanktio 
                                 bonusprosentti sanktioprosentti indeksi indeksikorotus nil nil kayttajaid)
          vastaus-sanktio (try
                            (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                          lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle 
                                          (fn [db hakuparametrit]
                                            {:lupaus-sitoutuminen {:pisteet 50}
                                             :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                          :pisteet {:maksimi 100 :ennuste 100 :toteuma 100}
                                                          :bonus-tai-sanktio {:sanktio lupaussanktio}
                                                          :tavoitehinta tavoitehinta
                                                          :odottaa-kannanottoa 0
                                                          :merkitsevat-odottaa-kannanottoa 0}})
                                          valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta 
                                          (fn [db hakuparametrit] tavoitehinta)
                                          jarjestelma-kyselyt/hae-jarjestelman-asetukset 
                                          (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                              (kutsu-palvelua (:http-palvelin jarjestelma) :tee-lupauspaatos +kayttaja-jvh+ lupauspaatos-sanktio))
                            (catch Exception e e
                              (println "ERROR: " e)))
          tallennettu-sanktio (valitse-paatos (:paatokset vastaus-sanktio) :lupaukset)
          sanktio (when (:sanktio_id tallennettu-sanktio)
                    (first (sanktio-kyselyt/hae-sanktio (:db jarjestelma) (:sanktio_id tallennettu-sanktio))))]
      
      ;; Assertiot bonukselle: Bonus tallennetaan, sanktio EI tallennu
      (is (not (nil? erilliskustannus-bonus)) 
          "Bonus tallennettiin kun tyyppi oli 'bonus'")
      (is (= lupausbonus (:rahasumma erilliskustannus-bonus)) 
          "Lupausbonus on oikea")
      (is (nil? (:sanktio_id tallennettu-bonus)) 
          "REGRESSIOTESTI: Sanktiota ei tallennettu vaikka datassa oli :lupaussanktio, koska tyyppi oli 'bonus'")
      
      ;; Assertiot sanktiolle: Sanktio tallennetaan, bonus EI tallennu
      (is (not (nil? sanktio)) 
          "Sanktio tallennettiin kun tyyppi oli 'sanktio'")
      (is (= lupaussanktio (:maara sanktio)) 
          "Lupaussanktio on oikea")
      (is (nil? (:erilliskustannus_id tallennettu-sanktio)) 
          "REGRESSIOTESTI: Bonusta ei tallennettu vaikka datassa oli :lupausbonus, koska tyyppi oli 'sanktio'"))))


;; Testaa tavoitehinnan muutospäätöksen lisäys
(deftest kysely-tavoitehinnan-muutos-lisays-onnistuu-2025-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2025
        muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) paatos kayttajaid)]
    (testaa-tavoitehinnan-muutospaatos vastaus urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)

    (is (= urakkaid (:urakkaid vastaus)))
    (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus)))
    (is (= tavoitehinta (:tavoitehinta vastaus)))
    (is (= kattohinta (:kattohinta vastaus)))
    (is (= false (:muokkaa_kattohinta vastaus)))))

(deftest kysely-tavoitehinnan-muutos-lisays-onnistuu-2024-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) paatos kayttajaid)]
    (testaa-tavoitehinnan-muutospaatos vastaus urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)
    (is (= urakkaid (:urakkaid vastaus)))
    (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus)))
    (is (= tavoitehinta (:tavoitehinta vastaus)))
    (is (= kattohinta (:kattohinta vastaus)))
    (is (= false (:muokkaa_kattohinta vastaus)))))

(deftest kysely-tavoitehinnan-muutos-lisays-onnistuu-2019-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2020
        muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) paatos kayttajaid)

        ;; Kun kattohinta on käsin asetettu uusiksi, niin sen täytyy vaikuttaa urakka_tavoite taulun kattohintaan
        ;; Varmistetaan, että näin on tapahtunut
        hoitokaudennro 2                                    ;; Oulun urakka alkaa 2019, joten joten 2020 on hoitokauden 2
        urakkatavoite (first (q-map (format "SELECT * FROM urakka_tavoite
                                       WHERE urakka = %s
                                         AND hoitokausi = %s" urakkaid hoitokaudennro)))
        _ (is (= kattohinta (:kattohinta urakkatavoite)) "Kattohinta on asetettu oikein urakka_tavoite tauluun")
        _ (is (= kattohinta (:kattohinta_indeksikorjattu urakkatavoite)) "Kattohinta on asetettu oikein urakka_tavoite tauluun")]
    (testaa-tavoitehinnan-muutospaatos vastaus urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)
    ;; -19 alkavalla urakalla pitää olla kattohinta käsin muokattavana
    (is (= urakkaid (:urakkaid vastaus)))
    (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi vastaus)))
    (is (= tavoitehinta (:tavoitehinta vastaus)))
    (is (= kattohinta (:kattohinta vastaus)))
    (is (= true (:muokkaa_kattohinta vastaus)))))

(deftest rajapinta-tavoitehinnan-muutos-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)
        ;; Tarvitaanko feikattuja kyselyitä?
        vastaus (try
                  (with-redefs [
                                ;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit] kattohinta)
                                ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-muutospaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinnan-muutokset)]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")))

;; Poistetaan tavoitehinnan muutospäätös
(deftest tavoitehinnan-muutos-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        muokkaa-kattohinta false
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) paatos kayttajaid)

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
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        muokkaa-kattohinta false
        tavoitehinta 5M
        kattohinta 5M
        paatos (paatos-apurit/tavoitehinnan-muutospaatos urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta kayttajaid)
        vastaus (try
                  (with-redefs [
                                ;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                kattohinta)
                                ;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-muutospaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinnan-muutokset)
        _ (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
        _ (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")

        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-muutospaatos +kayttaja-jvh+ tallennettu-paatos)
        ;; Annetuilla arvoilla poistettua päätöstä ei löydy, vaan default päätös
        poistettu-paatos (valitse-paatos (:paatokset poistovastaus) :tavoitehinnan-muutokset)]
    (is (nil? (:luotu poistettu-paatos)))
    (is (= urakkaid (:urakkaid poistettu-paatos)))))

;; Testaa tavoitehinnan alituspäätöksen lisääminen
(deftest kysely-tavoitehinnan-alitus-lisays-2021-onnistuu-test
  (let [hoitokauden-alkuvuosi 2024
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        ;; Haetaan urakan parametrit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id 1
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) paatos)]
    (testaa-tavoitehinnan-alitus vastaus urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
      alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)))

(deftest kysely-tavoitehinnan-alitus-lisays-2025-onnistuu-viimeiselle-hoitovuodelle-test
  (let [hoitokauden-alkuvuosi 2025
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        ;; Haetaan urakan parametrit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        alituksen-maara 300M
        toteutuneet-kustannukset (- hoitokauden-alun-tavoitehinta alituksen-maara)
        siirron-maara 0M ;; Viimeisenä hoitovuotena ei siirretä tietenkään enää mitään tulevaisuuteen
        ;; Tavoitepalkkio on on urakan prosentit, alituksen määrästä
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkio (* (/ tavoitepalkkion-maksuprosentti 100) alituksen-maara)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id 1
        viimeinen-hoitovuosi true
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id viimeinen-hoitovuosi kayttajaid)
        vastaus (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) paatos)]
    (is (= tavoitepalkkio (:tavoitepalkkio paatos)))
    (testaa-tavoitehinnan-alitus vastaus urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
      alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id viimeinen-hoitovuosi kayttajaid)))

(deftest kysely-tavoitehinnan-alitus-lisays-2025-onnistuu-test
  (let [hoitokauden-alkuvuosi 2025
        urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-alkuvuosi (-> (:alkupvm urakan-tiedot) pvm/vuosi)
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
        valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        budjettitavoite (budjettisuunnittelu-kyselyt/hae-budjettitavoite (:db jarjestelma) {:urakka urakkaid})
        budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) budjettitavoite)
        hoitokauden-lopun-tavoitehinta (valikatselmukset/maarita-hv-lopun-indeksikorjattu-tavoitehinta
                                         (:db jarjestelma) +kayttaja-jvh+ hoitokauden-alkuvuosi valittu-hoitokausi urakkaid urakan-alkuvuosi budjettitavoite-vuodelle)
        ;; Haetaan urakan parametrit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id 1
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) paatos)]
    (testaa-tavoitehinnan-alitus vastaus urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
      alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)))

(deftest rajapinta-tavoitehinnan-alitus-2021-lisays-onnistuu-test
  (let [hoitokauden-alkuvuosi 2021
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta
                                        (:db jarjestelma) {:urakka-id urakkaid
                                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        ;; Haetaan urakan parametrit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id nil
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)
        vastaus (try
                  (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hoitokauden-alun-tavoitehinta)
                                valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] hoitokauden-lopun-tavoitehinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-alituspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinnan-alitus)]
    (is (= "Tavoitehinnan alitus" (:nimi tallennettu-paatos)) "Päätöksen nimi täsmää")
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset tallennettu-paatos)) "Toteutuneet kustannukset tallentuu oikein")
    (is (= tavoitepalkkio (:tavoitepalkkio tallennettu-paatos)) "Tavoitepalkkio tallentuu oikein")
    (is (= siirron-maara (:siirron_maara tallennettu-paatos)) "Siirron määrä tallentuu oikein")
    (is (= alituksen-maara (:alituksen_maara tallennettu-paatos)) "Alituksen määrä tallentuu oikein")
    (is (= hoitokauden-alun-tavoitehinta (:hoitokauden_alun_tavoitehinta tallennettu-paatos)) "Hoitokauden alun tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen.")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")))

(deftest rajapinta-tavoitehinnan-alitus-2025-lisays-onnistuu-test
  (let [hoitokauden-alkuvuosi 2025
        urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-alkuvuosi (-> (:alkupvm urakan-tiedot) pvm/vuosi)
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
        valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta
                                        (:db jarjestelma) {:urakka-id urakkaid
                                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        budjettitavoite (budjettisuunnittelu-kyselyt/hae-budjettitavoite (:db jarjestelma) {:urakka urakkaid})
        budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) budjettitavoite)
        hoitokauden-lopun-tavoitehinta (valikatselmukset/maarita-hv-lopun-indeksikorjattu-tavoitehinta
                                         (:db jarjestelma) +kayttaja-jvh+ hoitokauden-alkuvuosi valittu-hoitokausi urakkaid urakan-alkuvuosi budjettitavoite-vuodelle)

        ;; Haetaan urakan parametrit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        haluttu-alituksen-maara 10M
        toteutuneet-kustannukset (- hoitokauden-lopun-tavoitehinta haluttu-alituksen-maara)
        alituksen-maara (- hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset)
        siirron-maara haluttu-alituksen-maara
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id nil
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)
        vastaus (try
                  (with-redefs [;; Validoinnin takia päätöksiä ei saada kuluvalle hoitovuodelle haettua, joten feikataan nykyhetki
                                pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2026 10 15))]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-alituspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinnan-alitus)]
    (is (= "Tavoitehinnan alitus" (:nimi tallennettu-paatos)) "Päätöksen nimi täsmää")
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset tallennettu-paatos)) "Toteutuneet kustannukset tallentuu oikein")
    (is (= tavoitepalkkio (:tavoitepalkkio tallennettu-paatos)) "Tavoitepalkkio tallentuu oikein")
    (is (= siirron-maara (:siirron_maara tallennettu-paatos)) "Siirron määrä tallentuu oikein")
    (is (= alituksen-maara (:alituksen_maara tallennettu-paatos)) "Alituksen määrä tallentuu oikein")
    (is (= hoitokauden-alun-tavoitehinta (:hoitokauden_alun_tavoitehinta tallennettu-paatos)) "Hoitokauden alun tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen.")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")))

;; Poistetaan tavoitehinnan alituspäätös
(deftest kysely-tavoitehinnanalitus-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        hoitokauden-alkuvuosi 2021
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        ;; Hae urakan hoitokauden alun tavoitehinta
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta
                                        (:db jarjestelma) {:urakka-id urakkaid
                                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})

        kayttajaid (:id +kayttaja-jvh+)

        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id 1
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) paatos)

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
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2021
        hoitokauden-alun-tavoitehinta (valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakkaid :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        toteutuneet-kustannukset 5M
        alituksen-maara 10M
        siirron-maara 100M
        tavoitepalkkio 150M
        tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
        tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
        kulu-id nil
        paatos (paatos-apurit/tavoitehinnan-alituspaatos urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                 alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id false kayttajaid)
        vastaus (try
                  (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hoitokauden-alun-tavoitehinta)
                                valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] hoitokauden-lopun-tavoitehinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-alituspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinnan-alitus)
        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-alituspaatos +kayttaja-jvh+ tallennettu-paatos)
        poistettu-paatos (valitse-paatos (:paatokset poistovastaus) :tavoitehinnan-alitus)]
    (is (= hoitokauden-alun-tavoitehinta (:hoitokauden_alun_tavoitehinta tallennettu-paatos)) "Hoitokauden alun tavoitehinta on sama päätöksen tekemisen jälkeen")
    (is (= hoitokauden-lopun-tavoitehinta (:hoitokauden_lopun_tavoitehinta tallennettu-paatos)) "Hoitokauden lopun tavoitehinta on sama päätöksen tekemisen jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")
    ;; Päätöksen poistaminen aiheuttaa default päätöksen palauttamisen
    (is (nil? (:luotu poistettu-paatos)))
    (is (= urakkaid (:urakkaid poistettu-paatos)))
    (is (= "Tavoitehinnan alitus" (:nimi poistettu-paatos)))))

;; Tavoitehinnan ylitys lisäys
(deftest kysely-tavoitehinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit))
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id 1
        paatos (paatos-apurit/tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id false kayttajaid)

        vastaus (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos (:db jarjestelma) paatos)]
    (testaa-tavoitehinnan-ylityspaatos vastaus urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
      ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa urakoitsija-maksaa siirto kulu-id false kayttajaid)))

(deftest rajapinta-tavoitehinnan-ylitys-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit))
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id nil
        paatos (paatos-apurit/tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id false kayttajaid)
        vastaus (try
                  (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                                valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] tavoitehinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :tavoitehinta-ylitys)]
    ;; Ehdot ei täyty, joten default tavoitehinnan ylityspäätöstä ei voida palauttaa
    (is (nil? tallennettu-paatos))))

;; Tavoitehinnan ylitys - Poisto
(deftest kysely-tavoitehinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit))
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id 1
        paatos (paatos-apurit/tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id false kayttajaid)
        _ (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos (:db jarjestelma) paatos)

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
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit))
        tilaaja-maksaa 150M
        urakoitsija-maksaa 50M
        siirto 50M
        kulu-id nil
        paatos (paatos-apurit/tavoitehinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                 ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                 urakoitsija-maksaa siirto kulu-id false kayttajaid)
        ;; Ei odoteta vastausta, koska ehdot ei täyty
        _ (try
            (with-redefs [;; Urakalla ei välttämättä ole tavoitehintaa, niin feikataan se tässä
                          budjettisuunnittelu-kyselyt/hae-budjettitavoite
                          (fn [db hakuparametrit] [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                    :tavoitehinta-oikaistu tavoitehinta
                                                    :hoitovuoden-lopun-kattohinta (* 1.1 tavoitehinta)}])]
              (kutsu-palvelua (:http-palvelin jarjestelma) :tee-tavoitehinnan-ylityspaatos +kayttaja-jvh+ paatos))
            (catch Exception e e
              (println "ERROR:" (.getMessage e))))
        ;; Haetaan sen sijaan tehty päätös suoraan tietokannasta
        haettavat-paatokset [{:nimi "Tavoitehinnan ylitys" :tyyppi "A" :jarjestys 10}]
        tietokantapaatokset (paatos-kyselyt/hae-paatokset (:db jarjestelma) haettavat-paatokset urakkaid hoitokauden-alkuvuosi)
        tallennettu-paatos (first tietokantapaatokset)

        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-tavoitehinnan-ylityspaatos +kayttaja-jvh+ tallennettu-paatos)
        poistettu-paatos (valitse-paatos (:paatokset poistovastaus) :tavoitehinta-ylitys)]
    (is (= tavoitehinta (:tavoitehinta tallennettu-paatos)) "Tavoitehinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    (is (< 0 (:kulu_id tallennettu-paatos)) "Kulu_id lisätty tallennuksen yhteydessä")
    ;; Koska ehdot eivät täyty, niin poiston jälkein välikatselmussivun palaute ei palauta tavoitehinnan ylityspäätöst
    (is (nil? poistettu-paatos))))

;; Kattohinnan ylitys lisäys
(deftest kysely-kattohinnan-ylitys-lisays-2024-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        viimeinen_hoitokausi false
        kulu-id 1
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) paatos)]
    (testaa-kattohinnan-ylityspaatos vastaus urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
      ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)))

(deftest kysely-kattohinnan-ylitys-lisays-2025-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        viimeinen_hoitokausi false
        kulu-id 1
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) paatos)]
    (testaa-kattohinnan-ylityspaatos vastaus urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
      ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

    ;; -25 alkavalla urakalla siirtorajoitusprosentti pitää olla kolme
    (is (= 0.03M (:siirtorajoitus_prosentti vastaus)))))

(deftest rajapinta-kattohinnan-ylitys-lisays-epaonnistuu-2024-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id nil
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                kattohinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e
                    (is (not (nil? e)))))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :kattohinnan-ylitys)]
    ;; Koska ehdot eivät täyty, niin kattohinnan ylityspäätöksen default päätöstä ei voida palauttaa
    (is (nil? tallennettu-paatos))))

(deftest rajapinta-kattohinnan-ylitys-lisays-onnistuu-2024-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-alkupvm (:alkupvm urakan-tiedot)
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 50M
        toteutuneet-kustannukset 55M
        ylityksen-maara (- toteutuneet-kustannukset kattohinta)
        urakoitsija-maksaa ylityksen-maara
        siirrettava-maara 0M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys

        ;; Luodaan kulu, jolla ylitetään kattohinta
        uusi-kulu (kuluapurit/uusi-kulu urakkaid toteutuneet-kustannukset hoitokauden-alkuvuosi urakan-alkupvm)

        kulu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kulu
                       +kayttaja-jvh+
                       {:urakka-id urakkaid
                        :kulu-kohdistuksineen uusi-kulu})

        kulu-id (:id kulu-vastaus)
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite (fn [db hakuparametrit]
                                                                                  [{:hoitovuoden-lopun-kattohinta kattohinta
                                                                                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}])
                                budjettisuunnittelu-kyselyt/onko-kustannussuunnitelma-vahvistettu (fn [db hakuparametrit]
                                                                                                    [{:exists true}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e
                    (println "ERROR: " e)
                    (is (nil? e))))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :kattohinnan-ylitys)
        ;; Hae päätöksen tiedot tietokannasta, jotta nekin voidaan tarkistaa
        paatos-tiedot (first (q-map (format "SELECT * FROM paatos_kattohinta WHERE urakkaid = %s AND hoitokauden_alkuvuosi = %s" urakkaid hoitokauden-alkuvuosi)))]

    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinta täsmää tallennuksen jälkeen")
    (is (= kattohinta (:kattohinta paatos-tiedot)) "Kattohinta täsmää tallennuksen jälkeen")
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset tallennettu-paatos)) "Toteutuneet kustannukset täsmää tallennuksen jälkeen")
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos-tiedot)) "Toteutuneet kustannukset täsmää tallennuksen jälkeen")
    (is (= siirrettava-maara (:siirrettava_maara tallennettu-paatos)) "Siirrettävä määrä täsmää tallennuksen jälkeen")
    (is (= siirrettava-maara (:siirrettava_maara paatos-tiedot)) "Siirrettävä määrä täsmää tallennuksen jälkeen")
    (is (= ylityksen-maara (:ylityksen_maara tallennettu-paatos)) "Siirrettävä määrä täsmää tallennuksen jälkeen")
    (is (= ylityksen-maara (:ylityksen_maara paatos-tiedot)) "Siirrettävä määrä täsmää tallennuksen jälkeen")))

(deftest rajapinta-kattohinnan-ylitys-epaonnistuu-viimeisena-vuotena-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2025
        kattohinta 5M
        toteutuneet-kustannukset 6M
        ylityksen-maara 1M
        urakoitsija-maksaa 0M
        siirrettava-maara 1M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id nil
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id true maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                kattohinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))]
    ;; Koska virheitä, tarkista virhe
    (is (str/includes? vastaus "Viimeisenä hoitovuodena ei voida siirtää kuluja seuraavalle vuodelle. Poista siirron osuus."))))

(deftest rajapinta-kattohinnan-ylitys-lisays-onnistuu-2025-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 50M
        urakoitsija-maksaa (/ 50M 2)
        siirrettava-maara (/ 50M 2)
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id nil
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite
                                (fn [db hakuparametrit] [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                          :hoitovuoden-lopun-tavoitehinta (* 0.9 kattohinta)
                                                          :hoitovuoden-lopun-kattohinta kattohinta}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))

        ;; Haetaan päätös suoraan tietokannasta - koska kattohinnan ylitykselle ei ehdot täyty. Tämä johtuu siitä, että
        ;; Kittilän urakalta puuttuu toteumia ja suunnitelmia
        db-paatos (first (paatos-kyselyt/hae-kattohinta-paatokset (:db jarjestelma) {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi :urakkaid urakkaid}))]

    (is (= 0.03M (:siirtorajoitus_prosentti db-paatos)))
    (is (= false (:viimeinen_hoitokausi db-paatos)))
    (is (= urakoitsija-maksaa (:urakoitsija_maksaa db-paatos)))
    (is (= kattohinta (:kattohinta db-paatos)))
    (is (= "Kattohinnan ylitys" (:nimi db-paatos)))
    (is (= hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi db-paatos)))))

;; Varmista, että viimeisenä vuotena ei voida siirtää kattohinnan ylitystä
(deftest kattohinnan-ylitys-siirto-viimeisena-vuotena
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2029
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id nil
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara siirtorajoitus-prosentti kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaus kattohinnan hakemiseen, koska urakalla ei ole välttämättä kattohintaa tallennettuna
                                valikatselmus-kyselyt/hae-oikaistu-kattohinta (fn [db hakuparametrit]
                                                                                kattohinta)]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-kattohinnan-ylityspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e))]
    (is (str/includes? vastaus  "Viimeisenä hoitovuodena ei voida siirtää kuluja seuraavalle vuodelle."))))

;; Kattohinnan ylitys - Poisto
(deftest kysely-kattohinnan-ylityspaatoksen-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id 1
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara
                 siirtorajoitus-prosentti kayttajaid)
        _ (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) paatos)

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
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirrettava-maara 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara           ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        kulu-id 1
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id false maksimi-siirrettava-maara
                 siirtorajoitus-prosentti kayttajaid)
        tallennettu-paatos (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) paatos)
        poistovastaus (kutsu-palvelua (:http-palvelin jarjestelma) :poista-kattohinnan-ylityspaatos +kayttaja-jvh+ tallennettu-paatos)
        poistettu-paatos (valitse-paatos (:paatokset poistovastaus) :kattohinnan-ylitys)]

    (is (= kattohinta (:kattohinta tallennettu-paatos)) "Kattohinnan muutospäätöslukemat täsmää validoinnin jälkeen")
    ;; Koska ehdot eivät täyty niin poiston jälkeen välikatselmussivun palaute ei palauta kattohinnan ylityspäätöstä
    (is (nil? poistettu-paatos))))

;; Hoitokaudenlopun indeksikorjauksen lisäys
(deftest kysely-hoitovuoden-indeksikorjaus-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_alun_indkorj_tavoitehinta 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M ;
        tavoitehinnan-muutokset 30000M
        hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset)
        hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                               {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                               {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                               {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                               {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                               {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                               {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                               {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                               {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                               {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                               {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                               {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
        kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
        alkuperainen-pisteluku 112.5
        alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
        pistelukujen-muutos 5.9
        pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
        indeksikorotuksen-prosenttiosuus 3.9
        paatos (paatos-apurit/indeksikorjauspaatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                 hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                 pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        vastaus (paatos-kyselyt/tee-indeksikorjauspaatos (:db jarjestelma) paatos)
        testattavat-indeksikuukaudet (reduce (fn [uusi-vectori kuukausi]
                                               (conj uusi-vectori [(:kuukausi kuukausi) (:indeksiluku kuukausi)]))
                                       [] hoitokauden-kuukaudet)]
    (testaa-indeksikorjauspaatos vastaus urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
      testattavat-indeksikuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
      pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)))


;; Indeksikorjauksen poisto
(deftest kysely-hoitovuoden-indeksikorjaus-poisto-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_alun_indkorj_tavoitehinta 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M ;
        tavoitehinnan-muutokset 30000M
        hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset)
        hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                               {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                               {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                               {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                               {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                               {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                               {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                               {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                               {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                               {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                               {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                               {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
        kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
        alkuperainen-pisteluku 112.5
        alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
        pistelukujen-muutos 5.9
        pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
        indeksikorotuksen-prosenttiosuus 3.9
        paatos (paatos-apurit/indeksikorjauspaatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                 hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                 pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        vastaus (paatos-kyselyt/tee-indeksikorjauspaatos (:db jarjestelma) paatos)
        testattavat-indeksikuukaudet (reduce (fn [uusi-vectori kuukausi]
                                               (conj uusi-vectori [(:kuukausi kuukausi) (:indeksiluku kuukausi)]))
                                       [] hoitokauden-kuukaudet)
        _ (testaa-indeksikorjauspaatos vastaus urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
            testattavat-indeksikuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
            pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        ;; Määrittele haettavat päätökset - Luetaan vain indeksipäätös, kun se on ainoa, mikä tässä testissä on luotu
        paatokset [{:nimi "Hoitovuoden lopun indeksikorjaus" :jarjestys 6}]
        paatos-ennen-poistoa (first (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi))

        ;; Poistetaan päätös
        _ (paatos-kyselyt/poista-indeksikorjauspaatos (:db jarjestelma) urakkaid kayttajaid (:id paatos-ennen-poistoa))
        ;; Tarkista, että päätös on poistettu
        v (paatos-kyselyt/hae-paatokset (:db jarjestelma) paatokset urakkaid hoitokauden-alkuvuosi)]
    (is (nil? (first v)))))

(deftest rajapinta-hoitovuoden-indeksikorjaus-2024-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_alun_indkorj_tavoitehinta 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M ;
        tavoitehinnan-muutokset 30000M
        hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset)
        hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                               {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                               {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                               {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                               {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                               {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                               {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                               {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                               {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                               {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                               {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                               {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
        kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
        alkuperainen-pisteluku 112.5
        alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
        pistelukujen-muutos 5.9
        pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
        indeksikorotuksen-prosenttiosuus 3.9
        paatos (paatos-apurit/indeksikorjauspaatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                 hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                 pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        kovakoodattu-budjettitavoite [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                       :tavoitehinta-oikaistu hv_lopun_tavoitehinta_ennen_indkorj}]
        vastaus (try
                  (with-redefs [valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hv_alun_indkorj_tavoitehinta)
                                ;; Budjettitavoitteesta ei tarvi kuin pari hassua juttua
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite (fn [db hakuparametrit] kovakoodattu-budjettitavoite)
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-indeksikorjauspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e
                    (println "Virhe tapahtui: " (.getMessage e)) e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :hoitovuoden-lopun-indeksikorjaus)]

    (is (= (bigdec alkuperainen-pisteluku) (:alkuperainen_pisteluku tallennettu-paatos)) "Alkuperainen pisteluku täsmää.")
    (is (= (bigdec indeksikorotuksen-prosenttiosuus) (:indeksikorotuksen_prosenttiosuus tallennettu-paatos)) "Indeksikorotuksen prosenttiosuus täsmää.")))

;; 2025 alkaville urakoille on esitelty muutokset, jotka muuttaa hoitovuoden lopun tavoitehintaa
(deftest rajapinta-hoitovuoden-indeksikorjaus-2025-lisays-onnistuu-test
  (let [;; Hae vaativa mhu urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_alun_indkorj_tavoitehinta 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M ;
        muutokset 30000M
        hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta muutokset)
        hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                               {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                               {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                               {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                               {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                               {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                               {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                               {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                               {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                               {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                               {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                               {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
        kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
        alkuperainen-pisteluku 112.5
        alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
        pistelukujen-muutos 5.9
        pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
        indeksikorotuksen-prosenttiosuus 3.9
        paatos (paatos-apurit/indeksikorjauspaatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta muutokset hv_lopun_tavoitehinta_ennen_indkorj
                 hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                 pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        kovakoodattu-budjettitavoite [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                       :tavoitehinta-oikaistu (- hv_lopun_tavoitehinta_ennen_indkorj muutokset) ;; 2025 ja sen jälkeen urakoilla tavoitehinna muutokset (ennen oikaisut) eivät tule enää budjettitavoitteen mukana
                                       :kirjallisesti-sovitut-muutokset muutokset
                                       :hoitovuoden-lopun-kattohinta (+ hv_lopun_tavoitehinta_ennen_indkorj 100000M)}]
        vastaus (try
                  (with-redefs [;; valikatselmus-kyselyt/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset))
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hv_alun_indkorj_tavoitehinta)
                                ;; Budjettitavoitteesta ei tarvi kuin pari hassua juttua
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite (fn [db hakuparametrit] kovakoodattu-budjettitavoite)
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-indeksikorjauspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e e
                    (println "Virhe tapahtui: " (.getMessage e)) e))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :hoitovuoden-lopun-indeksikorjaus)]

    (is (= (bigdec alkuperainen-pisteluku) (:alkuperainen_pisteluku tallennettu-paatos)) "Alkuperainen pisteluku täsmää.")
    (is (= (bigdec indeksikorotuksen-prosenttiosuus) (:indeksikorotuksen_prosenttiosuus tallennettu-paatos)) "Indeksikorotuksen prosenttiosuus täsmää.")))

(deftest rajapinta-hoitovuoden-indeksikorjaus-2024-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_alun_indkorj_tavoitehinta 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M ;
        tavoitehinnan-muutokset 30000M
        hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset)
        hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                               {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                               {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                               {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                               {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                               {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                               {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                               {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                               {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                               {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                               {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                               {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
        kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
        alkuperainen-pisteluku 112.5
        alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
        pistelukujen-muutos 5.9
        pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
        indeksikorotuksen-prosenttiosuus 3.9
        paatos (paatos-apurit/indeksikorjauspaatos urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                 hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                 pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus kayttajaid)

        kovakoodattu-budjettitavoite [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                       :tavoitehinta-oikaistu hv_lopun_tavoitehinta_ennen_indkorj}]
        vastaus (try
                  (with-redefs [;; Budjettitavoitteesta ei tarvi kuin pari hassua juttua
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite (fn [db hakuparametrit] kovakoodattu-budjettitavoite)
                                valikatselmus-kyselyt/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hv_alun_indkorj_tavoitehinta)
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-indeksikorjauspaatos +kayttaja-jvh+ paatos))
                  (catch Exception e
                    (println "ERROR: " e)))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :hoitovuoden-lopun-indeksikorjaus)
        poistovastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                    jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                        (kutsu-palvelua (:http-palvelin jarjestelma) :poista-indeksikorjauspaatos +kayttaja-jvh+ tallennettu-paatos))
        poistettu-paatos (valitse-paatos (:paatokset poistovastaus) :hoitovuoden-lopun-indeksikorjaus)]

    ;; Päätös on poistettu, joten sitä ei enää löydy
    (is (= "Hoitovuoden lopun indeksikorjaus" (:nimi poistettu-paatos)))
    (is (not (nil? (:virheet poistettu-paatos))))))

;; Hoitokauden lopun hinnat - lisäys
(deftest kysely-hoitovuoden-lopun-hintapaatos-lisays-onnistuu-2024-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M
        tavoitehinnan_muutokset 40000M
        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        vastaus (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) paatos)]
    (testaa-lopun-hintapaatos vastaus urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
      tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)
    ;; -24 alkavissa urakoissa kattohintakerroin on 1.1
    (is (= 1.1M (:kattohintakerroin vastaus)))))

(deftest kysely-hoitovuoden-lopun-hintapaatos-lisays-onnistuu-2025-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M
        tavoitehinnan_muutokset 40000M
        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        vastaus (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) paatos)]
    (testaa-lopun-hintapaatos vastaus urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
      tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)
    ;; -25 alkavissa urakoissa kattohintakerroin on 1.2
    (is (= 1.20M (:kattohintakerroin vastaus)))))

(deftest kysely-hoitovuoden-lopun-hintapaatos-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M
        tavoitehinnan_muutokset 40000M
        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        vastaus (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) paatos)
        _ (testaa-lopun-hintapaatos vastaus urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
            tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        ;; Poistetaan juuri lisätty päätös.
        poistovastaus (paatos-kyselyt/poista-hoitovuoden-lopun-hintapaatos (:db jarjestelma) urakkaid kayttajaid (:id vastaus))]
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

(deftest rajapinta-hoitovuoden-lopun-hintapaatos-lisays-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M
        tavoitehinnan_muutokset 40000M
        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        vastaus (try
                  (with-redefs [;; Feikataan vastaukset
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite
                                (fn [db hakuparametrit] [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                          :tavoitehinta-oikaistu tavoitehinta_jalkeen
                                                          :hoitovuoden-lopun-kattohinta kattohinta}])
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-hv-lopun-tavoite-ja-kattohintapaatos +kayttaja-jvh+ paatos))
                  (catch Exception e (println "ERROR:" e)))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :hoitovuoden-lopun-tavoite-ja-kattohinta)]
    (testaa-lopun-hintapaatos tallennettu-paatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
      tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)))

(deftest rajapinta-hoitovuoden-lopun-hintapaatos-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta_ennen 2000000M
        hoitokauden-lopun-indeksikorjaus 40000M
        tavoitehinnan_muutokset 40000M
        tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
        kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
        paatos (paatos-apurit/lopun-hintapaatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                 tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        uusi-paatos (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) paatos)
        _ (testaa-lopun-hintapaatos uusi-paatos urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
            tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid)

        ;; Poistetaan juuri lisätty päätös rajapinnan kautta
        vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                              jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                 (kutsu-palvelua (:http-palvelin jarjestelma) :poista-hoitovuoden-lopun-hintapaatos +kayttaja-jvh+ uusi-paatos))
        poistettu-paatos (valitse-paatos (:paatokset vastaus) :hoitovuoden-lopun-tavoite-ja-kattohinta)]
    ;; Päätös on poistettu, joten sitä ei enää löydy
    (is (= "Hoitovuoden lopun tavoite- ja kattohinta" (:nimi poistettu-paatos)))
    (is (not (nil? (:virheet poistettu-paatos))))))

(deftest kysely-hoidonjohtopalkkion-muutospaatos-lisays-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_lopun_indkorjaamaton_tavoitehinta 2100000M    ;; Hoitovuoden lopun tavoihinta ilman indeksikorjausta
        tarjouksen_tavoitehinta 2000000M
        muutosprosentti (* (- (/ hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta) 1) 100)
        hoidonjohtopalkkio 40000M
        hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)
        kulu_id 1

        paatos (paatos-apurit/hoidojohtopalkkiomuutospaatos urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
                 muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id kayttajaid)

        vastaus (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) paatos)]
    (testaa-hoidojohtopalkkiomuutospaatos vastaus urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
      muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kayttajaid)))


(deftest kysely-hoidonjohtopalkkion-muutospaatos-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_lopun_indkorjaamaton_tavoitehinta 2100000M
        tarjouksen_tavoitehinta 2000000M
        muutosprosentti (* (- (/ hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta) 1) 100)
        hoidonjohtopalkkio 40000M
        hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)
        kulu_id 1

        paatos (paatos-apurit/hoidojohtopalkkiomuutospaatos urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
                 muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id kayttajaid)

        vastaus (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) paatos)
        _ (testaa-hoidojohtopalkkiomuutospaatos vastaus urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
            muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kayttajaid)

        ;; Poistetaan päätös
        poistovastaus (paatos-kyselyt/poista-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) urakkaid kayttajaid (:id vastaus))]
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

(deftest rajapinta-hoidonjohtopalkkion-muutospaatos-lisays-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        hv_lopun_indkorjaamaton_tavoitehinta 2100000M
        tarjouksen_tavoitehinta 2000000M
        muutosprosentti (* (- (/ hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta) 1) 100)
        hoidonjohtopalkkio 40000M
        hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)
        kulu_id 1

        paatos (paatos-apurit/hoidojohtopalkkiomuutospaatos urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
                 muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id kayttajaid)

        kovakoodattu-budjettitavoite [{:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                       :tavoitehinta-oikaistu hv_lopun_indkorjaamaton_tavoitehinta}]
        vastaus (try
                  (with-redefs [;; Feikataan vastaukset
                                ;; Budjettitavoitteesta ei tarvi kuin pari hassua juttua
                                budjettisuunnittelu-kyselyt/hae-budjettitavoite (fn [db hakuparametrit] kovakoodattu-budjettitavoite)
                                lupaus-palvelu/maarita-urakan-tavoitehinta (fn [db urakkaid hoitokauden-alkuvuosi] tarjouksen_tavoitehinta)
                                paatos-kyselyt/hae-budjetoitu-hoidonjohtopalkkio-hoitokaudelle (fn [db hakuparametrit] [{:budjetoitu_summa_indeksikorjattu hoidonjohtopalkkio}])
                                ;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                    (kutsu-palvelua (:http-palvelin jarjestelma) :tee-hoidonjohtopalkkion-muutospaatos +kayttaja-jvh+ paatos))
                  (catch Exception e
                    (println "ERROR: e" e)))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :hoidonjohtopalkkion-muutos)]
    (testaa-hoidojohtopalkkiomuutospaatos tallennettu-paatos urakkaid hoitokauden-alkuvuosi hv_lopun_indkorjaamaton_tavoitehinta tarjouksen_tavoitehinta
      muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kayttajaid)))

(deftest rajapinta-hoidonjohtopalkkion-muutospaatos-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 2100000M
        tarjouksen_tavoitehinta 2000000M
        muutosprosentti (* (- (/ tavoitehinta tarjouksen_tavoitehinta) 1) 100)
        hoidonjohtopalkkio 40000M
        hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)
        kulu_id 1

        paatos (paatos-apurit/hoidojohtopalkkiomuutospaatos urakkaid hoitokauden-alkuvuosi tavoitehinta tarjouksen_tavoitehinta
                 muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id kayttajaid)
        ;; Lisätään päätös suoralla kyselyllä
        uusi-paatos (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) paatos)

        ;; Hae päätökset ja varmista, että juuri lisätty päätös löytyy
        valikatselmus-vastaus (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) +kayttaja-jvh+
                                {:urakkaid urakkaid :hoitovuosi hoitokauden-alkuvuosi})
        uusi-paatos (valitse-paatos (:paatokset valikatselmus-vastaus) :hoidonjohtopalkkion-muutos)
        _ (is (not (nil? (:id uusi-paatos))))

        ;; Poistetaan päätös rajapinnan kautta
        poisto-vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                     jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                        (kutsu-palvelua (:http-palvelin jarjestelma) :poista-hoidonjohtopalkkion-muutospaatos +kayttaja-jvh+ uusi-paatos))
        poistettu-paatos (valitse-paatos (:paatokset poisto-vastaus) :hoidonjohtopalkkion-muutos)]
    ;; Päätös on poistettu, joten sellaista päätöstä, jossa on id, ei enää löydy
    (is (= "Hoidonjohtopalkkion muutos" (:nimi poistettu-paatos)))
    (is (nil? (:id poistettu-paatos)))))

(deftest kysely-poytakirjan-raportin-lisays-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tarkistettu (pvm/nyt)
        paatos (poytakirjan-raporttipaatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)
        vastaus (paatos-kyselyt/tee-poytakirjan-raporttipaatos (:db jarjestelma) paatos)]
    (testaa-poytakirjan-raporttipaatos vastaus urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)))

(deftest kysely-poytakirjan-raportin-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tarkistettu (pvm/nyt)
        paatos (poytakirjan-raporttipaatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)
        uusi-paatos (paatos-kyselyt/tee-poytakirjan-raporttipaatos (:db jarjestelma) paatos)
        _ (testaa-poytakirjan-raporttipaatos uusi-paatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)
        ;; Poistetaan päätös
        poistovastaus (paatos-kyselyt/poista-poytakirjan-raporttipaatos (:db jarjestelma) urakkaid kayttajaid (:id uusi-paatos))]
    (is (= true (:poistettu poistovastaus)))
    (is (= kayttajaid (:poistaja poistovastaus)))))

(deftest rajapinta-poytakirjan-raportin-lisays-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tarkistettu (pvm/nyt)
        paatos (poytakirjan-raporttipaatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)
        vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                              jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tee-poytakirjan-raporttipaatos +kayttaja-jvh+ paatos))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :valikatselmuspoytakirjaan-liitettavat-raportit)]
    (testaa-poytakirjan-raporttipaatos tallennettu-paatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)))

(deftest rajapinta-poytakirjan-raportin-poisto-onnistuu-test
  (let [;; Hae -24 alkava urakka
        urakkaid (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tarkistettu (pvm/nyt)

        paatos (poytakirjan-raporttipaatos urakkaid hoitokauden-alkuvuosi tarkistettu kayttajaid)
        vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                              jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                 (kutsu-palvelua (:http-palvelin jarjestelma) :tee-poytakirjan-raporttipaatos +kayttaja-jvh+ paatos))
        tallennettu-paatos (valitse-paatos (:paatokset vastaus) :valikatselmuspoytakirjaan-liitettavat-raportit)

        ;; Poistetaan tallennettu päätös
        poisto-vastaus (with-redefs [;; Validointi on kinkkistä, joten otetaan osa validoinneista pois käytöstä
                                     jarjestelma-kyselyt/hae-jarjestelman-asetukset (fn [db] [{:valikatselmus_validoinnit_kaytossa false}])]
                        (kutsu-palvelua (:http-palvelin jarjestelma) :poista-poytakirjan-raporttipaatos +kayttaja-jvh+ tallennettu-paatos))
        poistettu-paatos (valitse-paatos (:paatokset poisto-vastaus) :valikatselmuspoytakirjaan-liitettavat-raportit)]
    ;; Poiston jälkeen löytyy vain default tiedot päätöksestä
    (is (= "Välikatselmuspöytäkirjaan liitettävät raportit" (:nimi poistettu-paatos)))))
