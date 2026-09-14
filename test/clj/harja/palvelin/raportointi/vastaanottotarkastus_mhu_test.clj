(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]

            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara :as talvisuola]
            [harja.palvelin.raportointi.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-urakan-lupaustiedot (component/using
                                     (lupaus-palvelu/->Lupaus {:kehitysmoodi true})
                                     [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

;; Meillä on kaksi vastaantottotarkastusraporttia, joista toinen on päällystysurakoille ja toinen MHU-urakoille.
;; Testataan, että ne ovat rekisteröityinä eri urakkatyyppien alle ja että ne eroavat toisistaan.
(deftest vastaanottotarkastusraportit-ovat-erilliset
  (let [paallystys (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti)
        mhu (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti-mhu)]
    (testing "molemmat raportit ovat rekisteröityinä"
      (is (some? paallystys))
      (is (some? mhu)))
    (testing "raportit on kohdistettu eri urakkatyypeille"
      (is (= #{:paallystys} (:urakkatyyppi paallystys)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu))))
    (testing "MHU-raportti tarvitsee yhden urakan"
      (is (= #{"urakka"} (:konteksti mhu)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu)))
      (is (= "Vastaanottotarkastusraportti - MHU" (:kuvaus mhu))))
    (testing "raporteilla on eri tunnisteet ja toteutukset"
      (is (not= (:nimi paallystys) (:nimi mhu)))
      (is (not= (:suorita paallystys) (:suorita mhu))))))

(def ^:private testi-hoitokaudet
  [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
   {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}])

(def ^:private testi-talvisuolan-erittely
  [:taulukko {:otsikko "Erittely hoitovuosittain"} [] []])

(defn- muodosta-testiraportti []
  (let [lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:pisteet {:toteuma 75}}}))
        talvisuolan-erittely testi-talvisuolan-erittely]
    (with-redefs [urakat-q/hae-urakka (fn [_ _] [{:nimi "Testiurakka"
                                                  :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                                                  :loppupvm #inst "2023-12-31T23:59:59.000-00:00"}])
                  urakat-q/hae-urakan-hoitokaudet (fn [_ _] testi-hoitokaudet)
                  talvisuola/suorita (fn [_ _ _]
                                       [:raportti {}
                                        [:taulukko {:otsikko "Koko urakka-ajan yhteenveto (kuivatonneina)"}
                                         []
                                         [["Suurin urakassa sallittu käyttömäärä + 5 %"
                                           [:arvo {:arvo 1050M}]]]]
                                        talvisuolan-erittely])
                  materiaalit-kyselyt/hae-talvisuolan-kokonaismaara
                  (fn [_ _] [{:kokonaismaara 1000M}])
                  lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle lupaustiedot
                  valikatselmus-q/hae-bonukset (fn [_ {:keys [alkupvm]}]
                                                 (if (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:rahasumma 100M}]
                                                   [{:rahasumma 200M}]))
                  valikatselmus-q/hae-sanktiot (fn [_ {:keys [alkupvm]}]
                                                 (cond
                                                   (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:maara -25M}]
                                                   (= alkupvm #inst "2021-01-01T00:00:00.000-00:00")
                                                   [{:sakkoryhma :talvisuolan_ylitys :maara 100M}]
                                                   :else
                                                   [{:maara -50M}]))
                  rahavaraus-kyselyt/hae-urakan-rahavaraukset
                  (fn [_ _]
                    [{:id 1 :nimi "Äkilliset hoitotyöt"}
                     {:id 2 :nimi "Vahinkojen korjaukset"}
                     {:id 3 :nimi "Tilaajan rahavaraus kannustinjärjestelmään"}])
                  rahavaraus-kyselyt/muutosten-rahavaraukset
                  (fn [_ _ hoitokauden-alkuvuosi]
                    (if (= hoitokauden-alkuvuosi 2021)
                      [{:id 1 :summa-indeksikorjattu 100M :toteumat 80M :tavoitehinnan-muutos -20M}
                       {:id 2 :summa-indeksikorjattu 50M :toteumat 40M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 25M :toteumat 20M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 175M :toteumat 140M :tavoitehinnan-muutos -35M}]
                      [{:id 1 :summa-indeksikorjattu 200M :toteumat 150M :tavoitehinnan-muutos -50M}
                       {:id 2 :summa-indeksikorjattu 100M :toteumat 90M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 50M :toteumat 45M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 350M :toteumat 285M :tavoitehinnan-muutos -65M}]))]
      (vastaanottotarkastus-mhu/suorita nil nil {:urakka-id 1}))))

(deftest raportti-sisaltaa-lupaukset-hoitovuosittain
  (let [raportti (muodosta-testiraportti)]
    (is (= [:taulukko
            {:otsikko "Lupaukset" :sheet-nimi "Lupaukset" :tyhja nil}
            [{:otsikko "Hoitovuosi" :leveys 5}
             {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
             {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
             {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
            [["2021-2022" 70 65 75M]
             ["2022-2023" 80 75 150M]]]
          (nth raportti 2)))))

(deftest raportti-sisaltaa-talvisuolan-kokonaiskayttomaaran
  (let [raportti (muodosta-testiraportti)]
    (is (= [:otsikko "Talvisuolan kokonaiskäyttömäärä"]
          (nth raportti 3)))
    (is (= ["Kohtuullistettu käyttöraja + 5% (tonnia)" 1050M]
          (let [rivi (nth (nth (nth raportti 4) 3) 0)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Toteuma (tonnia)" 1000M]
          (let [rivi (nth (nth (nth raportti 4) 3) 1)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Erotus (tonnia)" -50M]
          (let [rivi (nth (nth (nth raportti 4) 3) 2)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Kirjattu sakon määrä (euroa)" 100M]
          (let [rivi (nth (nth (nth raportti 4) 3) 3)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= 50
          (get-in raportti [4 1 :leveysprosentti])))
    (is (= false
          (get-in raportti [4 1 :viimeinen-rivi-yhteenveto?])))
    (is (= testi-talvisuolan-erittely
          (nth raportti 5)))
    (is (not-any? #(and (vector? %)
                     (= "Ympäristöraportti" (get-in % [1 :otsikko])))
          raportti))))

(deftest raportti-sisaltaa-rahavarausten-tavoitehinnan-muutokset
  (let [raportti (muodosta-testiraportti)]
    (is (= [:taulukko
            {:otsikko "Rahavarausten tavoitehintamuutokset"
             :sheet-nimi "Rahavarausten tavoitehintamuutokset"
             :tyhja nil
             :rivi-ennen [{:sarakkeita 1}
                          {:teksti "Äkilliset hoitotyöt"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:teksti "Vahinkojen korjaukset"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:teksti "Tilaajan rahavaraus kannustinjärjestelmään"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:sarakkeita 1}]}
            [{:otsikko "Hoitokausi" :leveys 5}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Tavoitehinnan muutos (€)" :leveys 5 :fmt :raha}]
            [["2021-2022" 100M 80M 50M 40M 25M 20M -35M]
             ["2022-2023" 200M 150M 100M 90M 50M 45M -65M]]]
          (nth raportti 7)))
    (is (not-any? #(and (vector? %)
                     (= "Ympäristöraportti" (get-in % [1 :otsikko])))
          raportti))))

(deftest MHU21-urakan-tavoitehinnan-oikaisut-muodostuvat-hoitovuosittain
  (let [tv-otsikko-1 "Testioikaisu 2091"
        tv-otsikko-2 "Testioikaisu 2092"
        tv-selite-1 "Ensimmäisen hoitovuoden oikaisu"
        tv-selite-2 "Toisen hoitovuoden oikaisu"
        tv-summa-1 1000
        tv-summa-2 -250
        hoitokausi-1 2091
        hoitokausi-2 2092
        urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        hoitokaudet [{:alkupvm (pvm/luo-pvm-aika hoitokausi-1 9 1 0)
                      :loppupvm (pvm/luo-pvm-aika (inc hoitokausi-1) 8 30 23 59 59)}
                     {:alkupvm (pvm/luo-pvm-aika hoitokausi-2 9 1 0)
                      :loppupvm (pvm/luo-pvm-aika (inc hoitokausi-2) 8 30 23 59 59)}]
        siivoa-testioikaisut! #(u (str "DELETE FROM tavoitehinnan_oikaisu
                                        WHERE \"urakka-id\" = " urakka-id "
                                          AND otsikko IN ('" tv-otsikko-1 "', '" tv-otsikko-2 "')"))]
    (try
      (siivoa-testioikaisut!)
      (u (str "INSERT INTO tavoitehinnan_oikaisu
               (\"urakka-id\", \"muokkaaja-id\", muokattu, otsikko, selite, summa,
                \"hoitokauden-alkuvuosi\", poistettu)
               VALUES (" urakka-id ", " (:id +kayttaja-jvh+) ", NOW(),
                       '" tv-otsikko-1 "', '" tv-selite-1 "', " tv-summa-1 ",
                       " hoitokausi-1 ", false),
                      (" urakka-id ", " (:id +kayttaja-jvh+) ", NOW(),
                       '" tv-otsikko-2 "', '" tv-selite-2 "', " tv-summa-2 ",
                       " hoitokausi-2 ", false)"))
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-tavoitehinnan-oikaisut
                            (:db jarjestelma) urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (is (= [(str hoitokausi-1 "-" hoitokausi-2) (bigdec tv-summa-1)]
              (first rivit)))
        (is (= [(str hoitokausi-2 "-" (inc hoitokausi-2)) (bigdec tv-summa-2)]
              (second rivit)))
        (is (= ["Yhteensä" (bigdec (+ tv-summa-1 tv-summa-2))]
              (get-in (last rivit) [:rivi])))
        (is (= "Harjaan kirjatut tavoitehinnan muutokset"
              (get-in taulukko [1 :sheet-nimi]))))
      (finally
        (siivoa-testioikaisut!)))))

(deftest lupaukset-kayttaa-kuukausittaisia-pisteita-toimii
  (let [urakka-id-raasepori (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        urakan-tiedot (first (urakat-q/hae-urakka (:db jarjestelma) {:id urakka-id-raasepori}))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet (:db jarjestelma) urakka-id-raasepori))
        raportti (with-redefs [lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle
                               (fn [_ _] {:lupaus-sitoutuminen {:pisteet 70}
                                          :yhteenveto {:pisteet {:maksimi 100, :ennuste 100, :toteuma 100}}})
                               valikatselmus-q/hae-bonukset (fn [_ _] [{:rahasumma 100M}])
                               valikatselmus-q/hae-sanktiot (fn [_ _] [{:maara -25M}])]
                   (vastaanottotarkastus-mhu/lupaukset-taulukko (:db jarjestelma) urakka-id-raasepori urakan-tiedot hoitokaudet))]
    (is (= ["2021-2022" 70 100 75M]
          (first (last raportti))))))


