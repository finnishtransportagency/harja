(ns harja.palvelin.palvelut.varuste-ulkoiset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.varuste-ulkoiset :as varuste-ulkoiset]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.testi :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :excel-vienti (component/using
                                        (excel-vienti/luo-excel-vienti)
                                        [:http-palvelin])
                        :varuste-velho (component/using
                                         (varuste-ulkoiset/->VarusteVelho)
                                         [:http-palvelin :db :excel-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(def urakka-id-35 35)
(def urakka-id-33 33)

(deftest palvelu-on-olemassa-ja-vaatii-parametrin-urakka-id
  (is (thrown-with-msg? IllegalArgumentException #"urakka-id on pakollinen"
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-varustetoteuma-ulkoiset
                                        +kayttaja-jvh+
                                        {:urakka-id nil :hoitokauden-alkuvuosi 2019}))))

(deftest palvelu-on-olemassa-ja-vaatii-parametrin-hoitovuosi
  (is (thrown-with-msg? IllegalArgumentException #"hoitokauden-alkuvuosi on pakollinen"
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-varustetoteuma-ulkoiset
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi nil}))))

(defn assertoi-saatu-lista
  [odotettu-lista parametrit hae-fn]
  (let [odotettu-keys (keys (first odotettu-lista))
        vertaa (fn [x] ((apply juxt odotettu-keys) x))
        lajittele (fn [l] (sort-by vertaa l))
        odotettu-oid-lista (lajittele odotettu-lista)
        saatu-oid-lista (->> (hae-fn (:db jarjestelma) +kayttaja-jvh+ parametrit)
                             :toteumat
                             (map #(select-keys % odotettu-keys))
                             vec
                             lajittele)]
    (is (= odotettu-oid-lista saatu-oid-lista))))

(defn assertoi-saatu-oid-lista [odotettu-oid-lista parametrit]
  (let [odotettu-lista (map (fn [x] {:ulkoinen-oid x}) odotettu-oid-lista)]
    (assertoi-saatu-lista odotettu-lista parametrit varuste-ulkoiset/hae-urakan-uusimmat-varustetoteuma-ulkoiset)))

(deftest palvelu-palauttaa-oikean-hoitovuoden-tuloksia
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173999"
                             "1.2.246.578.4.3.12.512.310174000"]
                            {:urakka-id urakka-id-33 :hoitokauden-alkuvuosi 2022}))

(deftest tyhjalle-hoitovuodelle-palautuu-tyhja-tulos
  "Ja rinnakkaisien hoitovuosien alku & loppu ovat 1.10. ja 30.9. Ne eivät tule mukaan tulokseen."
  (assertoi-saatu-oid-lista [] {:urakka-id urakka-id-33 :hoitokauden-alkuvuosi 2023}))

(deftest hae-urakan-35-uusimmat-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173990"
                             "1.2.246.578.4.3.12.512.310173991"
                             "1.2.246.578.4.3.12.512.310173992"
                             "1.2.246.578.4.3.12.512.310173993"
                             "1.2.246.578.4.3.12.512.310173994"
                             "1.2.246.578.4.3.12.512.310173995"
                             "1.2.246.578.4.3.12.512.310173996"
                             "1.2.246.578.4.3.12.512.310173997"
                             "1.2.246.578.4.3.12.512.310173998"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019}))

(deftest hae-vain-urakan-erittain-hyvat-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173990"
                             "1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019 :kuntoluokat ["Erittäin hyvä"]}))

(deftest hae-vain-urakan-erittain-hyvat-ja-hyvat-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173995"
                             "1.2.246.578.4.3.12.512.310173990"
                             "1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019 :kuntoluokat ["Erittäin hyvä" "Hyvä"] }))

(deftest hae-vain-urakan-hyvat-paivitetyt-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173998"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2020 :kuntoluokat ["Hyvä"] :toteuma "paivitetty"}))

(deftest hae-vain-urakan-paivitetyt-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173994"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019 :toteuma "paivitetty"}))

(deftest hae-vain-urakan-tietolaji512-varusteet
  (assertoi-saatu-oid-lista ["1.2.246.578.4.3.12.512.310173997"]
                            {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019 :tietolajit ["tl506"]}))

(deftest palauta-uusin-versio-varusteesta-josta-loytyy-monta-versiota
  (assertoi-saatu-lista [{:ulkoinen-oid "1.2.246.578.4.3.12.512.310173998" :alkupvm #inst "2020-10-24T21:00:00.000-00:00"}]
                        {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2020} varuste-ulkoiset/hae-urakan-uusimmat-varustetoteuma-ulkoiset))

(deftest kuukausi-rajaus
  (assertoi-saatu-lista [{:id 9} {:id 10} {:id 11}]
                        {:urakka-id urakka-id-35 :ulkoinen-oid "1.2.246.578.4.3.12.512.310173998"}
                        varuste-ulkoiset/hae-varustetoteumat-ulkoiset))

(deftest tr-osoite-suodatin
  (is (varuste-ulkoiset/kelvollinen-tr-filter 1 1 1 1 1))   ; kaikki kentat annettu => OK
  (is (varuste-ulkoiset/kelvollinen-tr-filter 1 1 1 nil nil)) ; vain aosa annettu => OK
  (is (varuste-ulkoiset/kelvollinen-tr-filter 1 nil nil nil nil)) ; vain tie annettu => OK
  (is (varuste-ulkoiset/kelvollinen-tr-filter nil nil nil nil nil)) ; ei TR osoitetta annettu => OK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter 1 1 1 1 nil))) ; leta puuttuu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter 1 1 1 nil 1))) ; losa puuttuu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter 1 1 nil 1 1))) ; aeta puuttuu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter 1 nil 1 1 1))) ; aosa puuttuu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter nil 1 1 1 1))) ; tie puuttuu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter nil nil nil nil 1))) ; vain leta annettu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter nil nil nil 1 nil))) ; vain aeta annettu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter nil nil 1 nil nil))) ; vain aeta annettu => NOK
  (is (not (varuste-ulkoiset/kelvollinen-tr-filter nil 1 nil nil nil))) ; vain aosa annettu => NOK
  (is (varuste-ulkoiset/kelvollinen-tr-filter 1 1 3 2 1))) ; leta < aeta, kun aosa != losa => OK


(deftest kaytetaanko-palvelussa-tr-osoite-suodatinta
  (let [tie 4
        aosa 422
        aeta 648
        odotettu [{:alkupvm #inst "2020-09-29T21:00:00.000-00:00"
                  :id 8
                  :kuntoluokka "Erittäin hyvä"
                  :lisatieto nil
                  :loppupvm nil
                  :muokkaaja "migraatio"
                  :tietolaji "tl506"
                  :toteuma "tarkastus"
                  :tr-alkuetaisyys aeta
                  :tr-alkuosa aosa
                  :tr-loppuetaisyys nil
                  :tr-loppuosa nil
                  :tr-numero tie
                  :ulkoinen-oid "1.2.246.578.4.3.12.512.310173997"}]
        saatu (->> (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-urakan-varustetoteuma-ulkoiset
                                  +kayttaja-jvh+
                                  {:urakka-id urakka-id-35 :hoitokauden-alkuvuosi 2019 :tie tie :aosa aosa :aeta aeta :losa nil :leta nil})
                  :toteumat
                  (map #(dissoc % :sijainti :muokattu)))]
    (is (= odotettu saatu))))

(deftest varustehaun-vertaile-funktio

  ; -------1-----|--2-- osa
  ; ---1---2---3-|--1-- eta
  ; -------A-----|----- A
  ; -----------B-|----- B
  ; -------------|--C-- C
  (let [A [1 2]
        B [1 3]
        C [2 1]
        vertaile (fn [[aosa1 aeta1] [aosa2 aeta2]]
                   (first (first (q (str "SELECT tr_vertaile(" aosa1 ", " aeta1 ", " aosa2 ", " aeta2 ")")))))]
    (is (= 0 (vertaile A A)) "vertaile(A,A) => 0")
    (is (= -1 (vertaile A B)) "vertaile(A,B) => -1")
    (is (= 1 (vertaile B A)) "vertaile(B,A) => 1")
    (is (= -1 (vertaile A C)) "vertaile(A,C) => -1")
    (is (= 1 (vertaile C A)) "vertaile(C,A) => 1")
    (is (= -1 (vertaile B C)) "vertaile(B,C) => -1")
    (is (= 1 (vertaile C B)) "vertaile(C,B) => 1")))


(deftest varustehaun-leikkaus-funktio
  (let [
        ; TR-osoite filterin testit.
        ; Testissä v w ja x ovat varusteita tietokannassa c - k ovat filtereitä, jotka hakevat kannasta osumia.

        ; Data
        ; --------1-------|--------2-------- osa
        ; --1---2---3---4-|--1---2---3---4-- etaisyys
        ; ---------v1=====|=====v2---------- varuste v (aosa 1 aeta 3 => losa 2 leta 2)
        ; ---------w12----|----------------- varuste w
        ; ----------------|-----x12--------- varuste x

        varusteet {:v [1 3 2 2]
                   :w [1 3 1 3]
                   :x [2 2 2 2]}

        ; Filtterit
        ; --------1-------|--------2-------- osa
        ; --1---2---3---4-|--1---2---3---4-- etaisyys
        ; ----------------|---------c1==c2-- c => ei leikkaa
        ; -d1==d2---------|----------------- d => ei leikkaa
        ; -----e1======e2-|----------------- e => leikkaa alusta v, sisältää w
        ; ----------------|-f1======f2------ f => leikkaa lopusta v, sisältää x
        ; -----g1=========|=========g2------ g => sisältää kokonaan kaikki
        ; -------------h1=|=h2-------------- h => sisältyy kokonaan v
        ; ---------i12----|----------------- i => pistemäinen v alussa sekä w alussa ja lopussa
        ; -----j12--------|----------------- j => kokonaan ennen kaikkia
        ; ----------------|----------k12---- k => kokonaan kaikkien jälkeen
        ; -------------l12|----------------- l => pistemäinen v sisällä
        ; ----------------|-----m12--------- m => pistemäinen v lopussa

        filterit {:c [2 3 2 4]
                  :d [1 1 1 2]
                  :e [1 2 1 4]
                  :f [2 1 2 3]
                  :g [1 2 2 3]
                  :h [1 4 2 1]
                  :i [1 3 1 3]
                  :j [1 2 1 2]
                  :k [2 3 2 3]
                  :l [1 4 1 4]
                  :m [2 2 2 2]}

        leikkaus (fn [varuste filteri]
                   (let [tie 1
                         [aosa1 aeta1 losa1 leta1] (get varusteet varuste)
                         [aosa2 aeta2 losa2 leta2] (get filterit filteri)
                         leikkaus (q (format "SELECT varuste_leikkaus(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"
                                             tie aosa1 aeta1 losa1 leta1
                                             tie aosa2 aeta2 losa2 leta2))]
                     (first (first leikkaus))))

        osuvat-varusteet (fn [filteri]
                           (filter #(leikkaus % filteri) [:v :w :x]))]

    (is (= [] (osuvat-varusteet :c)) "c")
    (is (= [] (osuvat-varusteet :d)) "d")
    (is (= [:v :w] (osuvat-varusteet :e)) "e")
    (is (= [:v :x] (osuvat-varusteet :f)) "f")
    (is (= [:v :w :x] (osuvat-varusteet :g)) "g")
    (is (= [:v] (osuvat-varusteet :h)) "h")
    (is (= [:v :w] (osuvat-varusteet :i)) "i")
    (is (= [] (osuvat-varusteet :j)) "j")
    (is (= [] (osuvat-varusteet :k)) "k")
    (is (= [:v] (osuvat-varusteet :l)) "l")
    (is (= [:v :x] (osuvat-varusteet :m)) "m" )))

(def odotettu-excel-raportti
   {:raportti [:raportti
              {:nimi "Varustetoimenpiteet 01.10.2019-30.09.2020",
               :raportin-yleiset-tiedot {:raportin-nimi "Varustetoimenpiteet",
                                         :urakka "Oulun MHU 2019-2024",
                                         :alkupvm "01.10.2019",
                                         :loppupvm "30.09.2020"},
               :orientaatio :landscape}
               [:taulukko
                {:nimi "Varustetoimenpiteet"
                 :tyhja nil}
                [{:fmt :pvm
                  :otsikko "Ajankohta"}
                 {:otsikko "Tierekisteriosoite"}
                 {:otsikko "Toimenpide"}
                 {:otsikko "Varustetyyppi"}
                 {:otsikko "Varusteen lisätieto"}
                 {:otsikko "Kuntoluokitus"}
                 {:otsikko "Tekijä"}]
                '({:rivi [#inst "2019-09-30T21:00:00.000-00:00"
                         "Tie 4 / 421 / 1921"
                         "Lisätty"
                         "Viemärit"
                         "kansi: 600 kaivo: 1000 syvyys: 1000 materiaali: betoni"
                         "Erittäin hyvä"
                         "migraatio"]}
                 {:rivi [#inst "2019-11-29T22:00:00.000-00:00"
                         "Tie 4 / 422 / 637"
                         "Lisätty"
                         "Viemärit"
                         nil
                         "Hyvä"
                         "migraatio"]}
                 {:rivi [#inst "2020-01-30T22:00:00.000-00:00"
                         "Tie 4 / 421 / 1907"
                         "Päivitetty"
                         "Viemärit"
                         nil
                         "Puuttuu"
                         "migraatio"]}
                 {:rivi [#inst "2020-04-24T21:00:00.000-00:00"
                         "Tie 4 / 421 / 1900"
                         "Lisätty"
                         "Viemärit"
                         nil
                         "Puuttuu"
                         "migraatio"]}
                 {:rivi [#inst "2020-06-09T21:00:00.000-00:00"
                         "Tie 4 / 420 / 5758"
                         "Lisätty"
                         "Viemärit"
                         nil
                         "Erittäin huono"
                         "migraatio"]}
                 {:rivi [#inst "2020-09-29T21:00:00.000-00:00"
                         "Tie 4 / 421 / 1904"
                         "Korjattu"
                         "Viemärit"
                         nil
                         "Tyydyttävä"
                         "migraatio"]}
                 {:rivi [#inst "2020-09-29T21:00:00.000-00:00"
                         "Tie 4 / 422 / 641"
                         "Lisätty"
                         "Viemärit"
                         nil
                         "Puuttuu"
                         "migraatio"]}
                 {:rivi [#inst "2020-09-29T21:00:00.000-00:00"
                         "Tie 4 / 422 / 648"
                         "Tarkastettu"
                         "Liikennemerkit"
                         nil
                         "Erittäin hyvä"
                         "migraatio"]}
                 {:rivi [#inst "2020-09-29T21:00:00.000-00:00"
                         "Tie 4 / 422 / 656"
                         "Lisätty"
                         "Viemärit"
                         nil
                         "Puuttuu"
                         "migraatio"]})]]})

(deftest tee-varuste-ulkoiset-excel
  (let [odotettu odotettu-excel-raportti
        vastaus (kutsu-excel-palvelua (:http-palvelin jarjestelma)
                  :varusteet-ulkoiset-excel
                  +kayttaja-jvh+
                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id :hoitokauden-alkuvuosi 2019})]
    (is (= vastaus odotettu) "Varusteiden excel ei vastannut odotettua")))
