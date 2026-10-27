(ns harja.domain.laadunseuranta_test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.test]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktion-lomake :as sanktion-lomake-domain]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktion-summamaaritys :as summamaaritys]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]
            [harja.pvm :as pvm]))

(deftest muu-kuin-muistutus-on-julkinen-sanktiodomainin-predikaatti
  (testing "muistutukset tunnistetaan yleisessä sanktio-domainissa"
    (is (sanktio-domain/muu-kuin-muistutus? {:laji :A}))
    (is (not (sanktio-domain/muu-kuin-muistutus? {:laji :muistutus})))
    (is (not (sanktio-domain/muu-kuin-muistutus? {:laji :yllapidon_muistutus})))
    (is (not (sanktio-domain/muu-kuin-muistutus? {:laji :vesivayla_muistutus})))))

(deftest sanktion-lomakekonteksti-palauttaa-kenttasaannot
  (let [aikakausikonteksti (sanktion-lomake-domain/aikakausikonteksti 2025 2025 :teiden-hoito)
        perus {:sanktio {:laji :A :tyyppi {:koodi 1}}
               :yllapitourakka? false
               :yllapitokohdeurakka? false
               :vesivaylaurakka? false
               :aikakausikonteksti aikakausikonteksti}]
    (testing "tavallinen hoidon sanktio näyttää tyypin, tapahtumapaikan ja perustelun"
      (is (= {:nayta-tyyppi? true
              :nayta-tapahtumapaikka? true
              :nayta-perustelu? true
              :nayta-puute-tai-laiminlyonti? false
              :nayta-havaittu? true
              :laskutus-ilman-laskutuskelpoisuutta? false
              :nayta-indeksi? false
              :nayta-manuaalinen-summa? true
              :salli-arvonvahennys? false}
             (select-keys (sanktion-lomake-domain/sanktion-lomakekonteksti perus)
                          [:nayta-tyyppi? :nayta-tapahtumapaikka? :nayta-perustelu?
                           :nayta-puute-tai-laiminlyonti? :nayta-havaittu?
                           :laskutus-ilman-laskutuskelpoisuutta? :nayta-indeksi?
                           :nayta-manuaalinen-summa? :salli-arvonvahennys?]))))
    (testing "manuaalinen summakenttä piilotetaan muistutukselta ja laskutusrajan ylitykseltä"
      (is (false? (:nayta-manuaalinen-summa?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :muistutus})))))
      (is (false? (:nayta-manuaalinen-summa?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :laskutus_yli_laskutusrajan}))))))
    (testing "indeksi näkyy vain indeksin sallivalla aikakausikontekstilla ja muulla kuin muistutuksella"
      (is (true? (:nayta-indeksi?
                   (sanktion-lomake-domain/sanktion-lomakekonteksti
                     (assoc perus :aikakausikonteksti
                            (sanktion-lomake-domain/aikakausikonteksti 2020 2020 :teiden-hoito))))))
      (is (false? (:nayta-indeksi?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :muistutus}
                             :aikakausikonteksti
                             (sanktion-lomake-domain/aikakausikonteksti 2020 2020 :teiden-hoito)))))))
    (testing "ylläpitourakka näyttää puute- tai laiminlyöntikentän"
      (is (true? (:nayta-puute-tai-laiminlyonti?
                   (sanktion-lomake-domain/sanktion-lomakekonteksti
                     (assoc perus :yllapitourakka? true))))))
    (testing "laskutus ilman laskutuskelpoisuutta piilottaa Havaittu-kentän"
      (is (false? (:nayta-havaittu?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :laskutus_ilman_laskutuskelpoisuutta}))))))
    (testing "asiakirjamerkintä piilottaa tyypin ja laskutus ilman laskutuskelpoisuutta tapahtumapaikan"
      (is (false? (:nayta-tyyppi?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :asiakirjamerkintojen_paikkansa_pitamattomyys})))))
      (is (false? (:nayta-tapahtumapaikka?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :laskutus_ilman_laskutuskelpoisuutta}))))))
    (testing "työn tekemättä jättämisen tyyppi 22 piilottaa perustelun"
      (is (false? (:nayta-perustelu?
                    (sanktion-lomake-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :tyon_tekematta_jattaminen
                                             :tyyppi {:koodi 22}}))))))
    (testing "talvisuolan ilmoitus ja validointi koskevat talvisuolan ylitystä"
      (let [konteksti (sanktion-lomake-domain/sanktion-lomakekonteksti
                        (assoc perus
                          :sanktio {:laji :talvisuolan_ylitys}
                          :urakan-loppupvm (pvm/->pvm "30.9.2026")))]
        (is (true? (:nayta-talvisuola-ilmoitus? konteksti)))
        (is (some? ((:talvisuolan-validointi konteksti)
                    (pvm/->pvm "1.10.2024"))))))
    (testing "puuttuva urakan loppupäivä ei riko talvisuolan validointia"
      (is (nil? (sanktion-lomake-domain/talvisuolan-validointi
                  nil (pvm/->pvm "1.10.2024") {:laji :talvisuolan_ylitys}))))
    (testing "aikakausikonteksti vaikuttaa arvonvähennyksen sallimiseen"
      (is (true? (:salli-arvonvahennys?
                   (sanktion-lomake-domain/sanktion-lomakekonteksti
                     (assoc perus :aikakausikonteksti
                            (sanktion-lomake-domain/aikakausikonteksti 2024 2025 :teiden-hoito)))))))))

(deftest sanktion-paivamaarien-asetus
  (let [kasittelypvm (pvm/->pvm "15.11.2025")
        maarattypvm (pvm/->pvm "20.11.2025")
        olemassa-oleva-perintapvm (pvm/->pvm "31.12.2025")
           komponenttirivi {:laji :A
                :sanktioarvo 123
                         :perintapvm nil
                :laskutuskuukausi-komp-tiedot {:vuosi 2025 :kuukausi 11}
                :laatupoikkeama {:paatos {}}}
           perusrivi {:laatupoikkeama {:paatos {}}}]
    (testing "käsittelyajan asetus asettaa määräyspäivän ja puuttuvan perintäpäivän"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika kasittelypvm}}
              :maarattypvm kasittelypvm
              :perintapvm kasittelypvm}
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
                 perusrivi kasittelypvm :kasittelyaika))))
    (testing "määräyspäivän asetus asettaa käsittelyajan ja puuttuvan perintäpäivän hoitokauden loppuun"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika maarattypvm}}
              :maarattypvm maarattypvm
              :perintapvm (pvm/hoitokauden-loppupvm 2026)}
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
                 perusrivi maarattypvm :maarattypvm))))
    (testing "olemassa oleva perintäpäivä säilyy, kun laskutuskuukausi on valittu"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika kasittelypvm}}
              :maarattypvm kasittelypvm
              :perintapvm olemassa-oleva-perintapvm
              :laskutuskuukausi-komp-tiedot {:vuosi 2025 :kuukausi 11}}
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
               (assoc perusrivi
                 :perintapvm olemassa-oleva-perintapvm
                 :laskutuskuukausi-komp-tiedot {:vuosi 2025 :kuukausi 11})
               kasittelypvm :kasittelyaika))))
    (testing "käsittelyajan asetus ei aseta perintäpäivää, kun komponenttitiedot ovat olemassa"
      (is (= (assoc komponenttirivi
               :maarattypvm kasittelypvm
               :perintapvm nil
               :laatupoikkeama {:paatos {:kasittelyaika kasittelypvm}})
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
               komponenttirivi kasittelypvm :kasittelyaika))))
    (testing "määräyspäivän asetus ei aseta perintäpäivää, kun komponenttitiedot ovat olemassa"
      (is (= (assoc komponenttirivi
               :maarattypvm maarattypvm
               :perintapvm nil
               :laatupoikkeama {:paatos {:kasittelyaika maarattypvm}})
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
               komponenttirivi maarattypvm :maarattypvm))))
    (testing "puuttuva määräyspäivä ei riko päivämäärien asetusta"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika nil}}
              :maarattypvm nil
              :perintapvm nil}
             (sanktion-lomake-domain/aseta-kasittely-tai-maarattypvm
               perusrivi nil :maarattypvm))))))

(deftest urakan-mahdolliset-sanktiolajit
  (let [hoidon-lajit-ilman-arvonvahennysta [:muistutus :A :B :C :pohjavesisuolan_ylitys :talvisuolan_ylitys
                                            :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]
        hoidon-lajit-arvonvahennyksella [:muistutus :A :B :C :arvonvahennyssanktio :pohjavesisuolan_ylitys :talvisuolan_ylitys
                                         :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]
        yllapidon-lajit [:yllapidon_sakko :yllapidon_muistutus]
        mhu24-urakka {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2024)}
        mhu25-urakka {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2025)}
        mhu26-urakka {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2026)}
        alueurakka {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)}]

    (testing "Hoidon urakat, kun arvonvähennys on vielä vanhassa sanktiolistassa"
      (is (= hoidon-lajit-arvonvahennyksella
             (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2025))
        "MHU24-urakka ennen hoitovuotta 2026 -> arvonvähennyssanktio mukana")
      (is (= hoidon-lajit-arvonvahennyksella
             (sanktio-domain/urakan-sanktiolajit alueurakka 2025))
        "Alueurakka ennen hoitovuotta 2026 -> arvonvähennyssanktio mukana"))

    (testing "Hoidon urakat, kun arvonvähennys on siirtynyt omalle lomakkeelle"
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2026))
        "MHU24-urakka hoitovuodesta 2026 alkaen -> ei arvonvähennyssanktiota (uusi lomake käytössä)")
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit mhu24-urakka 2027))
        "MHU25-urakka hoitovuonna 2027 -> ei arvonvahennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit mhu26-urakka 2026))
        "MHU26-urakka -> ei arvonvahennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit alueurakka 2026))
        "Alueurakka hoitovuodesta 2026 alkaen -> ei arvonvähennyssanktiota")
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2025))
        "MHU25-urakka -> ei arvonvähennyssanktiota vanhassa listassa hoitovuodesta riippumatta")
      (is (= hoidon-lajit-ilman-arvonvahennysta
             (sanktio-domain/urakan-sanktiolajit mhu25-urakka 2027))
        "MHU25-urakka hoitovuonna 2027 -> ei arvonvähennyssanktiota"))

    (testing "Ylläpidon urakat saavat aina ylläpidon lajit, hoitovuodesta riippumatta"
      (is (= yllapidon-lajit
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2025)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2026)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paallystys} 2027)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2025)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2026)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :paikkaus} 2027)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2025)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2026)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :tiemerkinta} 2027)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2025)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2026)
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :valaistus} 2027))
        "Ylläpidon sanktiolajit"))

    (testing "Tuntematon urakkatyyppi"
      (is (= []
             (sanktio-domain/urakan-sanktiolajit {:tyyppi :vesivayla-hoito} 2025))
        "Muille urakkatyypeille ei tarjota sanktiolajeja"))))

(deftest sanktiolajin-vaihto-sailyttaa-uuteen-lajiin-kuuluvan-tyypin
  (let [vanha-tyyppi {:id 1 :koodi 13 :toimenpidekoodi "H1"}
        toinen-tyyppi {:id 2 :koodi 14 :toimenpidekoodi "H2"}
        toimenpideinstanssit [{:tpi_id 303 :id "H1"}]
        rivi {:laji :A
              :tyyppi vanha-tyyppi
              :toimenpideinstanssi 101
              :summa 250
              :normaalimaara 500
              :laskettava-syote {:maara 2}
              :laskennan-syote {:maara 2}}]
    (is (= (assoc rivi :laji :B :toimenpideinstanssi 303)
           (sanktion-lomake-domain/vaihda-sanktiolajia
             rivi :B [vanha-tyyppi toinen-tyyppi] toimenpideinstanssit)))))

(deftest sanktiolajin-vaihto-esivalitsee-yhden-tyypin-ja-paattelee-toimenpideinstanssin
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi "H2"}
        toimenpideinstanssit [{:tpi_id 202 :id "H2"}
                              {:tpi_id 203 :id "H3"}]
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi nil}]
    (is (= (assoc rivi
             :laji :muistutus
             :tyyppi tyyppi
             :toimenpideinstanssi 202)
           (sanktion-lomake-domain/vaihda-sanktiolajia
             rivi :muistutus [tyyppi] toimenpideinstanssit)))))

(deftest sanktiolajin-vaihto-palauttaa-nil-kohdistuksen-puuttuvalla-vastaavuudella
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi "H2"}
        rivi {:laji :muistutus
              :tyyppi tyyppi
              :toimenpideinstanssi nil}
        toimenpideinstanssit [{:tpi_id 202 :id "H3"}
                              {:tpi_id 203 :id "H4"}]]
    (is (nil?
          (:toimenpideinstanssi
            (sanktion-lomake-domain/vaihda-sanktiolajia
              rivi :A [tyyppi] toimenpideinstanssit))))))

(deftest sanktiolajin-vaihto-nollaa-muistutuksen-summan-ja-kohdistuksen
  (let [rivi {:laji :A
              :tyyppi {:id 1}
              :summa 250
              :omailmoitettu true
              :toimenpideinstanssi 101
              :indeksi 3}]
    (is (= (assoc rivi
             :laji :muistutus
             :tyyppi nil
             :summa nil
             :omailmoitettu false
             :toimenpideinstanssi nil
             :indeksi nil)
           (sanktion-lomake-domain/vaihda-sanktiolajia
             rivi :muistutus [] [])))))

(deftest sanktiolajin-vaihto-sailyttaa-summa-ja-poistaa-laskennan-syotteet
  (let [rivi {:laji :A
              :tyyppi {:id 1}
              :summa 250
              :normaalimaara 500
              :laskettava-syote {:maara 2}
              :laskennan-syote {:maara 2}}]
    (is (= (-> rivi
             (assoc :laji :B :tyyppi nil)
             (dissoc :laskettava-syote :laskennan-syote))
           (sanktion-lomake-domain/vaihda-sanktiolajia
             rivi :B [] [])))))

(deftest sanktiolajin-vaihto-sailyttaa-vanhan-tyypin-toimenpideinstanssin
  (let [vanha-tyyppi {:id 1 :koodi 13 :toimenpidekoodi "H6"}
        toinen-tyyppi {:id 2 :koodi 14 :toimenpidekoodi "H7"}
        rivi {:laji :A
              :tyyppi vanha-tyyppi
              :toimenpideinstanssi 606}
        toimenpideinstanssit [{:tpi_id 202 :id "H6"}]]
    (is (= 202
           (:toimenpideinstanssi
             (sanktion-lomake-domain/vaihda-sanktiolajia
               rivi :B [vanha-tyyppi toinen-tyyppi] toimenpideinstanssit))))))

(deftest muistutuksesta-a-lajiksi-vaihto-palauttaa-toimenpideinstanssin-idn
  (let [tyyppi {:id 1 :koodi 13 :toimenpidekoodi "H6"}
        rivi {:laji :muistutus
              :tyyppi tyyppi
              :toimenpideinstanssi nil}
        toimenpideinstanssit [{:tpi_id 202 :id "H6"}]]
    (is (= 202
           (:toimenpideinstanssi
             (sanktion-lomake-domain/vaihda-sanktiolajia
               rivi :A [tyyppi] toimenpideinstanssit))))))

(deftest muistutuksesta-a-lajiksi-sailyttaa-tyypin-ja-palauttaa-kohdistuksen
  (let [tyyppi {:id 1 :koodi 13 :toimenpidekoodi 22}
        toinen-tyyppi {:id 2 :koodi 14 :toimenpidekoodi 33}
        toimenpideinstanssit [{:id 22 :tpi_id 202} {:id 33 :tpi_id 303}]
        muistutus (sanktion-lomake-domain/vaihda-sanktiolajia
                    {:laji :A :tyyppi tyyppi :toimenpideinstanssi 202}
                    :muistutus [tyyppi] toimenpideinstanssit)]
    (doseq [vuosi [2025 2026]]
      (let [a-sanktio (sanktion-lomake-domain/vaihda-sanktiolajia
                        (assoc muistutus :toimenpideinstanssi nil)
                        :A [toinen-tyyppi tyyppi] toimenpideinstanssit vuosi [])]
        (is (= tyyppi (:tyyppi a-sanktio)))
        (is (= 202 (:toimenpideinstanssi a-sanktio)))))))

(deftest muistutuksesta-a-lajiksi-ei-valitse-epa-selvaa-tyyppia
  (let [rivi {:laji :muistutus :tyyppi {:id 3} :toimenpideinstanssi nil}
        tyypit [{:id 1 :toimenpidekoodi 22} {:id 2 :toimenpidekoodi 33}]
        tulos (sanktion-lomake-domain/vaihda-sanktiolajia
                rivi :A tyypit [{:id 22 :tpi_id 202} {:id 33 :tpi_id 303}])]
    (is (nil? (:tyyppi tulos)))
    (is (nil? (:toimenpideinstanssi tulos)))))

(deftest sanktiotyypin-vaihto-palauttaa-toimenpideinstanssin-uudemmissa-urakoissa
  (doseq [vuosi [2025 2026]]
    (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi 22}
          tulos (sanktion-lomake-domain/vaihda-sanktiotyyppia
                  {:laji :A :tyyppi nil :toimenpideinstanssi nil}
                  tyyppi vuosi [{:id 22 :tpi_id 202}] [])]
      (is (= tyyppi (:tyyppi tulos)))
      (is (= 202 (:toimenpideinstanssi tulos))))))

(deftest koodittoman-tyypin-vaihto-palauttaa-uudemman-urakan-kiintean-kohdistuksen
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        rivi {:laji :A :tyyppi nil :toimenpideinstanssi nil}
        kulun-kohdistukset [{:id 22 :tpi_id 202 :t2_koodi "23150"}]
        toimenpideinstanssit (conj kulun-kohdistukset {:id 33 :tpi_id 303 :t2_koodi "23120"})]
    (is (= 202 (:toimenpideinstanssi
                (sanktion-lomake-domain/vaihda-sanktiotyyppia
                  rivi tyyppi 2026 toimenpideinstanssit kulun-kohdistukset))))))

(deftest koodittoman-tyypin-lajinvaihto-palauttaa-uudemman-urakan-kiintean-kohdistuksen
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        toinen-tyyppi {:id 3 :koodi 18 :toimenpidekoodi 33}
        rivi {:laji :muistutus :tyyppi tyyppi :toimenpideinstanssi nil}
        kohdistukset [{:id 22 :tpi_id 202 :t2_koodi "23150"}]
        toimenpideinstanssit (conj kohdistukset {:id 33 :tpi_id 303 :t2_koodi "23120"})
        tulos (sanktion-lomake-domain/vaihda-sanktiolajia
                rivi :A [toinen-tyyppi tyyppi] toimenpideinstanssit 2026 kohdistukset)]
    (is (= tyyppi (:tyyppi tulos)))
    (is (= 202 (:toimenpideinstanssi tulos)))))

(deftest koodittoman-tyypin-vaihto-ei-valitse-epa-selvaa-kohdistusta
  (let [tyyppi {:id 2 :toimenpidekoodi nil}
        rivi {:laji :A :tyyppi nil :toimenpideinstanssi nil}
        kohdistukset [{:id 22 :tpi_id 202} {:id 33 :tpi_id 303}]]
    (is (nil? (:toimenpideinstanssi
               (sanktion-lomake-domain/vaihda-sanktiotyyppia
                 rivi tyyppi 2026 kohdistukset kohdistukset))))))

(deftest sanktiotyypin-vaihto-ei-keksi-puuttuvaa-kohdistusta
  (let [rivi {:laji :A :tyyppi {:id 1} :toimenpideinstanssi 101}
        kohdistukset [{:id 33 :tpi_id 303}]
        tyyppi {:id 2 :toimenpidekoodi 22}]
    (is (nil? (:toimenpideinstanssi
               (sanktion-lomake-domain/vaihda-sanktiotyyppia
                 rivi tyyppi 2026 kohdistukset kohdistukset))))
    (is (= 101 (:toimenpideinstanssi
                (sanktion-lomake-domain/vaihda-sanktiotyyppia
                  rivi nil 2026 kohdistukset kohdistukset))))))

(deftest sanktion-kulun-kohdistukset-maaraytyvat-urakan-ja-tyypin-mukaan
  (let [talvi {:id 1 :tpi_id 101 :tpi_nimi "Talvihoito" :t2_koodi "23110"}
        soratie {:id 2 :tpi_id 202 :tpi_nimi "Sorateiden hoito" :t2_koodi "23120"}
        liikenne {:id 3 :tpi_id 303 :tpi_nimi "Liikenneympäristö" :t2_koodi "23130"}
        johto {:id 4 :tpi_id 404 :tpi_nimi "Hoidon johto" :t2_koodi "23150"}
        toimenpideinstanssit [talvi soratie liikenne johto]]
    (doseq [[tyyppi odotetut] [["Talvihoito, päätiet" [talvi]]
                               ["Talvihoito, muut tiet" [talvi]]
                               ["Muut hoitourakan tehtäväkokonaisuudet" [soratie liikenne johto]]
                               ["Sorateiden hoito ja ylläpito" [soratie]]
                               ["Liikenneympäristön hoito" [liikenne]]
                               [nil toimenpideinstanssit]]]
      (is (= odotetut
             (sanktion-lomake-domain/mahdolliset-kulun-kohdistukset
               toimenpideinstanssit 2024 tyyppi)))
      (is (= [johto]
             (sanktion-lomake-domain/mahdolliset-kulun-kohdistukset
               toimenpideinstanssit 2025 tyyppi))))
    (is (empty? (sanktion-lomake-domain/mahdolliset-kulun-kohdistukset nil 2025 nil)))))

(deftest sanktiotyypin-vaihto-kayttaa-uuden-tyypin-kulun-kohdistuksia
  (let [talvi {:id 1 :tpi_id 101 :tpi_nimi "Talvihoito" :t2_koodi "23110"}
        soratie {:id 2 :tpi_id 202 :tpi_nimi "Sorateiden hoito" :t2_koodi "23120"}
        johto {:id 3 :tpi_id 303 :tpi_nimi "Hoidon johto" :t2_koodi "23150"}
        toimenpideinstanssit [talvi soratie johto]
        tyyppi {:id 2 :nimi "Sorateiden hoito ja ylläpito"}
        rivi {:laji :A :tyyppi {:id 1 :nimi "Talvihoito, päätiet"}
              :toimenpideinstanssi nil}]
    (doseq [[vuosi odotettu] [[2024 202] [2025 303]]]
      (let [kohdistukset (sanktion-lomake-domain/mahdolliset-kulun-kohdistukset
                           toimenpideinstanssit vuosi (:nimi tyyppi))
            tulos (sanktion-lomake-domain/vaihda-sanktiotyyppia
                    rivi tyyppi vuosi toimenpideinstanssit kohdistukset)]
        (is (= tyyppi (:tyyppi tulos)))
        (is (= odotettu (:toimenpideinstanssi tulos)))))))

(deftest sanktiotyypin-vaihto-vaihtaa-tyypin-ja-johtaa-toimenpideinstanssin
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi 22}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi 101}
        toimenpideinstanssit [{:id 11 :tpi_id 101}
                              {:id 22 :tpi_id 202}]]
    (is (= (assoc rivi
             :tyyppi tyyppi
             :toimenpideinstanssi 202
             :tpi_id 202)
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2024 toimenpideinstanssit toimenpideinstanssit)))))

(deftest sanktiotyypin-vaihto-esivalitsee-ainoan-kulun-kohdistuksen-ilman-toimenpidekoodia
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi nil}
        toimenpideinstanssit [{:id 11 :tpi_id 101}
                              {:id 22 :tpi_id 202}]
        kulun-kohdistukset [{:id 22 :tpi_id 202}]]
    (is (= (assoc rivi
             :tyyppi tyyppi
             :toimenpideinstanssi 202
             :tpi_id 202)
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2024 toimenpideinstanssit kulun-kohdistukset)))))

(deftest sanktiotyypin-vaihto-sailyttaa-nykyisen-toimenpideinstanssin-kun-valintaa-ei-voi-johtaa
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi 101}
        toimenpideinstanssit [{:id 11 :tpi_id 101}
                              {:id 22 :tpi_id 202}]]
    (is (= (assoc rivi :tyyppi tyyppi :tpi_id 101)
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2024 toimenpideinstanssit toimenpideinstanssit)))))

(deftest sanktiotyypin-vaihto-sailyttaa-toimenpideinstanssin-uudemmassa-urakassa
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi 101}
        toimenpideinstanssit [{:id 22 :tpi_id 202}]]
    (is (= (assoc rivi :tyyppi tyyppi)
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 toimenpideinstanssit [])))))

(deftest sanktiotyypin-vaihto-laskee-kiintean-profiilisumman-omailmoitus-tilassa
  (let [tyyppi {:id 2
                :summamaaritykset [{:maaritystapa :automaattinen
                                    :summa-euroina 4000M
                                    :ohjeteksti nil}]}
        rivi {:laji :A
              :tyyppi {:id 1}
              :normaalimaara 999M
              :summa 999M
              :omailmoitettu true
              :laskettava-syote {:maara 2}
              :laskennan-syote {:maara 2}}]
    (is (= (-> rivi
             (assoc :tyyppi tyyppi
               :normaalimaara 4000M
               :summa 2000M
               :omailmoitettu true)
             (dissoc :laskettava-syote :laskennan-syote))
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 [] [])))))

(deftest sanktiotyypin-vaihto-ei-kirjoita-manuaalista-c-ryhman-summaa
  (let [tyyppi {:id 2
                :summamaaritykset [{:maaritystapa :manuaalinen
                                    :summa-euroina 1250M
                                    :ohjeteksti "C-ryhmän manuaalinen summa"}]}
        rivi {:laji :C
              :tyyppi {:id 1}
              :normaalimaara 700M
              :summa 700M
              :omailmoitettu true}]
    (is (= (-> rivi
             (assoc :tyyppi tyyppi
               :omailmoitettu false)
             (dissoc :normaalimaara :summa))
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 [] [])))))

(deftest sanktiotyypin-vaihto-poistaa-vanhan-laskennan-ja-nollaa-ei-kiintean-summan
  (let [tyyppi {:id 2 :summamaaritykset []}
        rivi {:laji :A
              :tyyppi {:id 1}
              :normaalimaara 500M
              :summa 250M
              :omailmoitettu true
              :laskettava-syote {:maara 2}
              :laskennan-syote {:maara 2}}]
    (is (= (-> rivi
             (assoc :tyyppi tyyppi
               :normaalimaara nil
               :summa nil
               :omailmoitettu false)
             (dissoc :laskettava-syote :laskennan-syote))
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 [] [])))))

(deftest sanktiotyypin-uudelleenvalinta-sailyttaa-laskennan-syotteet
  (let [tyyppi {:id 2 :summamaaritykset []}
        laskettava-syote {:maara 2}
        laskennan-syote {:maara 2}
        rivi {:laji :A
              :tyyppi tyyppi
              :normaalimaara 500M
              :summa 250M
              :laskettava-syote laskettava-syote
              :laskennan-syote laskennan-syote}]
    (is (= (assoc rivi :omailmoitettu false)
           (sanktion-lomake-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 [] [])))))

      (deftest aikakausikonteksti-kokoaa-urakkakohtaiset-ehdot
        (let [yleinen {:nayta-indeksi? false
             :perinta-tapa :laskutuskuukausi
             :maaraystapa? false
             :kasittelytavat sanktio-domain/kasittelytavat
             :salli-arvonvahennys? true}
         mhu25 {:nayta-indeksi? false
           :perinta-tapa :hoitovuosi
           :maaraystapa? true
           :kasittelytavat sanktio-domain/kasittelytavat-mhu25
           :salli-arvonvahennys? false}
         mhu26-hoitokausi-2025 {:nayta-indeksi? false
                 :perinta-tapa :hoitovuosi
                 :maaraystapa? true
                 :kasittelytavat sanktio-domain/kasittelytavat-mhu25
                 :salli-arvonvahennys? false}
         muu-urakkatyyppi {:nayta-indeksi? false
                 :perinta-tapa :hoitovuosi
                 :maaraystapa? false
                 :kasittelytavat sanktio-domain/kasittelytavat
                 :salli-arvonvahennys? true}]
          (is (= (assoc yleinen :nayta-indeksi? true)
            (sanktion-lomake-domain/aikakausikonteksti 2020 2025 :teiden-hoito)))
          (is (= yleinen
            (sanktion-lomake-domain/aikakausikonteksti 2023 2025 :teiden-hoito)))
          (is (= mhu25
            (sanktion-lomake-domain/aikakausikonteksti 2025 2025 :teiden-hoito)))
          (is (= mhu26-hoitokausi-2025
            (sanktion-lomake-domain/aikakausikonteksti 2026 2025 :teiden-hoito)))
          (is (= muu-urakkatyyppi
            (sanktion-lomake-domain/aikakausikonteksti 2025 2025 :hoito)))
          (is (= (assoc muu-urakkatyyppi
              :perinta-tapa :hoitovuosi
              :salli-arvonvahennys? false)
            (sanktion-lomake-domain/aikakausikonteksti 2026 2026 :hoito)))))

(deftest laatupoikkeaman-mahdolliset-sanktiolajit
  (let [hoidon-lajit [:muistutus :A :B :C :arvonvahennyssanktio]
        yllapidon-lajit [:yllapidon_sakko :yllapidon_muistutus]
        alkupvm (pvm/hoitokauden-alkupvm 2019)
        alueurakan-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        mhu-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        paallystyksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paallystys :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        paikkauksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paikkaus :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        tiemerkinnan-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :tiemerkinta :alkupvm (pvm/hoitokauden-alkupvm 2019)})
        valaistuksen-lajit (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :valaistus :alkupvm (pvm/hoitokauden-alkupvm 2019)})]

    (is (= [:muistutus :A :B :C :arvonvahennyssanktio]
           alueurakan-lajit mhu-lajit)
      "Hoidon sanktiolajit urakoille laatupoikkeamissa")
    (is (= [:yllapidon_sakko :yllapidon_muistutus]
           paallystyksen-lajit paikkauksen-lajit tiemerkinnan-lajit valaistuksen-lajit)
      "Ylläpidon sanktiolajit laatupoikkeamissa")

    ;; Laatupoikkeamissa hoidon urakat saavat aina arvonvähennyssanktion (validoinnista riippumatta).
    (testing "Hoidon urakat laatupoikkeamissa"
      (is (= hoidon-lajit
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :hoito :alkupvm alkupvm})
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :teiden-hoito :alkupvm alkupvm}))
        (str "Hoidon sanktiolajit laatupoikkeamissa")))

    (testing "Ylläpidon urakat laatupoikkeamissa"
      (is (= yllapidon-lajit
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paallystys :alkupvm alkupvm})
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :paikkaus :alkupvm alkupvm})
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :tiemerkinta :alkupvm alkupvm})
             (sanktio-domain/laatupoikkeaman-sanktiolajit {:tyyppi :valaistus :alkupvm alkupvm}))
        (str "Ylläpidon sanktiolajit laatupoikkeamissa")))))

(deftest sanktiolajien-tyyppien-urakkakohtaiset-poikkeudet
  (let [muistutus-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :muistutus (pvm/hoitokauden-alkupvm 2020))
        muistutus-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :muistutus (pvm/hoitokauden-alkupvm 2021))
        A-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :A (pvm/hoitokauden-alkupvm 2020))
        A-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :A (pvm/hoitokauden-alkupvm 2021))
        B-tyyppikoodit-ennen-2021 (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :B (pvm/hoitokauden-alkupvm 2020))
        B-tyyppikoodit-2021-tai-jalkeen (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :B (pvm/hoitokauden-alkupvm 2021))
        lupaussanktio (sanktio-domain/sanktiolaji->sanktiotyyppi-koodi :lupaussanktio (pvm/hoitokauden-alkupvm 2020))]

    (is (= [13 14 15 16 10] muistutus-tyyppikoodit-ennen-2021)
      "Muistutus sanktiotyypit urakoissa ennen 2020")
    (is (= [13 14 15 16] A-tyyppikoodit-ennen-2021 B-tyyppikoodit-ennen-2021)
      "A ja B lajien sanktiotyypit urakoissa ennen 2020")

    (is (= [13 14 17 10] muistutus-tyyppikoodit-2021-tai-jalkeen)
      "Muistutus sanktiotyypit urakoissa 2020 tai sen jälkeen")
    (is (= [13 14 17] A-tyyppikoodit-2021-tai-jalkeen B-tyyppikoodit-2021-tai-jalkeen)
      "A ja B lajien sanktiotyypit urakoissa 2020 tai sen jälkeen")

    (is (= [0] lupaussanktio)
      "Lupaussanktio")))

(deftest sanktiotyypin-nimi-toistaa-lajin-nimen-kun-tyyppia-ei-ole
  (testing "Sanktiotyypin koodi 0 käyttää sanktiolajin nimeä"
    (is (= "Vastuuhenkilön tenttipistemäärän alentuminen"
           (sanktiotyyppi-domain/sanktiotyypin-nimi
             "Vastuuhenkilön tenttipistemäärän alentuminen"
             {:koodi 0
              :nimi "Ei tarvita sanktiotyyppiä"}))))
  (testing "Erillinen sanktiotyyppi näytetään omalla nimellään"
    (is (= "Talvihoito"
           (sanktiotyyppi-domain/sanktiotyypin-nimi
             "Muistutus"
             {:koodi 2
              :nimi "Talvihoito"})))))

(deftest sanktio-konfiguraation-adapteri-palauttaa-lajit-ja-tyypit
  (let [sanktio-konfiguraatio {:sanktio-lajit [{:laji :muistutus
                                                :nimi "Muistutus"
                                                :jarjestys 1
                                                :sanktiotyypit [{:id 10 :koodi 13 :nimi "Tyyppi A"}
                                                                {:id 11 :koodi 14 :nimi "Tyyppi B"}]}
                                               {:laji :A
                                                :nimi "Sakko"
                                                :jarjestys 2
                                                :sanktiotyypit [{:id 12 :koodi 17 :nimi "Tyyppi C"}]}]}]
    (testing "Lajit tulevat resolverin jarjestyksessa"
      (is (= [:muistutus :A]
             (sanktio-domain/sanktio-konfiguraation-lajit sanktio-konfiguraatio))))

    (testing "Lajin nimi luetaan resolverin profiilidatasta"
      (is (= "Sakko"
             (sanktio-domain/sanktio-konfiguraation-lajin-nimi sanktio-konfiguraatio :A))))

    (testing "Sanktiotyypit tulevat suoraan resolverin lajirivilta"
      (is (= [{:id 10 :koodi 13 :nimi "Tyyppi A"}
              {:id 11 :koodi 14 :nimi "Tyyppi B"}]
             (sanktio-domain/sanktio-konfiguraation-sanktiotyypit sanktio-konfiguraatio :muistutus))))

    (testing "Tuntematon laji ei palauta tyyppeja"
      (is (= []
             (sanktio-domain/sanktio-konfiguraation-sanktiotyypit sanktio-konfiguraatio :tuntematon))))))

(deftest sanktiotyypin-kiintea-automaattinen-summa-erottaa-kaavan
  (let [kiintea {:summamaaritykset [{:maaritystapa :automaattinen
                                     :summa-euroina 4000M}]}
        kaava {:summamaaritykset [{:maaritystapa :automaattinen
                                   :summa-euroina 2000M
                                   :ohjeteksti "alkavalta viikolta"}]}
        manuaalinen {:summamaaritykset [{:maaritystapa :manuaalinen
                                         :summa-euroina 200M
                                         :ohjeteksti "Kirjaa summa"}]}]
    (is (true? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kiintea)))
    (is (= 4000M (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa kiintea)))
    (is (false? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kaava)))
    (is (nil? (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa kaava)))
    (is (false? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? manuaalinen)))
    (is (nil? (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa manuaalinen)))
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys manuaalinen)))
    (is (= {:maaritystapa :manuaalinen
        :summa-euroina 200M
        :ohjeteksti "Kirjaa summa"}
         (first (:summamaaritykset manuaalinen))))))

(deftest kiintea-normaali-summa-perustuu-aina-alkuperaiseen-summaan
  (testing "Normaali kiinteä summa"
    (is (= 4000M
           (summamaaritys/kiintea-sanktiosumma 4000M false))))
  (testing "Itse ilmoitettu kiinteä summa"
    (is (= 2000M
           (summamaaritys/kiintea-sanktiosumma 4000M true))))
  (testing "Itse ilmoitetun summan uudelleen laskeminen ei puolita jo johdettua summaa"
    (is (= 2000M
           (summamaaritys/kiintea-sanktiosumma 4000M true)
           (summamaaritys/kiintea-sanktiosumma 4000M true)))))

(deftest kiintea-summamalli-johtaa-summan-yhdesta-lahteesta
  (let [kiintea {:id 1
                 :summamaaritykset [{:maaritystapa :automaattinen
                                     :summa-euroina 14000M}]
                 :voi-puolittaa-omailmoituksella true}
        ei-puolitettava (dissoc kiintea :voi-puolittaa-omailmoituksella)
        manuaalinen {:id 2
                     :summamaaritykset [{:maaritystapa :manuaalinen
                                         :summa-euroina 200M
                                         :ohjeteksti "Kirjaa summa"}]}]
    (testing "Ilman omailmoitusta summa on normaalimäärä"
      (is (= {:normaalimaara 14000M :omailmoitettu false :summa 14000M}
             (summamaaritys/kiintea-summamalli kiintea false))))
    (testing "Omailmoitus puolittaa normaalimäärästä johdetun summan"
      (is (= {:normaalimaara 14000M :omailmoitettu true :summa 7000M}
             (summamaaritys/kiintea-summamalli kiintea true))))
    (testing "Toistuva kutsu samoilla arvoilla ei puolita summaa uudelleen"
      (let [ensimmainen (summamaaritys/kiintea-summamalli kiintea true)
            toinen (summamaaritys/kiintea-summamalli kiintea true)]
        (is (= 7000M (:summa ensimmainen) (:summa toinen)))
        (is (= ensimmainen toinen))))
    (testing "Omailmoitusta ei sallita, jos profiili ei salli sitä"
      (is (= {:normaalimaara 14000M :omailmoitettu false :summa 14000M}
             (summamaaritys/kiintea-summamalli ei-puolitettava true))))
    (testing "Manuaalisen määrityksen summaa ei johdeta kiinteäksi"
      (is (nil? (summamaaritys/kiintea-summamalli manuaalinen true))))
    (testing "Laskettavalle määritykselle kiinteää summamallia ei muodosteta"
      (is (nil? (summamaaritys/kiintea-summamalli
                  {:id 3
                   :summamaaritykset [{:maaritystapa :laskettu
                                       :laskentatapa :tiekm-yksikkohinta
                                       :laskentaparametrit {:yksikkohinta 100}}]}
                  true))))))

(deftest liikennevahinkobonus-ja-alihankintabonus-palauttavat-kanoniset-nimet
  (is (= "Bonus alihankintasopimusten maksuehdoista"
         (sanktio-domain/sanktiolaji->teksti :alihankintabonus)))
  (is (= "Bonus alihankintasopimusten maksuehdoista"
         (sanktio-domain/bonuslaji->teksti :alihankintabonus)))
  (is (= "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
         (sanktio-domain/sanktiolaji->teksti :liikennevahinkojen_aiheuttajien_selvitysbonus)))
  (is (= "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
         (sanktio-domain/bonuslaji->teksti :liikennevahinkojen_aiheuttajien_selvitysbonus)))
  (is (= :bonukset
         (sanktio-domain/rivin-tyyppi
           {:laji :liikennevahinkojen_aiheuttajien_selvitysbonus
            :bonus? true}))))

(def ^:private tiekm-maaritys
  {:maaritystapa :laskettu
   :laskentaversio 1
   :laskentatapa :tiekm-yksikkohinta
   :laskentaparametrit {:syoteavain "tiekm"
                        :yksikko "tiekm"
                        :desimaalit 2
                        :yksikkohinta 200.0}
   :ohjeteksti nil
   :jarjestys 1})

(def ^:private prosenttiosuus-maaritys
  {:maaritystapa :laskettu
   :laskentaversio 1
   :laskentatapa :prosenttiosuus-syotteesta
   :laskentaparametrit {:syoteavain "laskutuskelvottomana_laskutettu_osuus"
                        :yksikko "€"
                        :desimaalit 2
                        :prosentti 20}
   :ohjeteksti nil
   :jarjestys 1})

(deftest laskentaversio-kuuluu-snapshotin-vastaavuuteen
  (let [maaritys (assoc tiekm-maaritys :laskentaversio 2)
        snapshot {:laskentaversio 1
                  :laskentatapa "tiekm_yksikkohinta"
                  :laskentaparametrit (:laskentaparametrit maaritys)}]
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta? snapshot maaritys)))
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                  (assoc snapshot :laskentaversio nil)
                  (assoc maaritys :laskentaversio nil)))
      "Puuttuva laskentaversio ei saa hyväksyä snapshotia yhteensopivaksi")
    (doseq [virheellinen-versio [0 -1 "1"]]
      (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                    (assoc snapshot :laskentaversio virheellinen-versio)
                    (assoc maaritys :laskentaversio virheellinen-versio)))
        (str "Virheellinen laskentaversio " (pr-str virheellinen-versio)
          " ei saa hyväksyä snapshotia yhteensopivaksi")))))

(deftest laskettu-summamaaritys-luetaan-vain-rakenteisesta-maarityksesta
  (let [laskettu-tyyppi {:summamaaritykset [tiekm-maaritys]}
        ohjetekstillinen-manuaalinen {:summamaaritykset [{:maaritystapa :manuaalinen
                                                          :ohjeteksti "200,00 € / tiekm."}]}]
    (is (= tiekm-maaritys (sanktion-laskenta/laskettu-summamaaritys laskettu-tyyppi)))
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys ohjetekstillinen-manuaalinen))
      "Ohjetekstiä ei saa tulkita laskentakaavana")
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys nil)))
    (is (false? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? laskettu-tyyppi))
      "Laskettu määritys ei ole kiinteä automaattinen summa")))

(deftest laske-sanktion-summa-tiekm-kertaa-yksikkohinta
  (let [tulos (sanktion-laskenta/laske-sanktion-summa tiekm-maaritys 12.5)]
    (is (= 2500.00M (:summa tulos)))
    (is (= 2 (.scale ^BigDecimal (:summa tulos))) "Tulos pyöristetään kahteen desimaaliin")
    (is (= {:syoteavain "tiekm"
            :syote 12.5M
            :yksikko "tiekm"
            :laskentaversio 1
            :laskentatapa "tiekm_yksikkohinta"
            :laskentaparametrit (:laskentaparametrit tiekm-maaritys)}
           (:laskennan-syote tulos))
      "Snapshot sisältää syötteen, laskentatavan ja profiiliparametrit")))

(deftest laske-sanktion-summa-prosenttiosuus-syotteesta
  (testing "Kaksi desimaalia"
    (is (= 246.91M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1234.56)))))
  (testing "HALF_UP pyöristää tasatilanteen ylöspäin"
    (let [maaritys (assoc-in prosenttiosuus-maaritys [:laskentaparametrit :prosentti] 15)]
      (is (= 0.02M (:summa (sanktion-laskenta/laske-sanktion-summa maaritys 0.10)))
        "0,10 × 15 % = 0,015 -> 0,02")))
  (testing "Kokonaisluku ja BigDecimal kelpaavat"
    (is (= 200.00M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1000))))
    (is (= 200.00M (:summa (sanktion-laskenta/laske-sanktion-summa prosenttiosuus-maaritys 1000.00M))))))

(deftest snapshot-vastaa-maaritysta-ja-syotetta
  (let [laskennan-syote {:syoteavain "tiekm"
                         :syote 12.5M
                         :yksikko "tiekm"
                         :laskentaversio 1
                         :laskentatapa "tiekm_yksikkohinta"
                         :laskentaparametrit (:laskentaparametrit tiekm-maaritys)}]
    (is (true? (sanktion-laskenta/syote-vastaa-snapshotia? laskennan-syote 12.50M)))
    (is (true? (sanktion-laskenta/snapshot-vastaa-maaritysta? laskennan-syote tiekm-maaritys)))
    (is (false? (sanktion-laskenta/snapshot-vastaa-maaritysta?
                  laskennan-syote
                  (assoc-in tiekm-maaritys [:laskentaparametrit :yksikkohinta] 300.0))))))

(deftest laske-sanktion-summa-hylkaa-virheellisen-syotteen-kentan-nimella
  (let [hylkaa (fn [maaritys syote osat]
                 (let [virhe (try (sanktion-laskenta/laske-sanktion-summa maaritys syote)
                               nil
                               (catch IllegalArgumentException e (.getMessage e)))]
                   (is (some? virhe) (str "Syöte " (pr-str syote) " pitää hylätä"))
                   (doseq [osa osat]
                     (is (str/includes? (str virhe) osa) (str "Virheessä pitää olla " osa ": " virhe)))))]
    (testing "Puuttuva syöte"
      (hylkaa tiekm-maaritys nil ["tiekm" "skalaarinen numero" "nil"]))
    (testing "Merkkijono ei ole numero"
      (hylkaa tiekm-maaritys "12" ["tiekm" "skalaarinen numero" "java.lang.String"]))
    (testing "Kokoelma ei ole skalaarinen"
      (hylkaa tiekm-maaritys {:arvo 12} ["tiekm" "skalaarinen numero" "PersistentArrayMap"])
      (hylkaa tiekm-maaritys [12] ["tiekm" "skalaarinen numero" "PersistentVector"]))
    (testing "Suhdeluku ei ole skalaarinen desimaaliluku"
      (hylkaa tiekm-maaritys 1/3 ["tiekm" "skalaarinen numero" "Ratio"]))
    (testing "Ei-äärelliset arvot"
      (hylkaa tiekm-maaritys Double/NaN ["tiekm" "äärellinen"])
      (hylkaa tiekm-maaritys Double/POSITIVE_INFINITY ["tiekm" "äärellinen"]))
    (testing "Nolla ja negatiivinen"
      (hylkaa tiekm-maaritys 0 ["tiekm" "nollaa suurempi" "java.lang.Long"])
      (hylkaa tiekm-maaritys -1.5 ["tiekm" "nollaa suurempi" "java.lang.Double"]))
    (testing "Liian monta desimaalia"
      (hylkaa tiekm-maaritys 1.234 ["tiekm" "enintään 2 desimaalia"])
      (hylkaa prosenttiosuus-maaritys 10.001M ["laskutuskelvottomana_laskutettu_osuus" "enintään 2 desimaalia"]))
    (testing "Profiilin minimi- ja maksimiraja"
      (let [rajattu (update tiekm-maaritys :laskentaparametrit assoc :minimi 0.5 :maksimi 100)]
        (hylkaa rajattu 0.25 ["tiekm" "vähintään 0.5"])
        (hylkaa rajattu 100.01 ["tiekm" "enintään 100"])
        (is (= 100.00M (:summa (sanktion-laskenta/laske-sanktion-summa
                                 (update tiekm-maaritys :laskentaparametrit assoc :yksikkohinta 1.0 :maksimi 100)
                                 100))))))
    (testing "Profiilin desimaalitarkkuus rajaa syötettä"
      (hylkaa (update tiekm-maaritys :laskentaparametrit assoc :desimaalit 0) 1.5
        ["tiekm" "enintään 0 desimaalia"]))))

(deftest laske-sanktion-summa-hylkaa-virheellisen-laskentamaarityksen
  (testing "Tuntematon laskentatapa"
    (is (thrown-with-msg? IllegalArgumentException #"laskentatapa.*:kaava-ohjetekstista"
          (sanktion-laskenta/laske-sanktion-summa (assoc tiekm-maaritys :laskentatapa :kaava-ohjetekstista) 1))))
  (testing "Puuttuva yksikköhinta"
    (is (thrown-with-msg? IllegalArgumentException #"laskentaparametrit.*yksikkohinta"
          (sanktion-laskenta/laske-sanktion-summa
            (update tiekm-maaritys :laskentaparametrit dissoc :yksikkohinta) 1))))
  (testing "Palvelimen tukemaa tarkkuutta suurempi profiilitarkkuus"
    (is (thrown-with-msg? IllegalArgumentException #"laskentaparametrit.*desimaalit"
          (sanktion-laskenta/laske-sanktion-summa
            (update tiekm-maaritys :laskentaparametrit assoc :desimaalit 3) 1)))))

(deftest syotteen-otsikko-muodostuu-profiilin-laskentaparametreista
  (is (= "Tiekm"
         (sanktion-laskenta/syotteen-otsikko (:laskentaparametrit tiekm-maaritys))))
  (is (= "Laskutuskelvottomana laskutettu osuus (€)"
         (sanktion-laskenta/syotteen-otsikko (:laskentaparametrit prosenttiosuus-maaritys)))))

(deftest syotteen-virhe-esitarkistaa-syotteen-profiilin-saannoilla
  (let [parametrit (:laskentaparametrit tiekm-maaritys)
        rajattu (assoc parametrit :minimi 0.5 :maksimi 100)]
    (testing "Kelvolliset syötteet"
      (doseq [syote [12.5 20 0.01 1.15 100.00]]
        (is (nil? (sanktion-laskenta/syotteen-virhe parametrit syote)) (str "Syöte " syote))))
    (testing "Puuttuva ja ei-numeerinen syöte"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit nil)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit "12"))))
    (testing "Nolla, negatiivinen ja ei-äärellinen"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit 0)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit -1.5)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit Double/NaN)))
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit Double/POSITIVE_INFINITY))))
    (testing "Liian monta desimaalia"
      (is (some? (sanktion-laskenta/syotteen-virhe parametrit 1.234)))
      (is (some? (sanktion-laskenta/syotteen-virhe (assoc parametrit :desimaalit 0) 1.5))))
    (testing "Profiilin minimi ja maksimi"
      (is (some? (sanktion-laskenta/syotteen-virhe rajattu 0.25)))
      (is (some? (sanktion-laskenta/syotteen-virhe rajattu 100.01)))
      (is (nil? (sanktion-laskenta/syotteen-virhe rajattu 100))))))

(deftest laskettava-syote-palautuu-lomakkeelle-palvelimen-snapshotista
  (let [luettu-rivi {:id 1 :summa -2500.0 :laskennan-syote {:syote 12.5 :syoteavain "tiekm"}}]
    (is (= 12.5 (sanktion-laskenta/laskettava-syote luettu-rivi)))
    (is (= 20 (sanktion-laskenta/laskettava-syote (assoc luettu-rivi :laskettava-syote 20)))
      "Käyttäjän muokkaus voittaa snapshotin")
    (is (nil? (sanktion-laskenta/laskettava-syote (assoc luettu-rivi :laskettava-syote nil)))
      "Tyhjennetty kenttä ei palaa snapshotin arvoon")
    (is (nil? (sanktion-laskenta/laskettava-syote {})))))

(deftest laskettu-tulos-on-esikatselu-muokatusta-syotteesta-tai-palvelimen-summa
  (let [luettu-rivi {:id 1 :summa -2500.0 :laskennan-syote {:syote 12.5}}]
    (testing "Muokkaamaton luettu rivi näyttää palvelimen tallentaman summan"
      (is (== 2500 (sanktion-laskenta/laskettu-tulos tiekm-maaritys luettu-rivi))))
    (testing "Muokattu syöte esikatsellaan profiilin laskentatavalla"
      (is (== 4000 (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote 20))))
      (is (== 246.91 (sanktion-laskenta/laskettu-tulos prosenttiosuus-maaritys {:laskettava-syote 1234.56}))))
    (testing "Virheellisestä tai puuttuvasta syötteestä ei näytetä tulosta"
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote 1.234))))
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys (assoc luettu-rivi :laskettava-syote nil)))))
    (testing "Uusi rivi ilman syötettä ei näytä tulosta"
      (is (nil? (sanktion-laskenta/laskettu-tulos tiekm-maaritys {:summa 500}))
        "Vanha käsin syötetty summa ei ole laskettu tulos"))))

(deftest manuaalinen-laskettavan-sisartyyppi-tunnistetaan-profiilin-rakenteesta
  (let [laskettava {:id 22 :summamaaritykset [tiekm-maaritys]}
        muu-manuaalinen {:id 23 :summamaaritykset []}
        c-manuaalinen-ohjetekstilla {:id 30 :summamaaritykset [{:maaritystapa :manuaalinen
                                                                :summa-euroina 200M
                                                                :ohjeteksti "200,00 € / tiekm."}]}
        lajin-tyypit [laskettava muu-manuaalinen]]
    (is (true? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? muu-manuaalinen lajin-tyypit)))
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? laskettava lajin-tyypit))
      "Laskettava tyyppi ei ole manuaalinen")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? muu-manuaalinen [muu-manuaalinen]))
      "Ilman laskettavaa sisartyyppiä käytös ei muutu")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? c-manuaalinen-ohjetekstilla
                  [laskettava c-manuaalinen-ohjetekstilla]))
      "Profiilin manuaalinen määritys säilyttää nykyisen käytöksen")
    (is (false? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi? nil lajin-tyypit)))))

;; Karakterisointi: summapolut ennen summakontekstin purkua. Testit käyttävät vain julkisia funktioita.
(deftest karakterisointi-kiintea-profiilisumma-ilman-omailmoitusta
  (let [tyyppi {:id 7
                :voi-puolittaa-omailmoituksella true
                :summamaaritykset [{:maaritystapa :automaattinen
                                    :summa-euroina 6000M
                                    :ohjeteksti nil}]}
        rivi (sanktion-lomake-domain/vaihda-sanktiotyyppia
               {:laji :A :tyyppi {:id 1} :omailmoitettu false}
               tyyppi 2025 [] [])]
    (is (= 6000M (:normaalimaara rivi)))
    (is (= 6000M (:summa rivi)))
    (is (false? (:omailmoitettu rivi)))))

(deftest karakterisointi-omailmoitettu-puolitus-vain-kun-profiili-sallii
  (let [sallittu {:id 7
                  :voi-puolittaa-omailmoituksella true
                  :summamaaritykset [{:maaritystapa :automaattinen
                                      :summa-euroina 6000M
                                      :ohjeteksti nil}]}
        ei-sallittu (dissoc sallittu :voi-puolittaa-omailmoituksella)]
    (is (= {:normaalimaara 6000M :omailmoitettu true :summa 3000M}
           (summamaaritys/kiintea-summamalli sallittu true)))
    (is (= {:normaalimaara 6000M :omailmoitettu false :summa 6000M}
           (summamaaritys/kiintea-summamalli ei-sallittu true)))))

(deftest karakterisointi-laskettava-sanktio-ei-kiinteaa-summaa
  (let [laskettava {:id 9
                    :summamaaritykset [{:maaritystapa :laskettu
                                        :laskentatapa :tiekm-yksikkohinta
                                        :laskentaversio 1
                                        :laskentaparametrit {:yksikkohinta 10M}}]}]
    (is (nil? (summamaaritys/kiintea-summamalli laskettava false)))
    (is (false? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? laskettava)))
    (is (some? (sanktion-laskenta/laskettu-summamaaritys laskettava)))))

(deftest karakterisointi-c-ryhman-manuaalinen-summa-ei-ole-kiinteaa
  (let [c-tyyppi {:id 3
                  :summamaaritykset [{:maaritystapa :manuaalinen
                                      :summa-euroina 250M
                                      :ohjeteksti "Kirjaa summa"}]}]
    (is (nil? (summamaaritys/kiintea-summamalli c-tyyppi true)))
    (is (false? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? c-tyyppi)))
    (is (nil? (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa c-tyyppi)))))

(deftest omailmoituksen-asetus-paivittaa-kiintean-summan
  (testing "omailmoitus puolittaa summan normaalimäärästä"
    (is (= {:omailmoitettu true :normaalimaara 14000 :summa 7000}
           (sanktion-lomake-domain/aseta-omailmoitus {:normaalimaara 14000 :summa 14000} true))))
  (testing "omailmoituksen poisto palauttaa normaalimäärän eikä puolita kahdesti"
    (is (= {:omailmoitettu false :normaalimaara 14000 :summa 14000}
           (sanktion-lomake-domain/aseta-omailmoitus {:omailmoitettu true :normaalimaara 14000 :summa 7000} false))))
  (testing "ilman summaa vain omailmoitus asetetaan"
    (is (= {:omailmoitettu true}
           (sanktion-lomake-domain/aseta-omailmoitus {} true)))))

(deftest sanktion-tallennusmuoto-kiintea-ja-laskettava-polku
  (let [kiintea-tyyppi {:id 7
                        :voi-puolittaa-omailmoituksella true
                        :summamaaritykset [{:maaritystapa :automaattinen
                                            :summa-euroina 6000M
                                            :ohjeteksti nil}]}
        laskettava-tyyppi {:id 9
                           :summamaaritykset [{:maaritystapa :laskettu
                                               :laskentatapa :tiekm-yksikkohinta
                                               :laskentaversio 1
                                               :laskentaparametrit {:yksikkohinta 10M}}]}
        lomake {:laji :A :tyyppi {:id 7} :omailmoitettu true :summa 1M}
        kiintea (sanktion-lomake-domain/sanktion-summakonteksti
                  {:laji :A :omailmoitettu true :aktiivinen-tyyppi kiintea-tyyppi})
        laskettava (sanktion-lomake-domain/sanktion-summakonteksti
                     {:laji :A :omailmoitettu false :aktiivinen-tyyppi laskettava-tyyppi})
        tallennettu (sanktion-lomake-domain/sanktion-tallennusmuoto
                      lomake kiintea {:laji :A :tyyppi {:id 7} :omailmoitettu true})]
    (is (= {:normaalimaara 6000M :omailmoitettu true :summa 3000M}
           (select-keys tallennettu [:normaalimaara :omailmoitettu :summa])))
    (let [tallennettu-laskettava (sanktion-lomake-domain/sanktion-tallennusmuoto
                                   {:laji :A :omailmoitettu true :summa 99M
                                    :normaalimaara 5M :laskennan-syote {:syote 42M}}
                                   laskettava
                                   {:laji :A :omailmoitettu true :summa 99M
                                    :normaalimaara 5M :laskennan-syote {:syote 42M}})]
      (is (false? (:omailmoitettu tallennettu-laskettava)))
      (is (not (contains? tallennettu-laskettava :summa)))
      (is (not (contains? tallennettu-laskettava :normaalimaara)))
      (is (not (contains? tallennettu-laskettava :laskennan-syote)))
      (is (= 42M (:laskettava-syote tallennettu-laskettava))))))

(deftest c-ryhman-manuaalinen-maaritys-vaatii-c-lajin-ja-ohjetekstin
  (let [tyyppi {:summamaaritykset [{:maaritystapa :manuaalinen
                                    :summa-euroina 1250M
                                    :ohjeteksti "C-ryhmän viitesumma"}]}
        ilman-ohjetta {:summamaaritykset [{:maaritystapa :manuaalinen
                                           :summa-euroina 1250M}]}]
    (is (= {:summa-euroina 1250M :ohjeteksti "C-ryhmän viitesumma"}
           (summamaaritys/c-ryhman-manuaalinen-maaritys :C tyyppi)))
    (is (nil? (summamaaritys/c-ryhman-manuaalinen-maaritys :A tyyppi)))
    (is (nil? (summamaaritys/c-ryhman-manuaalinen-maaritys :C ilman-ohjetta)))
    (is (nil? (summamaaritys/c-ryhman-manuaalinen-maaritys :C nil)))))

(deftest hoitovuodet-johdetaan-urakan-alku-ja-loppupvm-sta
  (testing "Puuttuva loppupäivä ei aiheuta virhettä"
    (is (= [] (sanktion-lomake-domain/hoitovuodet 2025 nil))))
  (testing "Loppupvm hoitovuoden lopussa (syyskuu) päättää viimeisen hoitovuoden edelliseen alkuvuoteen"
    (is (= [2025 2026 2027 2028 2029]
           (sanktion-lomake-domain/hoitovuodet 2025 (pvm/->pvm "30.9.2030")))))
  (testing "Loppupvm uuden hoitovuoden alussa (lokakuu) sisältää sen hoitovuoden"
    (is (= [2025 2026]
           (sanktion-lomake-domain/hoitovuodet 2025 (pvm/->pvm "1.10.2026")))))
  (testing "Loppu ennen alkua antaa tyhjän"
    (is (= [] (sanktion-lomake-domain/hoitovuodet 2025 (pvm/->pvm "30.9.2025"))))))

(deftest hoitovuosi-tekstina-numeroi-hoitovuoden
  (is (= "1. hoitovuosi (2025 - 2026)" (sanktion-lomake-domain/hoitovuosi-tekstina 2025 2025)))
  (is (= "3. hoitovuosi (2027 - 2028)" (sanktion-lomake-domain/hoitovuosi-tekstina 2025 2027)))
  (is (nil? (sanktion-lomake-domain/hoitovuosi-tekstina 2025 nil))))

(deftest perintapvm-hoitovuodeksi-tunnistaa-hoitovuoden-rajat
  (let [hoitovuodet [2025 2026]]
    (is (= 2025 (sanktion-lomake-domain/perintapvm-hoitovuodeksi hoitovuodet (pvm/->pvm "1.10.2025"))))
    (is (= 2025 (sanktion-lomake-domain/perintapvm-hoitovuodeksi hoitovuodet (pvm/->pvm "30.9.2026"))))
    (is (= 2026 (sanktion-lomake-domain/perintapvm-hoitovuodeksi hoitovuodet (pvm/->pvm "1.10.2026"))))
    (is (nil? (sanktion-lomake-domain/perintapvm-hoitovuodeksi hoitovuodet (pvm/->pvm "1.10.2027"))))
    (is (nil? (sanktion-lomake-domain/perintapvm-hoitovuodeksi hoitovuodet nil)))))

(deftest hoidonjohtopalkkio-tehtavaryhma-loytyy-nimella
  (let [g {:tehtavaryhma 7 :tehtavaryhma_nimi "G - Hoidonjohtopalkkio"}]
    (is (= g (sanktion-lomake-domain/hoidonjohtopalkkio-tehtavaryhma
               [{:tehtavaryhma 1 :tehtavaryhma_nimi "A - Muu"} g])))
    (is (nil? (sanktion-lomake-domain/hoidonjohtopalkkio-tehtavaryhma [])))))

(deftest perusteluton-sanktio-on-vain-laskettava-tyon-tekematta-jattaminen
  (is (true? (sanktiotyyppi-domain/perusteluton-sanktio? :tyon_tekematta_jattaminen {:koodi 22})))
  (is (false? (sanktiotyyppi-domain/perusteluton-sanktio? :tyon_tekematta_jattaminen {:koodi 23})))
  (is (false? (sanktiotyyppi-domain/perusteluton-sanktio? :muistutus {:koodi 22})))
  (is (false? (sanktiotyyppi-domain/perusteluton-sanktio? :tyon_tekematta_jattaminen nil))))

(deftest laskentamaarityksen-virhe-kuvaa-virheen-kentan-nimella
  (let [maaritys {:laskentatapa :tiekm-yksikkohinta
                  :laskentaversio 1
                  :laskentaparametrit {:syoteavain "tiekm" :yksikko "tiekm" :desimaalit 2 :yksikkohinta 200.0}}]
    (is (nil? (sanktion-laskenta/laskentamaarityksen-virhe maaritys)))
    (is (re-find #"laskentatapa.*:tuntematon"
          (sanktion-laskenta/laskentamaarityksen-virhe (assoc maaritys :laskentatapa :tuntematon))))
    (is (re-find #"laskentaversio.*0"
          (sanktion-laskenta/laskentamaarityksen-virhe (assoc maaritys :laskentaversio 0))))
    (is (re-find #"laskentaparametrit.*yksikkohinta"
          (sanktion-laskenta/laskentamaarityksen-virhe
            (update maaritys :laskentaparametrit dissoc :yksikkohinta))))
    (testing "raaka laskentatapa näkyy virheessä"
      (is (re-find #"\"tiekm_typo\""
            (sanktion-laskenta/laskentamaarityksen-virhe
              (assoc maaritys :laskentatapa nil) "tiekm_typo"))))))

(deftest aikakausikonteksti-sisaltaa-hoitovuodet-kun-loppupvm-annetaan
  (testing "Ilman loppupäivää kontekstissa ei ole hoitovuosia"
    (is (not (contains? (sanktion-lomake-domain/aikakausikonteksti 2025 2025 :teiden-hoito) :hoitovuodet))))
  (testing "Loppupäivän kanssa hoitovuodet johdetaan urakan alkuvuodesta"
    (is (= [2025 2026 2027 2028 2029]
           (:hoitovuodet (sanktion-lomake-domain/aikakausikonteksti
                           2025 2025 :teiden-hoito (pvm/->pvm "30.9.2030")))))
    (is (= (dissoc (sanktion-lomake-domain/aikakausikonteksti 2025 2025 :teiden-hoito) :hoitovuodet)
           (dissoc (sanktion-lomake-domain/aikakausikonteksti
                     2025 2025 :teiden-hoito (pvm/->pvm "30.9.2030")) :hoitovuodet)))))

(deftest sallitut-sanktiolajit-poistaa-arvonvahennyksen-kun-sita-ei-sallita
  (let [lajit [:A :B :arvonvahennyssanktio :muistutus]]
    (is (= [:A :B :muistutus]
           (sanktion-lomake-domain/sallitut-sanktiolajit {:salli-arvonvahennys? false} lajit)))
    (is (= lajit
           (sanktion-lomake-domain/sallitut-sanktiolajit {:salli-arvonvahennys? true} lajit)))
    (is (= [] (sanktion-lomake-domain/sallitut-sanktiolajit {:salli-arvonvahennys? false} nil)))))

(deftest lukutila-valitetaan-vain-laatupoikkeaman-sanktiolle-sanktion-tiedoissa
  (testing "Suorasanktion lukutila tulee aina parametrista"
    (is (false? (sanktion-lomake-domain/lukutila? true {:lukutila? true} false)))
    (is (true? (sanktion-lomake-domain/lukutila? true {} true))))
  (testing "Laatupoikkeaman sanktio voi pakottaa lukutilan sanktion tiedoissa"
    (is (true? (sanktion-lomake-domain/lukutila? false {:lukutila? true} false)))
    (is (true? (sanktion-lomake-domain/lukutila? false {} true)))
    (is (false? (sanktion-lomake-domain/lukutila? false {} false)))
    (is (false? (sanktion-lomake-domain/lukutila? nil nil nil)))))

(deftest nakymakonteksti-vaatii-kaikki-avaimet-nil-arvolla-sallittu
  (let [kokonainen (zipmap sanktion-lomake-domain/nakymakontekstin-avaimet (repeat nil))]
    (is (= kokonainen (sanktion-lomake-domain/nakymakonteksti kokonainen)))
    (testing "puuttuva avain nimetään virheessä"
      (let [virhe (try (sanktion-lomake-domain/nakymakonteksti (dissoc kokonainen :lukutila? :lomake))
                    nil
                    (catch clojure.lang.ExceptionInfo e e))]
        (is (some? virhe))
        (is (= #{:lukutila? :lomake} (set (:puuttuvat (ex-data virhe)))))
        (is (re-find #":lomake" (ex-message virhe)))))))

(deftest kokoa-nakymakonteksti-yhdistaa-lomakkeen-paatokset
  (let [tyyppi {:id 7 :koodi 1 :summamaaritykset []}
        syote {:sanktio {:laji :A :tyyppi {:id 7} :suorasanktio true}
               :urakka {:alkupvm (pvm/->pvm "1.10.2025")
                        :loppupvm (pvm/->pvm "30.9.2030")
                        :tyyppi :teiden-hoito
                        :indeksi "MAKU 2015"}
               :hoitokauden-alkuvuosi 2025
               :yllapitourakka? false
               :yllapitokohdeurakka? false
               :vesivaylaurakka? false
               :sanktiolajit [:A :arvonvahennyssanktio]
               :sanktiotyypit [tyyppi {:id 8 :koodi 2}]
               :toimenpideinstanssit [{:tpi_id 1}]
               :kulun-kohdistukset [{:tpi_id 1}]
               :tehtavaryhmat [{:tehtavaryhma_nimi "G - Hoidonjohtopalkkio" :tehtavaryhma 5}]
               :laskutuskuukaudet []
               :laskutuskuukausi-id "laskutuskuukausi-1"
               :indeksi-kaytossa? false
               :lukutila? true
               :voi-muokata? true}
        konteksti (sanktion-lomake-domain/kokoa-nakymakonteksti syote)]
    (testing "sisältää kaikki näkymäkontekstin avaimet"
      (is (every? #(contains? konteksti %) sanktion-lomake-domain/nakymakontekstin-avaimet)))
    (testing "urakan ja tyypin tiedot johdetaan syötteestä"
      (is (= 2025 (:urakan-alkuvuosi konteksti)))
      (is (= "MAKU 2015" (:urakan-indeksi konteksti)))
      (is (= [tyyppi {:id 8 :koodi 2}] (:tyyppi-valinnat konteksti)))
      (is (= 5 (:tehtavaryhma (:hoidonjohtopalkkio-tr konteksti)))))
    (testing "MHU25+ ei salli arvonvähennystä lomakkeella"
      (is (= [:A] (:sanktiolajit konteksti))))
    (testing "suorasanktion lukutila tulee parametrista"
      (is (true? (:lukutila? konteksti)))
      (is (false? (:lukutila? (sanktion-lomake-domain/kokoa-nakymakonteksti
                                (assoc syote :lukutila? false))))))
    (testing "tuntematon sanktiotyyppi ei riko kontekstia"
      (is (nil? (get-in (sanktion-lomake-domain/kokoa-nakymakonteksti
                          (assoc-in syote [:sanktio :tyyppi :id] 99))
                  [:summa :kiintea-summamalli]))))))
