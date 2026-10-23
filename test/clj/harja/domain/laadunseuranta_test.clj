(ns harja.domain.laadunseuranta_test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.test]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]
            [harja.pvm :as pvm]))

(deftest sanktion-lomakekonteksti-palauttaa-kenttasaannot
  (let [aikakausikonteksti (sanktio-domain/aikakausikonteksti 2025 2025 :teiden-hoito)
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
              :salli-arvonvahennys? false}
             (select-keys (sanktio-domain/sanktion-lomakekonteksti perus)
                          [:nayta-tyyppi? :nayta-tapahtumapaikka? :nayta-perustelu?
                           :nayta-puute-tai-laiminlyonti? :salli-arvonvahennys?]))))
    (testing "ylläpitourakka näyttää puute- tai laiminlyöntikentän"
      (is (true? (:nayta-puute-tai-laiminlyonti?
                   (sanktio-domain/sanktion-lomakekonteksti
                     (assoc perus :yllapitourakka? true))))))
    (testing "asiakirjamerkintä piilottaa tyypin ja laskutus ilman laskutuskelpoisuutta tapahtumapaikan"
      (is (false? (:nayta-tyyppi?
                    (sanktio-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :asiakirjamerkintojen_paikkansa_pitamattomyys})))))
      (is (false? (:nayta-tapahtumapaikka?
                    (sanktio-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :laskutus_ilman_laskutuskelpoisuutta}))))))
    (testing "työn tekemättä jättämisen tyyppi 22 piilottaa perustelun"
      (is (false? (:nayta-perustelu?
                    (sanktio-domain/sanktion-lomakekonteksti
                      (assoc perus :sanktio {:laji :tyon_tekematta_jattaminen
                                             :tyyppi {:koodi 22}}))))))
    (testing "talvisuolan ilmoitus ja validointi koskevat talvisuolan ylitystä"
      (let [konteksti (sanktio-domain/sanktion-lomakekonteksti
                        (assoc perus
                          :sanktio {:laji :talvisuolan_ylitys}
                          :urakan-loppupvm (pvm/->pvm "30.9.2026")))]
        (is (true? (:nayta-talvisuola-ilmoitus? konteksti)))
        (is (some? ((:talvisuolan-validointi konteksti)
                    (pvm/->pvm "1.10.2024"))))))
    (testing "aikakausikonteksti vaikuttaa arvonvähennyksen sallimiseen"
      (is (true? (:salli-arvonvahennys?
                   (sanktio-domain/sanktion-lomakekonteksti
                     (assoc perus :aikakausikonteksti
                            (sanktio-domain/aikakausikonteksti 2024 2025 :teiden-hoito)))))))))

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
             (sanktio-domain/aseta-kasittely-tai-maarattypvm
                 perusrivi kasittelypvm :kasittelyaika))))
    (testing "määräyspäivän asetus asettaa käsittelyajan ja puuttuvan perintäpäivän hoitokauden loppuun"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika maarattypvm}}
              :maarattypvm maarattypvm
              :perintapvm (pvm/hoitokauden-loppupvm 2026)}
             (sanktio-domain/aseta-kasittely-tai-maarattypvm
                 perusrivi maarattypvm :maarattypvm))))
    (testing "olemassa oleva perintäpäivä säilyy, kun laskutuskuukausi on valittu"
      (is (= {:laatupoikkeama {:paatos {:kasittelyaika kasittelypvm}}
              :maarattypvm kasittelypvm
              :perintapvm olemassa-oleva-perintapvm
              :laskutuskuukausi-komp-tiedot {:vuosi 2025 :kuukausi 11}}
             (sanktio-domain/aseta-kasittely-tai-maarattypvm
               (assoc perusrivi
                 :perintapvm olemassa-oleva-perintapvm
                 :laskutuskuukausi-komp-tiedot {:vuosi 2025 :kuukausi 11})
               kasittelypvm :kasittelyaika))))
    (testing "käsittelyajan asetus ei aseta perintäpäivää, kun komponenttitiedot ovat olemassa"
      (is (= (assoc komponenttirivi
               :maarattypvm kasittelypvm
               :perintapvm nil
               :laatupoikkeama {:paatos {:kasittelyaika kasittelypvm}})
             (sanktio-domain/aseta-kasittely-tai-maarattypvm
               komponenttirivi kasittelypvm :kasittelyaika))))
    (testing "määräyspäivän asetus ei aseta perintäpäivää, kun komponenttitiedot ovat olemassa"
      (is (= (assoc komponenttirivi
               :maarattypvm maarattypvm
               :perintapvm nil
               :laatupoikkeama {:paatos {:kasittelyaika maarattypvm}})
             (sanktio-domain/aseta-kasittely-tai-maarattypvm
               komponenttirivi maarattypvm :maarattypvm))))))

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
        toimenpideinstanssit [{:tpi_id 303 :toimenpidekoodi "H1"}]
        rivi {:laji :A
              :tyyppi vanha-tyyppi
              :toimenpideinstanssi 101
              :summa 250
              :normaalimaara 500
              :laskettava-syote {:maara 2}
              :laskennan-syote {:maara 2}}]
    (is (= (assoc rivi :laji :B :toimenpideinstanssi 303)
           (sanktio-domain/vaihda-sanktiolajia
             rivi :B [vanha-tyyppi toinen-tyyppi] toimenpideinstanssit)))))

(deftest sanktiolajin-vaihto-esivalitsee-yhden-tyypin-ja-paattelee-toimenpideinstanssin
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi "H2"}
        toimenpideinstanssit [{:tpi_id 202 :toimenpidekoodi "H2"}
                              {:tpi_id 203 :toimenpidekoodi "H3"}]
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi nil}]
    (is (= (assoc rivi
             :laji :muistutus
             :tyyppi tyyppi
             :toimenpideinstanssi 202)
           (sanktio-domain/vaihda-sanktiolajia
             rivi :muistutus [tyyppi] toimenpideinstanssit)))))

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
           (sanktio-domain/vaihda-sanktiolajia
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
           (sanktio-domain/vaihda-sanktiolajia
             rivi :B [] [])))))

(deftest sanktiolajin-vaihto-sailyttaa-vanhan-tyypin-toimenpideinstanssin
  (let [vanha-tyyppi {:id 1 :koodi 13 :toimenpidekoodi "H6"}
        toinen-tyyppi {:id 2 :koodi 14 :toimenpidekoodi "H7"}
        rivi {:laji :A
              :tyyppi vanha-tyyppi
              :toimenpideinstanssi 606}
        toimenpideinstanssit [{:tpi_id 202 :toimenpidekoodi "H6"}]]
    (is (= 202
           (:toimenpideinstanssi
             (sanktio-domain/vaihda-sanktiolajia
               rivi :B [vanha-tyyppi toinen-tyyppi] toimenpideinstanssit))))))

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
           (sanktio-domain/vaihda-sanktiotyyppia
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
           (sanktio-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2024 toimenpideinstanssit kulun-kohdistukset)))))

(deftest sanktiotyypin-vaihto-sailyttaa-nykyisen-toimenpideinstanssin-kun-valintaa-ei-voi-johtaa
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi nil}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi 101}
        toimenpideinstanssit [{:id 11 :tpi_id 101}
                              {:id 22 :tpi_id 202}]]
    (is (= (assoc rivi :tyyppi tyyppi :tpi_id 101)
           (sanktio-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2024 toimenpideinstanssit toimenpideinstanssit)))))

(deftest sanktiotyypin-vaihto-sailyttaa-toimenpideinstanssin-uudemmassa-urakassa
  (let [tyyppi {:id 2 :koodi 17 :toimenpidekoodi 22}
        rivi {:laji :A
              :tyyppi {:id 1}
              :toimenpideinstanssi 101}
        toimenpideinstanssit [{:id 22 :tpi_id 202}]]
    (is (= (assoc rivi :tyyppi tyyppi)
           (sanktio-domain/vaihda-sanktiotyyppia
             rivi tyyppi 2025 toimenpideinstanssit toimenpideinstanssit)))))

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
           (sanktio-domain/vaihda-sanktiotyyppia
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
           (sanktio-domain/vaihda-sanktiotyyppia
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
           (sanktio-domain/vaihda-sanktiotyyppia
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
           (sanktio-domain/vaihda-sanktiotyyppia
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
            (sanktio-domain/aikakausikonteksti 2020 2025 :teiden-hoito)))
          (is (= yleinen
            (sanktio-domain/aikakausikonteksti 2023 2025 :teiden-hoito)))
          (is (= mhu25
            (sanktio-domain/aikakausikonteksti 2025 2025 :teiden-hoito)))
          (is (= mhu26-hoitokausi-2025
            (sanktio-domain/aikakausikonteksti 2026 2025 :teiden-hoito)))
          (is (= muu-urakkatyyppi
            (sanktio-domain/aikakausikonteksti 2025 2025 :hoito)))
          (is (= (assoc muu-urakkatyyppi
              :perinta-tapa :hoitovuosi
              :salli-arvonvahennys? false)
            (sanktio-domain/aikakausikonteksti 2026 2026 :hoito)))))

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
    (is (true? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kiintea)))
    (is (= 4000M (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa kiintea)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? kaava)))
    (is (nil? (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa kaava)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? manuaalinen)))
    (is (nil? (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa manuaalinen)))
    (is (nil? (sanktion-laskenta/laskettu-summamaaritys manuaalinen)))
    (is (= {:maaritystapa :manuaalinen
        :summa-euroina 200M
        :ohjeteksti "Kirjaa summa"}
         (first (:summamaaritykset manuaalinen))))))

(deftest kiintea-normaali-summa-perustuu-aina-alkuperaiseen-summaan
  (testing "Normaali kiinteä summa"
    (is (= 4000M
           (sanktio-domain/kiintea-sanktiosumma 4000M false))))
  (testing "Itse ilmoitettu kiinteä summa"
    (is (= 2000M
           (sanktio-domain/kiintea-sanktiosumma 4000M true))))
  (testing "Itse ilmoitetun summan uudelleen laskeminen ei puolita jo johdettua summaa"
    (is (= 2000M
           (sanktio-domain/kiintea-sanktiosumma 4000M true)
           (sanktio-domain/kiintea-sanktiosumma 4000M true)))))

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
             (sanktio-domain/kiintea-summamalli kiintea false))))
    (testing "Omailmoitus puolittaa normaalimäärästä johdetun summan"
      (is (= {:normaalimaara 14000M :omailmoitettu true :summa 7000M}
             (sanktio-domain/kiintea-summamalli kiintea true))))
    (testing "Toistuva kutsu samoilla arvoilla ei puolita summaa uudelleen"
      (let [ensimmainen (sanktio-domain/kiintea-summamalli kiintea true)
            toinen (sanktio-domain/kiintea-summamalli kiintea true)]
        (is (= 7000M (:summa ensimmainen) (:summa toinen)))
        (is (= ensimmainen toinen))))
    (testing "Omailmoitusta ei sallita, jos profiili ei salli sitä"
      (is (= {:normaalimaara 14000M :omailmoitettu false :summa 14000M}
             (sanktio-domain/kiintea-summamalli ei-puolitettava true))))
    (testing "Manuaalisen määrityksen summaa ei johdeta kiinteäksi"
      (is (nil? (sanktio-domain/kiintea-summamalli manuaalinen true))))
    (testing "Laskettavalle määritykselle kiinteää summamallia ei muodosteta"
      (is (nil? (sanktio-domain/kiintea-summamalli
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
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? laskettu-tyyppi))
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
        rivi (sanktio-domain/vaihda-sanktiotyyppia
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
           (sanktio-domain/kiintea-summamalli sallittu true)))
    (is (= {:normaalimaara 6000M :omailmoitettu false :summa 6000M}
           (sanktio-domain/kiintea-summamalli ei-sallittu true)))))

(deftest karakterisointi-laskettava-sanktio-ei-kiinteaa-summaa
  (let [laskettava {:id 9
                    :summamaaritykset [{:maaritystapa :laskettu
                                        :laskentatapa :tiekm-yksikkohinta
                                        :laskentaversio 1
                                        :laskentaparametrit {:yksikkohinta 10M}}]}]
    (is (nil? (sanktio-domain/kiintea-summamalli laskettava false)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? laskettava)))
    (is (some? (sanktion-laskenta/laskettu-summamaaritys laskettava)))))

(deftest karakterisointi-c-ryhman-manuaalinen-summa-ei-ole-kiinteaa
  (let [c-tyyppi {:id 3
                  :summamaaritykset [{:maaritystapa :manuaalinen
                                      :summa-euroina 250M
                                      :ohjeteksti "Kirjaa summa"}]}]
    (is (nil? (sanktio-domain/kiintea-summamalli c-tyyppi true)))
    (is (false? (sanktio-domain/sanktiotyypilla-kiintea-automaattinen-summamaaritys? c-tyyppi)))
    (is (nil? (sanktio-domain/sanktiotyypin-kiintea-automaattinen-summa c-tyyppi)))))

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
        kiintea (sanktio-domain/sanktion-summakonteksti
                  {:laji :A :omailmoitettu true :aktiivinen-tyyppi kiintea-tyyppi})
        laskettava (sanktio-domain/sanktion-summakonteksti
                     {:laji :A :omailmoitettu false :aktiivinen-tyyppi laskettava-tyyppi})
        tallennettu (sanktio-domain/sanktion-tallennusmuoto
                      lomake kiintea {:laji :A :tyyppi {:id 7} :omailmoitettu true})]
    (is (= {:normaalimaara 6000M :omailmoitettu true :summa 3000M}
           (select-keys tallennettu [:normaalimaara :omailmoitettu :summa])))
    (let [tallennettu-laskettava (sanktio-domain/sanktion-tallennusmuoto
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
