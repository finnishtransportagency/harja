(ns harja.palvelin.palvelut.kayttajatiedot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kayttajatiedot (component/using
                                          (kayttajatiedot/->Kayttajatiedot)
                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each jarjestelma-fixture)


(deftest hae-kayttajan-urakat-toimii-oikein
  ;; Testaa että käyttäjän urakoiden haku toimii, halutaan normaalisti palauttaa vain käynnissä olevat urakat
  (let [urakka-id-saimaa (hae-urakan-id-nimella "Saimaan kanava")
        urakka-id-joensuu (hae-urakan-id-nimella "Joensuun kanava")
        urakka-id-yha (hae-urakan-id-nimella "YHA-päällystysurakka")
        urakka-id-oulu (hae-urakan-id-nimella "Oulun päällystyksen palvelusopimus")

        ;; Testikannan hallintayksiköt
        kanava-hallintayksikko 1
        pohjanmaa-hallintayksikko 12

        ;; Aseta Saimaan kanava päättyneeksi 
        _ (u "UPDATE urakka SET alkupvm = '1996-12-19', loppupvm = '1996-12-20' WHERE id = " urakka-id-saimaa " AND hallintayksikko = " kanava-hallintayksikko ";")
        ;; Aseta Joensuun kanava käynnissä olevaksi  
        _ (u "UPDATE urakka SET alkupvm = '2012-12-19', loppupvm = '2052-12-20' WHERE id = " urakka-id-joensuu " AND hallintayksikko = " kanava-hallintayksikko ";")
        ;; Aseta Oulun urakka käynnissä olevaksi  
        _ (u "UPDATE urakka SET alkupvm = '2012-12-19', loppupvm = '2052-12-20' WHERE id = " urakka-id-oulu " AND hallintayksikko = " pohjanmaa-hallintayksikko ";")
        ;; Aseta YHA urakka päättyneeksi 
        _ (u "UPDATE urakka SET alkupvm = '1996-12-19', loppupvm = '1996-12-20' WHERE id = " urakka-id-yha " AND hallintayksikko = " pohjanmaa-hallintayksikko ";")

        tulos-kanava (kutsu-palvelua
                       (:http-palvelin jarjestelma)
                       :kayttajan-urakat +kayttaja-jvh+
                       [kanava-hallintayksikko])
        
        tulos-pohjanmaa (kutsu-palvelua
                          (:http-palvelin jarjestelma)
                          :kayttajan-urakat +kayttaja-jvh+
                          [pohjanmaa-hallintayksikko])

        fn-urakka-id-loytyy (fn [id data]
                              (some #(= id (:id %)) (mapcat :urakat data)))]

    ;; Joensuu löytyy (käynnissä oleva)
    (is (true? (fn-urakka-id-loytyy urakka-id-joensuu tulos-kanava)) "Käynnissä oleva urakka löytyy")
    ;; Saimaa ei löydy (päättynyt)
    (is (nil? (fn-urakka-id-loytyy urakka-id-saimaa tulos-kanava)) "Päättynyttä urakkaa ei löydy")

    ;; Oulun urakka löytyy (käynnissä oleva)
    (is (true? (fn-urakka-id-loytyy urakka-id-oulu tulos-pohjanmaa)) "Käynnissä oleva urakka löytyy")
    ;; YHA urakkaa ei löydy (päättynyt)
    (is (nil? (fn-urakka-id-loytyy urakka-id-yha tulos-pohjanmaa)) "Päättynyttä urakkaa ei löydy")))


(deftest yhteydenpito-vastaanottajat-toimii
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :yhteydenpito-vastaanottajat +kayttaja-jvh+ nil)
        odotettu-ennen (ffirst (q "SELECT count(*) FROM kayttaja where sahkoposti IS NOT NULL AND muokattu >  now() - interval '1 year' ;"))
        ;; lisätään yhdelle muokattu aikaleima alle vuosi tästä hetkestä
        _ (u "UPDATE kayttaja SET muokattu = NOW() WHERE id = " (:id +kayttaja-jvh+))
        tulos-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                :yhteydenpito-vastaanottajat +kayttaja-jvh+ nil)
        odotettu-jalkeen (ffirst (q "SELECT count(*) FROM kayttaja where sahkoposti IS NOT NULL AND muokattu >  now() - interval '1 year' ;"))]
    (is (= (count tulos) odotettu-ennen))
    (is (= (count tulos-jalkeen) odotettu-jalkeen))
    (is (= (vec (distinct (mapcat keys tulos-jalkeen))) [:etunimi :sukunimi :sahkoposti]))))

(deftest yhdista-kayttajan-urakat-alueittain
  (let [ely-kaakkoissuomi {:id 7, :nimi "Kaakkois-Suomi", :elynumero 3}
        ely-lappi {:id 1, :nimi "Lappi", :elynumero 678}
        tienpaallystys-1 {:id 18, :nimi "Tienpäällystysurakka KAS ELY 1 2015", :alue nil}
        hoito-1 {:id 1, :nimi "Joku hoidon urakka", :alue nil}
        tienpaallystys-2 {:id 2, :nimi "Joku tienpäällystysjuttu", :alue nil}
        hoito-2 {:id 24, :nimi "Joku hoitourakkajuttu", :alue nil}]

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}
                    {:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-2}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-2
                        tienpaallystys-1}}
              {:tyyppi :hoito,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{hoito-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}
                    {:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-1}}]
          urakat-b [{:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-2}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :hoito,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{hoito-2 hoito-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-lappi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :paallystys,
               :hallintayksikko ely-lappi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-lappi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :paallystys,
               :hallintayksikko ely-lappi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a []
          urakat-b []]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b))
          []))))
