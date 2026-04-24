(ns harja.palvelin.palvelut.tiemerkinta-yhteenveto-test
  "Tiemerkintä kustannusten yhteenveto sekä muita tiemerkintöjen testejä"
  (:require [harja.testi :refer :all]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]
            [harja.palvelin.palvelut.kustannusten-kirjaus :as tiemerkinta]
            [harja.palvelin.palvelut.yllapito-toteumat :as yllapito-toteumat]))

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :laadunseuranta (component/using
                            (laadunseuranta/->Laadunseuranta)
                            [:http-palvelin :db])
          :muut (component/using
                  (yllapito-toteumat/->YllapitoToteumat)
                  [:http-palvelin :db])
          :kustannukset (component/using
                          (tiemerkinta/->TiemerkinnanKustannusKirjaukset)
                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)


(defn- tee-kutsu [palvelu params]
  (kutsu-palvelua
    (:http-palvelin jarjestelma)
    palvelu
    +kayttaja-jvh+
    params))


(defn- tyhjenna-tiemerkinta-tietokanta-kokonaan 
  "Tyhjennä kaikki tiemerkintöihin liittyvät taulut
   RESTART IDENTITY resetoi auto increment arvot 
   CASCADE handlaa dependency järjestyksen automaattisesti"
  []
  (u "TRUNCATE 
      tiemerkinta_korjauskustannus, 
      tiemerkinta_yllapitokohteen_kustannus, 
      tiemerkinta_paikkauskohteen_kustannus, 
      sanktio,
      laatupoikkeama, 
      yllapito_muu_toteuma RESTART IDENTITY CASCADE;"))


(defn- lisaa-korjauskustannus [vuosi maara pk1 pk2 pk3]
  (u (format
       "INSERT INTO tiemerkinta_korjauskustannus (urakka,luoja,luotu,muokattu,muokkaaja,kustannusvuosi,kustannus,pk1,pk2,pk3) 
        VALUES
        (
        (SELECT id FROM urakka WHERE nimi = 'Utajärven Tiemerkintäurakka POP ELY 2025–2027 (optiot 2028 ja 2029), P')::INT,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
        %s,
        %s,
        %s,
        %s,
        %s
        )", vuosi maara pk1 pk2 pk3)))


(defn- lisaa-paallystyskustannus [linjamerkinnat pienmerkinnat jyrsinnat]
  (u (format
       "INSERT INTO tiemerkinta_yllapitokohteen_kustannus (yllapitokohde,luoja,luotu,muokattu,muokkaaja,linjamerkinnat,pienmerkinnat,jyrsinnat) 
        VALUES (
        (SELECT id FROM yllapitokohde WHERE nimi = 'Ouluntie 2')::INT,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
        %s,
        %s,
        %s
        );", linjamerkinnat pienmerkinnat jyrsinnat)))


(defn- lisaa-tiemerkinta-sanktio [selite summa urakka-id]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        tehty (pvm/->pvm-aika (str "01.02." nykyinen-vuosi " 00:00"))
        alku (pvm/->pvm-aika (str "01.01." nykyinen-vuosi " 00:00"))
        loppu (pvm/->pvm-aika (str "12.12." nykyinen-vuosi " 00:00"))
        toimenpideinstanssi (ffirst (q "SELECT id FROM toimenpideinstanssi WHERE nimi = 'Tiemerkinnän TP'"))
        params {:sanktio {:kasittelyaika tehty,
                          :suorasanktio true,
                          :laji :yllapidon_sakko,
                          :summa summa,
                          :lomake-selite selite,
                          :toimenpideinstanssi toimenpideinstanssi,
                          :perintapvm tehty},
                :laatupoikkeama {:aika tehty,
                                 :tekijanimi "Vale Koodari",
                                 :paatos {:paatos "sanktio",
                                          :kasittelytapa :muu,
                                          :muukasittelytapa "Tiemerkintä",
                                          :kasittelyaika tehty,
                                          :perustelu selite},
                                 :urakka urakka-id,
                                 :yllapitokohde nil,
                                 :uusi-liite nil},
                :hoitokausi [alku loppu]}]

    (tee-kutsu :tallenna-suorasanktio params)))


(defn- lisaa-tiemerkinta-bonus [selite summa urakka-id]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        tehty (pvm/->pvm-aika (str "02.01." nykyinen-vuosi " 00:00"))
        alku (pvm/->pvm-aika (str "01.01." nykyinen-vuosi " 00:00"))
        loppu (pvm/->pvm-aika (str "12.12." nykyinen-vuosi " 00:00"))
        toimenpideinstanssi (ffirst (q "SELECT id FROM toimenpideinstanssi WHERE nimi = 'Tiemerkinnän TP'"))
        params {:sanktio {:id nil,
                          :laji :yllapidon_bonus,
                          :suorasanktio true,
                          :summa summa,
                          :indeksi nil,
                          :perintapvm tehty,
                          :liitteet nil,
                          :toimenpideinstanssi toimenpideinstanssi},
                :laatupoikkeama {:id nil,
                                 :tekijanimi "Vale Koodari",
                                 :urakka urakka-id,
                                 :yllapitokohde nil,
                                 :aika tehty,
                                 :uusi-liite nil,
                                 :paatos {:paatos "sanktio",
                                          :perustelu selite,
                                          :kasittelyaika tehty,
                                          :kasittelytapa :muu,
                                          :muukasittelytapa "Tiemerkintä"}},
                :hoitokausi [alku loppu]}]

    (tee-kutsu :tallenna-suorasanktio params)))


(defn- lisaa-tiemerkinta-muu-kustannus [maara tyyppi selite urakka sopimus yp-luokka]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        tehty (pvm/->pvm-aika (str "02.02." nykyinen-vuosi " 00:00"))
        alku (pvm/->pvm-aika (str "01.01." nykyinen-vuosi " 00:00"))
        loppu (pvm/->pvm-aika (str "01.01." (inc nykyinen-vuosi) " 00:00"))
        params {:toteumat [{:id nil,
                            :pvm tehty,
                            :hinta maara,
                            :tyyppi tyyppi,
                            :selite selite,
                            :poistettu false,
                            :yllapitoluokka yp-luokka}],
                :urakka-id urakka,
                :sopimus-id sopimus,
                :alkupvm alku,
                :loppupvm loppu}]

    (tee-kutsu :tallenna-yllapito-toteumat params)))


(deftest tiemerkinta-yhteenveto-toimii
  ;; Halutaan erityisesti testata prosenttilaskelmat
  (tyhjenna-tiemerkinta-tietokanta-kokonaan)

  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        alkuaika (pvm/->pvm-aika (str "01.01." nykyinen-vuosi " 00:00"))
        loppuaika (pvm/->pvm-aika (str "01.01." (inc nykyinen-vuosi) " 00:00"))
        urakka-id (hae-urakan-id-nimella "Utajärven Tiemerkintäurakka POP ELY 2025–2027 (optiot 2028 ja 2029), P")
        sopimus-id (ffirst (q "SELECT id FROM sopimus WHERE nimi = 'Utajärven Tiemerkintäurakka Sopimus';"))
        paasopimus-id (ffirst (q "SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-2024'"))

        params {:kaikki? false,
                :sopimus sopimus-id
                :valittu-aikavali [alkuaika loppuaika],
                :urakan-tiedot {:id urakka-id
                                :alkupvm (pvm/->pvm-aika "01.01.2017 00:00")
                                :loppupvm (pvm/->pvm-aika "01.01.2027 00:00")}}

        yhteenvedon-sarakkeet [:kustannus
                               :pk1-hinta
                               :pk2-hinta
                               :pk3-hinta
                               :pk1-prosentti
                               :pk2-prosentti
                               :pk3-prosentti
                               :ei-luokkaa-prosentti
                               :ei-luokkaa-hinta]

        yhteenvedon-rivit #{:korjaus
                            :paikkausten-merkinnat
                            :paallysteiden-merkinnat
                            :sakko
                            :bonus
                            :arvonmuutokset
                            :muut-kustannukset
                            :yhteensa}

        kaikki-sarakkeet-tyhjia? (fn [data]
                                   (every?
                                     (fn [rivi]
                                       (every? #(== 0 (or (get rivi % 0) 0)) yhteenvedon-sarakkeet))
                                     data))

        ;; Tietokanta on tyhjänä, tehdään kutsu 
        vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto params)

        ;; Kaikki pitäisi olla 0
        _ (is (= yhteenvedon-rivit (set (map :tyyppi vastaus))) "Kaikki yhteenvedon rivit löytyy")
        _ (is (kaikki-sarakkeet-tyhjia? vastaus) "Yhteenvedon sarakkeet pitäisi näyttää nollaa")]


    (testing "Yhteenvedon Tiemerkintöjen korjaus laskennat toimii"
      (let [_ (tyhjenna-tiemerkinta-tietokanta-kokonaan)
            ;; -----------------------------------
            ;; Lisää toteuma nykyiselle vuodelle
            korjaus-yht-maara 5000.0
            pk1-prosentti 25.0
            pk2-prosentti 25.0
            pk3-prosentti 50.0
            _ (lisaa-korjauskustannus nykyinen-vuosi korjaus-yht-maara pk1-prosentti pk2-prosentti pk3-prosentti)

            ;; Hae tiedot uudelleen
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto params)

            ;; Dataa pitäisi löytyä nykyiseltä vuodelta
            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa")

            ;; Hae vastauksesta korjauskustannukset
            korjaukset (first (filter #(= (:tyyppi %) :korjaus) vastaus))
            yht (:kustannus korjaukset)

            ;; ----------------------------------------------------------------------
            ;; Varmista, että prosentit vastaa määriin, sekä insertattuihin arvoihin
            _ (is (= korjaus-yht-maara yht) "Lisätty kustannus täsmää")

            _ (is (= (:pk1-prosentti korjaukset) (* 100 (/ (:pk1-hinta korjaukset) yht))) "PK1 prosentti täsmää pk1 hintaa")
            _ (is (= (:pk2-prosentti korjaukset) (* 100 (/ (:pk2-hinta korjaukset) yht))) "PK2 prosentti täsmää pk2 hintaa")
            _ (is (= (:pk3-prosentti korjaukset) (* 100 (/ (:pk3-hinta korjaukset) yht))) "PK3 prosentti täsmää pk3 hintaa")

            _ (is (= (:pk1-prosentti korjaukset) pk1-prosentti) "PK1 prosentti täsmää insertoitua arvoa")
            _ (is (= (:pk2-prosentti korjaukset) pk2-prosentti) "PK2 prosentti täsmää insertoitua arvoa")
            _ (is (= (:pk3-prosentti korjaukset) pk3-prosentti) "PK3 prosentti täsmää insertoitua arvoa")]))


    (testing "Yhteenvedon Päällystyskohteiden tiemerkintäkustannukset laskennat toimii"
      (let [_ (tyhjenna-tiemerkinta-tietokanta-kokonaan)
            ;; -----------------------------------
            ;; Lisää päällystyskustannus
            linjamerkinnat 1000.0
            pienmerkinnat 9000.0
            jyrsinnat 2000.0
            _ (lisaa-paallystyskustannus linjamerkinnat pienmerkinnat jyrsinnat)

            odotettu-yhteensa (+ linjamerkinnat pienmerkinnat jyrsinnat)

            ;; Hae tiedot uudelleen
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto params)

            ;; Dataa pitäisi löytyä 2025 vuodelta 
            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - päällystys")

            ;; Hae vastauksesta päällysteiden merkinnät 
            kustannukset (first (filter #(= (:tyyppi %) :paallysteiden-merkinnat) vastaus))
            yht (:kustannus kustannukset)

            _ (is (= (bigdec odotettu-yhteensa) (bigdec yht)) "Lisätty kustannus täsmää - päällystys")

            _ (is (= (:pk1-prosentti kustannukset) 0.0) "PK1 prosentti 0 koska luokkaa ei annettu - päällystys")
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti 0 koska luokkaa ei annettu - päällystys")
            _ (is (= (:pk3-prosentti kustannukset) 0.0) "PK3 prosentti 0 koska luokkaa ei annettu - päällystys")]))


    (testing "Yhteenvedon Sakot ja Bonukset toimii"
      (let [_ (tyhjenna-tiemerkinta-tietokanta-kokonaan)
            ;; -----------------------------------
            ;; Lisää sakko
            selite (str "Sakko " nykyinen-vuosi)
            odotettu-maara 100
            _ (lisaa-tiemerkinta-sanktio selite odotettu-maara urakka-id)

            ;; Hae tiedot uudelleen
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto params)

            ;; Dataa pitäisi löytyä nykyiseltä vuodelta
            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - sakko")

            ;; Hae vastauksesta sakot 
            sakot (first (filter #(= (:tyyppi %) :sakko) vastaus))
            sakot-yht (:kustannus sakot)

            _ (is (= (bigdec (- odotettu-maara)) (bigdec sakot-yht)) "Lisätty sakko täsmää - sakko")
            _ (is (= (:ei-luokkaa-prosentti sakot) 100.0) "Ei luokkaa prosentti pitäisi olla 100% - sakko")

            ;; -----------------------------------
            ;; Lisää bonus 
            selite (str "Bonus " nykyinen-vuosi)
            odotettu-maara 1000
            _ (lisaa-tiemerkinta-bonus selite odotettu-maara urakka-id)

            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto params)

            bonukset (first (filter #(= (:tyyppi %) :bonus) vastaus))
            bonukset-yht (:kustannus bonukset)

            _ (is (= (bigdec odotettu-maara) (bigdec bonukset-yht)) "Lisätty bonus täsmää - bonus")
            _ (is (= (:ei-luokkaa-prosentti bonukset) 100.0) "Ei luokkaa prosentti pitäisi olla 100% - bonus")]))


    (testing "Yhteenvedon muut kustannukset toimii"
      (let [_ (tyhjenna-tiemerkinta-tietokanta-kokonaan)
            ;; -----------------------------------
            ;; Muu kustannus 
            odotettu-yhteensa 100.0
            yp-luokka {:lyhyt-nimi 1, :nimi "PK1", :numero 8}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "muu" "Muu kustannus" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - Muu kustannus")

            kustannukset (first (filter #(= (:tyyppi %) :muut-kustannukset) vastaus))
            yht (:kustannus kustannukset)

            _ (is (= (bigdec odotettu-yhteensa) (bigdec yht)) "Lisätty kustannus täsmää - Muu kustannus")
            _ (is (= (:pk1-prosentti kustannukset) 100.0) "PK1 prosentti 100% - Muu kustannus")
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti 0% - Muu kustannus")
            _ (is (= (:pk3-prosentti kustannukset) 0.0) "PK3 prosentti 0% - Muu kustannus")



            ;; -----------------------------------
            ;; Arvomuutos  
            yp-luokka {:lyhyt-nimi 2, :nimi "PK2", :numero 9}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "arvonmuutos" "Arvonmuutos" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - arvomuutos")

            kustannukset (first (filter #(= (:tyyppi %) :arvonmuutokset) vastaus))
            yht (:kustannus kustannukset)

            _ (is (= (bigdec odotettu-yhteensa) (bigdec yht)) "Lisätty kustannus täsmää - arvomuutos")
            _ (is (= (:pk1-prosentti kustannukset) 0.0) "PK1 prosentti 0% - arvomuutos")
            _ (is (= (:pk2-prosentti kustannukset) 100.0) "PK2 prosentti 100% - arvomuutos")
            _ (is (= (:pk3-prosentti kustannukset) 0.0) "PK3 prosentti 0% - arvomuutos")


            ;; -----------------------------------
            ;; Indeksimuutos   
            yp-luokka {:lyhyt-nimi 3, :nimi "PK3", :numero 10}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "indeksi" "Indeksi" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - indeksimuutos")

            kustannukset (first (filter #(= (:tyyppi %) :muut-kustannukset) vastaus))
            yht (:kustannus kustannukset)

            ;; Koska indeksimuutos lasketaan Muut kustannukset alle 
            ;; Näitä pitäisi olla nyt yhteensä 200.0 e
            _ (is (= (* (bigdec odotettu-yhteensa) 2) (bigdec yht)) "Yhteensä arvo täsmää - indeksimuutos")

            ;; Koska aikaisemmin lisättiin muu kustannus
            ;; Ja indeksimuutokset Muut kustannukset  alle
            ;; -> prosentit pitäisi olla nyt 50 0 50 
            _ (is (= (:pk1-prosentti kustannukset) 50.0) "PK1 prosentti 50% - indeksimuutos")
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti 0% - indeksimuutos")
            _ (is (= (:pk3-prosentti kustannukset) 50.0) "PK3 prosentti 50% - indeksimuutos")



            ;; -----------------------------------
            ;; Lisätyö   
            yp-luokka {:lyhyt-nimi -, :nimi "Ei pk-luokkaa", :numero nil}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "lisatyo" "Lisätyö" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - lisätyö")

            kustannukset (first (filter #(= (:tyyppi %) :muut-kustannukset) vastaus))
            yht (:kustannus kustannukset)

            ;; Koska lisätyö lasketaan Muut kustannukset alle 
            ;; Näitä pitäisi olla nyt yhteensä 300.0 e
            _ (is (= (* (bigdec odotettu-yhteensa) 3) (bigdec yht)) "Yhteensä arvo täsmää - lisätyö")

            ;; Nyt ollaan lisätty pk1, pk3, sekä ei luokkaa (Muut kustannukset alle)
            ;; Pk2 pitäisi olla 0, loput 33.333%
            ;; Pk2 lisättiin arvonmuutos, joka tulee omalle rivilleen
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti on 0% - lisätyö")
            _ (is (= (Math/round (:pk1-prosentti kustannukset)) 33) "PK1 prosentti on 33% - lisätyö")
            _ (is (= (Math/round (:pk3-prosentti kustannukset)) 33) "PK3 prosentti on 33% - lisätyö")
            _ (is (= (Math/round (:ei-luokkaa-prosentti kustannukset)) 33) "Ei pk luokkaa prosentti on 33% - lisätyö")



            ;; -----------------------------------
            ;; Muutostyö   
            yp-luokka {:lyhyt-nimi -, :nimi "Ei pk-luokkaa", :numero nil}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "muutostyo" "Muutostyö" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - muutostyö")

            kustannukset (first (filter #(= (:tyyppi %) :muut-kustannukset) vastaus))
            yht (:kustannus kustannukset)

            ;; Pitäisi olla nyt yhteensä 400.0 e
            _ (is (= (* (bigdec odotettu-yhteensa) 4) (bigdec yht)) "Yhteensä arvo täsmää - muutostyö")

            ;; Olemassa nyt 
            ;;   2x ei luokkaa, 
            ;;   1 kpl pk1, 
            ;;   1 kpl pk3
            ;;      ->  50%  25%  25% 
            ;;
            ;; Pk2 pitäisi olla 0% (arvomuutos)
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti on 0% - muutostyö")
            _ (is (= (Math/round (:pk1-prosentti kustannukset)) 25) "PK1 prosentti on 25% - muutostyö")
            _ (is (= (Math/round (:pk3-prosentti kustannukset)) 25) "PK3 prosentti on 25% - muutostyö")
            _ (is (= (Math/round (:ei-luokkaa-prosentti kustannukset)) 50) "Ei pk luokkaa prosentti on 50% - muutostyö")



            ;; -----------------------------------
            ;; Sopimusalueen muutos   
            yp-luokka {:lyhyt-nimi -, :nimi "Ei pk-luokkaa", :numero nil}
            _ (lisaa-tiemerkinta-muu-kustannus odotettu-yhteensa "sopimusalueen-muutos" "Sop muutos" urakka-id paasopimus-id yp-luokka)
            vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))

            _ (is (not (kaikki-sarakkeet-tyhjia? vastaus)) "Yhteenvedon sarakkeet EI pitäisi näyttää nollaa - Sopimusalueen muutos")

            kustannukset (first (filter #(= (:tyyppi %) :muut-kustannukset) vastaus))
            yht (:kustannus kustannukset)

            ;; Pitäisi olla nyt yhteensä 500.0 e
            _ (is (= (* (bigdec odotettu-yhteensa) 5) (bigdec yht)) "Yhteensä arvo täsmää - Sopimusalueen muutos")

            ;; Olemassa nyt 
            ;;   3x ei luokkaa, 
            ;;   1 kpl pk1, 
            ;;   1 kpl pk3
            ;;      ->  60%  20%  20% 
            _ (is (= (:pk2-prosentti kustannukset) 0.0) "PK2 prosentti on 0% - Sopimusalueen muutos")
            _ (is (= (Math/round (:pk1-prosentti kustannukset)) 20) "PK1 prosentti on 20% - Sopimusalueen muutos")
            _ (is (= (Math/round (:pk3-prosentti kustannukset)) 20) "PK3 prosentti on 20% - Sopimusalueen muutos")
            _ (is (= (Math/round (:ei-luokkaa-prosentti kustannukset)) 60) "Ei pk luokkaa prosentti on 60% - Sopimusalueen muutos")]))

    
    (testing "Yhteenvedon Yhteensä sarake laskee prosentit oikein"
      (let [vastaus (tee-kutsu :hae-tiemerkinta-yhteenveto (assoc params :sopimus paasopimus-id))
            kustannukset (first (filter #(= (:tyyppi %) :yhteensa) vastaus))

            {:keys [kustannus pk1-hinta pk2-hinta pk3-hinta ei-luokkaa-hinta
                    pk1-prosentti pk2-prosentti pk3-prosentti ei-luokkaa-prosentti]} kustannukset

            _ (is (= (Math/round (double pk1-prosentti)) (Math/round (double (* 100 (/ pk1-hinta kustannus)))))
                "Yhteensä sarakkeen PK1 prosentti laskenta toimii")
            _ (is (= (Math/round (double pk2-prosentti)) (Math/round (double (* 100 (/ pk2-hinta kustannus)))))
                "Yhteensä sarakkeen PK2 prosentti laskenta toimii")
            _ (is (= (Math/round (double pk3-prosentti)) (Math/round (double (* 100 (/ pk3-hinta kustannus)))))
                "Yhteensä sarakkeen PK3 prosentti laskenta toimii")
            _ (is (= (Math/round (double ei-luokkaa-prosentti)) (Math/round (double (* 100 (/ ei-luokkaa-hinta kustannus)))))
                "Yhteensä sarakkeen ei pk luokkaa prosentti laskenta toimii")]))))


(deftest hae-tiemerkinta-kustannustyypit-toimii
  (let [params {:urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")}
        vastaus (tee-kutsu :hae-tiemerkinta-kustannustyypit params)]
    (is (=
         #{:muu :indeksi :sopimusalueen-muutos :arvonmuutos :lisatyo :muutostyo}
         (set (map (comp keyword :tyyppi) vastaus)))
      "Kaikki kustannustyypit löytyy")))


(deftest tiemerkinnan-tila-trigger-toimii []
  (let [paikkauskohde-nimi "MT 86 Paavolantie"
        tiemerkinnan-tila (fn [] (ffirst (q
                                           (format "SELECT \"tiemerkinnan-tila\" FROM paikkauskohde WHERE nimi = '%s'"
                                             paikkauskohde-nimi))))
        vaihda-tila (fn [tila]
                      (u
                        (format "UPDATE paikkauskohde SET 
                                 \"paikkauskohteen-tila\" = '%s'::paikkauskohteen_tila,
                                 \"tiemerkintaa-tuhoutunut?\" = true 
                                 WHERE nimi = '%s'"
                          tila paikkauskohde-nimi)))

        _ (vaihda-tila "ehdotettu")
        _ (is (= (tiemerkinnan-tila) "ei-tiemerkintaa") "Ehdotettu -> ei tiemerkintää")

        _ (vaihda-tila "tilattu")
        _ (is (= (tiemerkinnan-tila) "ei-tiemerkintaa") "Tilattu -> ei tiemerkintää")

        _ (vaihda-tila "tarkistettu")
        _ (is (= (tiemerkinnan-tila) "ei-tiemerkintaa") "Tarkistettu -> ei tiemerkintää")

        _ (vaihda-tila "hylatty")
        _ (is (= (tiemerkinnan-tila) "ei-tiemerkintaa") "Hylätty -> ei tiemerkintää")

        ;; Valmis -> tila pitäisi muuttua käsittelemättä 
        _ (vaihda-tila "valmis")
        _ (is (= (tiemerkinnan-tila) "kasittelematta")
            "Paikkauskohde valmistui -> Tiemerkinnän tila pitäisi olla käsittelemättä")


        ;; Merkitse paikkauskohde valmiiksi, mutta tiemerkintää ei tuhoutunut
        _ (vaihda-tila "hylatty")
        _ (u
            (format "UPDATE paikkauskohde SET 
                                           \"paikkauskohteen-tila\" = '%s'::paikkauskohteen_tila,
                                           \"tiemerkintaa-tuhoutunut?\" = false 
                                           WHERE nimi = '%s'"
              "valmis" paikkauskohde-nimi))

        _ (is (= (tiemerkinnan-tila) "ei-tiemerkintaa")
            "Tiemerkintää ei tuhoutunut -> tila pitäisi olla: ei tiemerkintää")]))

