(ns ^:hidas harja.palvelin.palvelut.ilmoitukset-test
  (:require [clj-time.coerce :as tc]
            [clojure.test :refer :all]
            [clojure.set :as set]
            [clojure.core.async :refer [go]]

            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]

            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c])
  (:import (java.time LocalDateTime Month ZoneId)
           (java.util Date)
           (org.joda.time DateTime)))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-ilmoitukset (component/using
                                           (ilmoitukset/->Ilmoitukset)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (urakkatieto-lopetus!))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-ilmoitukset-sarakkeet
  (let []
    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :urakkanimi :ilmoitusid :ilmoitettu :valitetty :valitetty-urakkaan :otsikko :ilmoitustyyppi :selitteet :sijainti
           :uusinkuittaus :tila :urakkatyyppi :tila

           [:tr :numero] [:tr :alkuosa] [:tr :loppuosa] [:tr :alkuetaisyys] [:tr :loppuetaisyys]

           [:kuittaukset 0 :id] [:kuittaukset 0 :kuitattu] [:kuittaukset 0 :kuittaustyyppi]
           [:kuittaukset 0 :kuittaaja :etunimi] [:kuittaukset 0 :kuittaaja :sukunimi]]

          :hae-ilmoitukset
          {:hallintayksikko nil
           :urakka (hae-oulun-alueurakan-2014-2019-id)
           :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
           :tyypit [:kysely :toimepidepyynto :ilmoitus]
           :aikavali nil
           :aloituskuittauksen-ajankohta :kaikki
           :hakuehto nil
           :lajittelu-suunta :laskeva}))))

(def hae-ilmoitukset-parametrit
  {:hallintayksikko nil
   :urakka nil
   :hoitokausi nil
   :aikavali [(Date. 0 0 0) (Date.)]
   :tyypit +ilmoitustyypit+
   :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
   :aloituskuittauksen-ajankohta :kaikki
   :hakuehto ""
   :lajittelu-suunta :laskeva})

(defn hae [parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-ilmoitukset +kayttaja-jvh+ parametrit))

(deftest hae-ilmoituksia
  (let [parametrit hae-ilmoitukset-parametrit
        ilmoitusten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoitusid-12347-kuittaukset-maara-suoraan-kannasta
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitusid = 12347;")))
        ilmoitukset-palvelusta (hae parametrit)
        kuittaukset-palvelusta (mapv :kuittaukset ilmoitukset-palvelusta)
        kuittaukset-palvelusta-lkm (apply + (map count kuittaukset-palvelusta))
        ilmoitusid-12348 (first (filter #(= 12348 (:ilmoitusid %)) ilmoitukset-palvelusta))
        ilmoitusid-12348-kuittaukset (:kuittaukset ilmoitusid-12348)
        ilmoitusid-12347 (first (filter #(= 12347 (:ilmoitusid %)) ilmoitukset-palvelusta))
        ilmoitusid-12347-kuittaukset (:kuittaukset ilmoitusid-12347)
        uusin-kuittaus-ilmoitusidlle-12347 (:uusinkuittaus ilmoitusid-12347)
        uusin-kuittaus-ilmoitusidlle-12347-testidatassa (pvm/aikana (pvm/->pvm "18.12.2007") 19 17 30 000)]
    (doseq [i ilmoitukset-palvelusta]
      (is (#{:toimenpidepyynto :tiedoitus :kysely}
            (:ilmoitustyyppi i)) "ilmoitustyyppi"))
    (is (= 0 (count ilmoitusid-12348-kuittaukset)) "12348:lla ei kuittauksia")
    (is (= ilmoitusten-maara-suoraan-kannasta (count ilmoitukset-palvelusta)) "Ilmoitusten lukumäärä")
    (is (= kuittausten-maara-suoraan-kannasta kuittaukset-palvelusta-lkm) "Kuittausten lukumäärä")
    (is (= ilmoitusid-12347-kuittaukset-maara-suoraan-kannasta (count ilmoitusid-12347-kuittaukset)) "Ilmoitusidn 123347 kuittausten määrä")
    (is (= uusin-kuittaus-ilmoitusidlle-12347-testidatassa uusin-kuittaus-ilmoitusidlle-12347) "uusinkuittaus ilmoitukselle 12347")))

(deftest hae-ilmoitukset-tyypin-mukaan
  (let [hoito-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                 :urakkatyyppi :hoito)) ;; Teiden hoidon ilmoitukset palautuvat hoito-ilmoitusten mukana
        paallystys-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                      :urakkatyyppi :paallystys))
        kaikki-ilmoitukset (hae (assoc hae-ilmoitukset-parametrit
                                  :urakkatyyppi :kaikki))

        idt #(into #{} (map :id) %)]

    ;; urakkatyypitön ilmoitus tulee aina, joten näitä on 2
    (is (= 3 (count paallystys-ilmoitukset)))

    (is (< (count paallystys-ilmoitukset)
           (count hoito-ilmoitukset)
           (count kaikki-ilmoitukset)))

    (is (= (set/union (idt hoito-ilmoitukset)
                      (idt paallystys-ilmoitukset))
           (idt kaikki-ilmoitukset)))))

(deftest tallenna-ilmoitustoimenpide
  (let [parametrit [{:ilmoittaja-sukunimi "Vastaava"
                     :ilmoittaja-tyopuhelin "0400123123"
                     :ilmoittaja-etunimi "Järjestelmän"
                     :ilmoittaja-organisaatio "Liikennevirasto"
                     :ilmoittaja-ytunnus nil
                     :ilmoittaja-sahkoposti "jvh@example.com"
                     :ilmoituksen-id 1
                     :ulkoinen-ilmoitusid 123
                     :ilmoittaja-matkapuhelin "0400123123"
                     :vapaatesti "TESTI123"
                     :tyyppi :lopetus
                     :aiheutti-toimenpiteita true}]

        ilmoitusten-maara-ennen (ffirst (q
                                          (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-ennen (ffirst (q
                                          (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoituksen-1-kuittaukset-maara-ennen
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitus = 1;")))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-ilmoitustoimenpiteet
                          +kayttaja-jvh+
                          parametrit)
        ilmoitusten-maara-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM ilmoitus;")))
        kuittausten-maara-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM ilmoitustoimenpide;")))
        ilmoituksen-1-kuittaukset-maara-jalkeen
        (ffirst (q (str "SELECT count(*) FROM ilmoitustoimenpide WHERE ilmoitus = 1;")))

        aiheutti-toimenpiteita (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE id = 1;")))]

    ;; Ilmoituksia sama määrä, kuittausten määrä kannassa nousi yhdellä
    (is (= ilmoitusten-maara-ennen ilmoitusten-maara-jalkeen))
    (is (= (+ kuittausten-maara-ennen 1) kuittausten-maara-jalkeen))
    (is (= (+ ilmoituksen-1-kuittaukset-maara-ennen 1) ilmoituksen-1-kuittaukset-maara-jalkeen))

    (is aiheutti-toimenpiteita "Lopetuskuittaus toimenpiteiden kanssa merkittiin oikein")

    (u "DELETE FROM ilmoitustoimenpide WHERE vapaateksti = 'TESTI123';")))

(deftest tallenna-ilmoitustoimenpide-ilman-oikeuksia
  (let [parametrit [{:ilmoittaja-sukunimi "Vastaava"
                     :ilmoittaja-tyopuhelin "0400123123"
                     :ilmoittaja-etunimi "Järjestelmän"
                     :ilmoittaja-organisaatio "Liikennevirasto"
                     :ilmoittaja-ytunnus nil
                     :ilmoittaja-sahkoposti "jvh@example.com"
                     :ilmoituksen-id 1
                     :ulkoinen-ilmoitusid 123
                     :ilmoittaja-matkapuhelin "0400123123"
                     :vapaatesti "TESTI123"
                     :tyyppi :aloitus}]]
    (is (thrown-with-msg? Exception #"EiOikeutta"
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :tallenna-ilmoitustoimenpiteet
                                          +kayttaja-tero+
                                          parametrit)))))

(deftest hae-ilmoituksia-tienumerolla
  (let [oletusparametrit {:hallintayksikko nil
                          :urakka nil
                          :hoitokausi nil
                          :aikavali [(Date. 0 0 0) (Date.)]
                          :tyypit +ilmoitustyypit+
                          :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                          :aloituskuittauksen-ajankohta :kaikki
                          :hakuehto ""
                          :lajittelu-suunta :laskeva}
        hae (fn [parametrit]
              (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-ilmoitukset +kayttaja-jvh+
                              (merge oletusparametrit parametrit)))

        ilmoitukset-kaikille-teille (hae {:tr-numero nil})
        ilmoitukset-tielle-6 (hae {:tr-numero 6})
        ilmoitukset-olemattomalle-tielle (hae {:tr-numero 9999999})]
    (is (> (count ilmoitukset-kaikille-teille) (count ilmoitukset-tielle-6))
        "Haku ilman tierajausta löytää enemmän ilmoituksia")
    (is (> (count ilmoitukset-tielle-6) 0) "Tielle 6 on ilmoituksia")

    (is (some #(= 6 (get-in % [:tr :numero])) ilmoitukset-kaikille-teille)
        "Tien 6 ilmoituksia löytyy myös kaikista ilmoituksista")

    (is (every? #(= 6 (get-in % [:tr :numero])) ilmoitukset-tielle-6)
        "Vain tien 6 ilmoituksia on rajatussa hakujoukossa")

    (is (zero? (count ilmoitukset-olemattomalle-tielle))
        "Olemattomalle tielle rajattu haku ei löydy ilmoituksia")))


(defn urakan-kausi-iso-8601
  "Feikataan urakan kesäkausi dynaamisesti, jotta testejä voi ajaa vaikka t/now vaihtuu.
  (Myöhästymisen tarkastelu käyttää nykyistä aikaa (t/now) sisäisesti.)"
  [siirra-paivia]
  (pvm/pvm->iso-8601 (t/plus (t/now) (t/days siirra-paivia))))

(deftest ilmoitus-myohassa-ilman-kuittauksia-talvikausi
  ;; Kesäkausi on nykyhetki -2 päivä <--> nykyhetki -1 päivä, eli nyt ollaan talvikaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -2)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 -1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                               :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                               :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                               :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7)))
                               :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

;; Kesäkauden testaamisen puolesta riittää testata se kerran tässä yhteydessä.
;; Tarkastellaan muut myöhästymiset vain talvikauden näkökulmasta.
(deftest ilmoitus-myohassa-ilman-kuittauksia-kesakausi
  ;; Kesäkausi on nykyhetki -1 päivää <--> nykyhetki +1 päivä, eli nyt ollaan kesäkaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -1)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/days 7)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/days 7))) :kuittaukset []}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))))

;; Tiedotus-ilmoitus käyttää kesäkaudella monimutkaisempaa logiikkaa myöhästymisen päättelyyn.
;; Testataan myöhästyminen tiedotuksille erikseen.
(deftest tiedotus-ilmoitus-myohassa-kesakausi
  ;; Tiedotus-tyyppisten ilmoitusten talvikauden sääntö on monimutkaisempi, joten mockataan tähän t/now päivämäärä arkipäiväksi.
  ;; (Säännön mukaan ilmoitus pitäisi kuitata viimeistään seuraavan arkipäivän aamuna klo 9).

  ;; Tässä 11.4.2023 on tiistai, normaali arkipäivä
  (with-redefs [t/now #(->
                         (pvm/iso-8601->pvm "2023-04-11")
                         ;; Ilmoituksen kuittaus on minuutin myöhässä
                         (pvm/aikana 9 1 0 0)
                         (tc/to-date-time)
                         (pvm/suomen-aikavyohykkeeseen))]
    (let [myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                                 :urakka-kesakausi-alkupvm "2023-04-01"
                                 :urakka-kesakausi-loppupvm "2023-04-30"
                                 ;; 6.4.2023 on arkipäivä, sen jälkeen arkipyhinä pitkäperjantai ja toinen pääsiäispäivä
                                 ;; Seuraava arkipäivä on 11.4. (tiistai)
                                 :ilmoitettu (c/to-sql-time (pvm/keskipaiva
                                                              (pvm/iso-8601->pvm "2023-04-06")))
                                 :valitetty-urakkaan (c/to-sql-time (pvm/keskipaiva
                                                                      (pvm/iso-8601->pvm "2023-04-06"))) :kuittaukset []}]
      (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus))))))

(deftest tiedotus-ilmoitus-EI-myohassa-kesakausi
  ;; Tiedotus-tyyppisten ilmoitusten talvikauden sääntö on monimutkaisempi, joten mockataan tähän t/now päivämäärä arkipäiväksi.
  ;; (Säännön mukaan ilmoitus pitäisi kuitata viimeistään seuraavan arkipäivän aamuna klo 9).

  ;; Tässä 11.4.2023 on tiistai, normaali arkipäivä
  (testing "Ilmoituksen myöhästymistä tarkastellaan seuraavana arkipäivänä klo 9:00 (Ei myöhässä)"
    (with-redefs [t/now #(->
                           (pvm/iso-8601->pvm "2023-04-11")
                           (pvm/aikana 9 0 0 0)
                           (tc/to-date-time)
                           (pvm/suomen-aikavyohykkeeseen))]
      (let [myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                                   :urakka-kesakausi-alkupvm "2023-04-01"
                                   :urakka-kesakausi-loppupvm "2023-04-30"
                                   ;; 6.4.2023 on arkipäivä, sen jälkeen arkipyhinä pitkäperjantai ja toinen pääsiäispäivä
                                   ;; Seuraava arkipäivä on 11.4. (tiistai)
                                   :ilmoitettu (c/to-sql-time (pvm/keskipaiva
                                                                (pvm/iso-8601->pvm "2023-04-06")))
                                   :valitetty-urakkaan (c/to-sql-time (pvm/keskipaiva
                                                                        (pvm/iso-8601->pvm "2023-04-06"))) :kuittaukset []}]
        (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus))))))

  (testing "Ilmoituksen myöhästymistä tarkastellaan seuraavana arkipäivänä klo 8:59 (Ei myöhässä)"
    (with-redefs [t/now #(->
                           (pvm/iso-8601->pvm "2023-04-11")
                           (pvm/aikana 8 59 0 0)
                           (tc/to-date-time)
                           (pvm/suomen-aikavyohykkeeseen))]
      (let [myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                                   :urakka-kesakausi-alkupvm "2023-04-01"
                                   :urakka-kesakausi-loppupvm "2023-04-30"
                                   ;; 6.4.2023 on arkipäivä, sen jälkeen arkipyhinä pitkäperjantai ja toinen pääsiäispäivä
                                   ;; Seuraava arkipäivä on 11.4. (tiistai)
                                   :ilmoitettu (c/to-sql-time (pvm/keskipaiva
                                                                (pvm/iso-8601->pvm "2023-04-06")))
                                   :valitetty-urakkaan (c/to-sql-time (pvm/keskipaiva
                                                                        (pvm/iso-8601->pvm "2023-04-06"))) :kuittaukset []}]
        (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))))

(deftest ilmoitus-myohassa-kun-kuittaus-myohassa
  ;; Kesäkausi on nykyhetki -2 päivä <--> nykyhetki -1 päivä, eli nyt ollaan talvikaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -2)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 -1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 73)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 73)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :lopetus}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 11)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 11)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                               :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                               :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                               :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-myohassa-kun-kuittaus-vaaraa-tyyppia
  ;; Kesäkausi on nykyhetki -2 päivä <--> nykyhetki -1 päivä, eli nyt ollaan talvikaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -2)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 -1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 75)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 75)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 15)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 15)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :aloitus}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                               :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                               :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                               :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 2)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :aloitus}]}]
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (true? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-ei-myohassa
  ;; Kesäkausi on nykyhetki -2 päivä <--> nykyhetki -1 päivä, eli nyt ollaan talvikaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -2)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 -1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 71)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 71)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :lopetus}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 9)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 9)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                               :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                               :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                               :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 40)))
                               :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 40)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}]
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest ilmoitus-ei-myohassa-valitetty-urakkaan-myohemmin
  ;; Kesäkausi on nykyhetki -2 päivä <--> nykyhetki -1 päivä, eli nyt ollaan talvikaudella
  (let [urakka-kesakausi-alkupvm (urakan-kausi-iso-8601 -2)
        urakka-kesakausi-loppupvm (urakan-kausi-iso-8601 -1)
        myohastynyt-kysely {:ilmoitustyyppi :kysely
                            :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                            :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                            :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/hours 71)))
                            :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/hours 71)))
                            :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :lopetus}]}
        myohastynyt-toimenpidepyynto {:ilmoitustyyppi :toimenpidepyynto
                                      :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                                      :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                                      :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 9)))
                                      :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 9)))
                                      :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}
        myohastynyt-tiedoitus {:ilmoitustyyppi :tiedoitus
                               :urakka-kesakausi-alkupvm urakka-kesakausi-alkupvm
                               :urakka-kesakausi-loppupvm urakka-kesakausi-loppupvm
                               :ilmoitettu (c/to-sql-time (t/minus (t/now) (t/minutes 40)))
                               :valitetty-urakkaan (c/to-sql-time (t/minus (t/now) (t/minutes 40)))
                               :kuittaukset [{:kuitattu (c/to-sql-time (t/now)) :kuittaustyyppi :vastaanotto}]}]
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-kysely)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-toimenpidepyynto)))
    (is (false? (ilmoitukset/ilmoitus-myohassa? myohastynyt-tiedoitus)))))

(deftest aloituskuittausta-ei-annettu-alle-tunnissa
  (let [ilmoitus1 {:ilmoitettu (c/to-sql-time (t/now))
                   :valitetty-urakkaan (c/to-sql-time (t/now)) :kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 80)))
                                                                      :kuittaustyyppi :aloitus}]}
        ilmoitus2 {:ilmoitettu (c/to-sql-time (t/now))
                   :valitetty-urakkaan (c/to-sql-time (t/now)) :kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 55)))
                                                                      :kuittaustyyppi :vastaanotto}]}
        ilmoitus3 {:ilmoitettu (c/to-sql-time (t/now))
                   :valitetty-urakkaan (c/to-sql-time (t/now)):kuittaukset []}]
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus1 (t/hours 1))))
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus2 (t/hours 1))))
    (is (false? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus3 (t/hours 1))))))

(deftest aloituskuittaus-annettu-alle-tunnissa
  (let [ilmoitus {:ilmoitettu (c/to-sql-time (t/now))
                  :valitetty-urakkaan (c/to-sql-time (t/now)):kuittaukset [{:kuitattu (c/to-sql-time (t/plus (t/now) (t/minutes 25)))
                                                                     :kuittaustyyppi :aloitus}]}]
    (is (true? (#'ilmoitukset/sisaltaa-aloituskuittauksen-aikavalilla? ilmoitus (t/hours 1))))))

(deftest tarkista-ajat-ja-aikavalihaut
         (let [alkuaika (clj-time.core/date-time 2005 10 11 2)
               loppuaika (clj-time.core/date-time 2005 10 11 4)
               ilmoittettu (clj-time.core/date-time 2005 10 10 3 5 32)
               valitetty (clj-time.core/date-time 2005 10 11 3 6 37)
               parametrit {:hallintayksikko              nil
                           :urakka                       nil
                           :hoitokausi                   nil
                           :valitetty-urakkaan-alkuaika  (c/to-date alkuaika)
                           :valitetty-urakkaan-loppuaika (c/to-date loppuaika)
                           :tyypit                       +ilmoitustyypit+
                           :tilat                        [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                           :aloituskuittauksen-ajankohta :kaikki
                           :hakuehto                     ""
                           :lajittelu-suunta :laskeva}
               ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :hae-ilmoitukset +kayttaja-jvh+ parametrit)
               ilmoitus (first ilmoitukset-palvelusta)]
              (is (= 1 (count ilmoitukset-palvelusta)) "Annettu aikaväli palauttaa vain yhden ilmoituksen")
              (is (t/after? (c/from-sql-time (:valitetty-urakkaan ilmoitus)) alkuaika) "Välitetty urakkaan alkuajan jälkeen.")
              (is (t/before? (c/from-sql-time (:valitetty-urakkaan ilmoitus)) loppuaika) "Välitetty urakkaan loppuaikaa ennen.")
              (is (t/equal? (c/from-sql-time (:ilmoitettu ilmoitus)) ilmoittettu) "Ilmoitettu-aika on oikein.")
              (is (t/equal? (c/from-sql-time (:valitetty ilmoitus)) valitetty) "Valitetty-aika on oikein.")))

(deftest aikavalihaku-kuluva-kalenterikuukausi
  (with-redefs [pvm/joda-timeksi (fn [] (DateTime. 2005 10 20 13 15 0))
                pvm/nyt (fn [] (pvm/luo-pvm-dec-kk 2005 10 20))]
  (let [parametrit {:hallintayksikko nil
                    :urakka nil
                    :hoitokausi nil
                    :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :kuluva}
                    :tyypit +ilmoitustyypit+
                    :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                    :aloituskuittauksen-ajankohta :kaikki
                    :hakuehto ""
                    :lajittelu-suunta :laskeva}
        ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
    (is (= (count ilmoitukset-palvelusta) 2) "Annettu aikaväli palauttaa kaksi ilmoitusta")
    (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta))
    (is (some #(= "Soittakaa Sepolle" (:otsikko %)) ilmoitukset-palvelusta))
    (is (= (:otsikko (first ilmoitukset-palvelusta)) "Tiellä 6 on taas vikaa")))))

(deftest aikavalihaku-kuluva-kalenterikuukausi-lajittelu-nouseva
  (with-redefs [pvm/joda-timeksi (fn [] (DateTime. 2005 10 20 13 15 0))
                pvm/nyt (fn [] (pvm/luo-pvm-dec-kk 2005 10 20))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :kuluva}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta :nouseva}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      ; palauttaa samat kuin oletus suuntaan järjestettäessä koska tulosjoukon lukumäärä alle 500 rajaarvon
      (is (= (count ilmoitukset-palvelusta) 2) "Annettu aikaväli palauttaa kaksi ilmoitusta")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta))
      (is (some #(= "Soittakaa Sepolle" (:otsikko %)) ilmoitukset-palvelusta))
      (is (= (:otsikko (first ilmoitukset-palvelusta)) "Soittakaa Sepolle")))))

(deftest aikavalihaku-edellinen-kalenterikuukausi
  (with-redefs [pvm/nyt (fn [] (pvm/luo-pvm-dec-kk 2005 11 20))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :edellinen}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta :laskeva}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 2) "Annettu aikaväli palauttaa kaksi ilmoitusta")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta))
      (is (some #(= "Soittakaa Sepolle" (:otsikko %)) ilmoitukset-palvelusta)))))

(deftest aikavalihaku-kalenterikuukausi-tuntematon
  (let [parametrit {:hallintayksikko nil
                    :urakka nil
                    :hoitokausi nil
                    :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :diipadaa}
                    :tyypit +ilmoitustyypit+
                    :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                    :aloituskuittauksen-ajankohta :kaikki
                    :hakuehto ""
                    :lajittelu-suunta :laskeva}
        vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :hae-ilmoitukset +kayttaja-jvh+ parametrit)
                  (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= (.getMessage vastaus) "Tuntematon kalenterikuukausiaikaväli :diipadaa"))))

(deftest aikavalihaku-lajittelu-suunta-puuttuu
  (with-redefs [pvm/joda-timeksi (fn [] (DateTime. 2005 10 20 13 15 0))
                pvm/nyt (fn [] (pvm/luo-pvm-dec-kk 2005 10 20))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :kuluva}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 2) "Annettu aikaväli palauttaa kaksi ilmoitusta")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta))
      (is (some #(= "Soittakaa Sepolle" (:otsikko %)) ilmoitukset-palvelusta))
      (is (= (:otsikko (first ilmoitukset-palvelusta)) "Tiellä 6 on taas vikaa")))))

(deftest aikavalihaku-lajittelu-suunta-nil
  (with-redefs [pvm/joda-timeksi (fn [] (DateTime. 2005 10 20 13 15 0))
                pvm/nyt (fn [] (pvm/luo-pvm-dec-kk 2005 10 20))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "Kuluva kalenterikuukausi", :kalenterikuukausi :kuluva}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta nil}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 2) "Annettu aikaväli palauttaa kaksi ilmoitusta")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta))
      (is (some #(= "Soittakaa Sepolle" (:otsikko %)) ilmoitukset-palvelusta))
      (is (= (:otsikko (first ilmoitukset-palvelusta)) "Tiellä 6 on taas vikaa")))))

(defn- LocalDateTime->Date [localDateTime]
  (Date/from (. (. localDateTime atZone (ZoneId/systemDefault)) toInstant)))
(defn- nyt-localdatetimena [pv]
  (LocalDateTime/of 2005 Month/OCTOBER pv 6 25))
(deftest aikavalihaku-edellinen-tunti
  (with-redefs [pvm/nyt (fn [] (LocalDateTime->Date (nyt-localdatetimena 11)))
                pvm/tuntia-sitten (fn [tuntia] (LocalDateTime->Date (. (nyt-localdatetimena 11) minusHours tuntia)))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "1 tunnin ajalta" :tunteja 1}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta :laskeva}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 1) "Annettu aikaväli palauttaa yhden ilmoituksen")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta)))))

(deftest aikavalihaku-edellinen-12-tuntia
  (with-redefs [pvm/nyt (fn [] (LocalDateTime->Date (nyt-localdatetimena 11)))
                pvm/tuntia-sitten (fn [tuntia] (LocalDateTime->Date (. (nyt-localdatetimena 11) minusHours tuntia)))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "1 tunnin ajalta" :tunteja 12}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta :laskeva}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 1) "Annettu aikaväli palauttaa yhden ilmoituksen")
      (is (some #(= "Tiellä 6 on taas vikaa" (:otsikko %)) ilmoitukset-palvelusta)))))

(deftest aikavalihaku-edellinen-paiva-ei-ilmoitusta
  (with-redefs [pvm/nyt (fn [] (LocalDateTime->Date (nyt-localdatetimena 20)))
                ; 20 lokakuuta 2005 tienoilla ei toivottavasti ole eikä tule ilmoitusta
                pvm/tuntia-sitten (fn [tuntia] (LocalDateTime->Date (. (nyt-localdatetimena 20) minusHours tuntia)))]
    (let [parametrit {:hallintayksikko nil
                      :urakka nil
                      :hoitokausi nil
                      :valitetty-urakkaan-vakioaikavali {:nimi "1 päivän ajalta" :tunteja 24}
                      :tyypit +ilmoitustyypit+
                      :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
                      :aloituskuittauksen-ajankohta :kaikki
                      :hakuehto ""
                      :lajittelu-suunta :laskeva}
          ilmoitukset-palvelusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-ilmoitukset +kayttaja-jvh+ parametrit)]
      (is (= (count ilmoitukset-palvelusta) 0) "Annettu aikaväli ei palauta yhtään ilmoitusta"))))

(deftest hae-ilmoitus-oikeudet
  (let [hae-ilmoitus-kayttajana #(kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :hae-ilmoitus % 1)]
    (testing "Ilmoituksen haku oikeuksilla toimii"
      (is (= (:ilmoitusid (hae-ilmoitus-kayttajana +kayttaja-jvh+)) 12345)))

    (testing "Ilmoituksen haku ilman oikeuksia epäonnistuu"
      (is (thrown-with-msg?
            Exception #"EiOikeutta"
            (hae-ilmoitus-kayttajana +kayttaja-ulle+))))))

(def ilmoituksien-lkm-perffitestissa 10000)

(defn- lisaa-testi-ilmoitus [urakka-id tyyppi tila]
  (u (str "INSERT INTO ilmoitus (urakka, ilmoitettu, valitetty, vastaanotettu, \"vastaanotettu-alunperin\", \"valitetty-urakkaan\", ilmoitustyyppi, tila) VALUES (" urakka-id ", (select now() - interval '2 days'), ((select now() - interval '2 days') + interval '5 minutes'), ((select now() - interval '2 days') + interval '5 minutes'), ((select now() - interval '2 days') + interval '5 minutes'), ((select now() - interval '2 days') + interval '5 minutes'),'" tyyppi"'::ilmoitustyyppi, '" tila"'::ilmoituksen_tila);")))

(deftest hae-ilmoitukset-ei-ole-liian-hidas
  (let [urakka-idt (mapv first (q (str "SELECT id FROM urakka;")))]

    (doseq [i (range ilmoituksien-lkm-perffitestissa)]
      (let [urakka (rand-nth  urakka-idt)
            tyyppi (rand-nth  ["toimenpidepyynto", "tiedoitus", "kysely"])
            tila (rand-nth ["kuittaamaton" "vastaanotettu" "aloitettu" "lopetettu"])]
        (lisaa-testi-ilmoitus urakka tyyppi tila))))

  (let [parametrit hae-ilmoitukset-parametrit
        ilmoitusten-maara-suoraan-kannasta (ffirst (q
                                                     (str "SELECT count(*) FROM ilmoitus;")))
        aika-ennen (pvm/millisekunteina (pvm/nyt))
        ilmoitukset-palvelusta (hae parametrit)
        aika-jalkeen (pvm/millisekunteina (pvm/nyt))
        palvelukutsun-kesto-ms (- aika-jalkeen aika-ennen)]
    ;; patologisella kyselyllä tämä räjähtää yli 3s kestäväksi.
    ;; Pyritään pitämään silti kestovaatimus tiukempana, niin että mahdolliset vähemmän katastrofaalisetkin
    ;; perffihuonontumiset jäisivät kiinni. Toivottavasti ei ole kovin ajoympäristöherkkä tämä kesto.
    (is (< palvelukutsun-kesto-ms 800) "Ilmoituksien haku kestää liian kauan")

    (doseq [i ilmoitukset-palvelusta]
      (is (#{:toimenpidepyynto :tiedoitus :kysely}
            (:ilmoitustyyppi i)) "ilmoitustyyppi"))
    (is (= 501 (count ilmoitukset-palvelusta)) "Ilmoitusten lukumäärä") ;eka sivullinen eli 500+1 palautuu
    (is (= ilmoitusten-maara-suoraan-kannasta 10053) "Ilmoitusten lukumäärä")))

(deftest ^:perf hae-ilmoitukset-kesto
  (is (gatling-onnistuu-ajassa?
        "Hae ilmoitukset"
        {:concurrency 100
         :timeout-in-ms 6000}
        #(hae hae-ilmoitukset-parametrit))))
