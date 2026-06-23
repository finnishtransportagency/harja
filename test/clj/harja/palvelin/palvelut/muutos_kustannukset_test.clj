(ns harja.palvelin.palvelut.muutos-kustannukset-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.kulut.kustannusten-seuranta :as kustannusten-seuranta]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :db-replica (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])
          :kustannusten-seuranta (component/using
                                   (kustannusten-seuranta/->KustannustenSeuranta)
                                   [:http-palvelin :db :db-replica])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)


(def ^{:private true} +urakka+ (hae-urakan-id-nimella "Iin MHU 2021-2026"))
(def ^{:private true} +tpi+ (hae-toimenpideinstanssi-id +urakka+ "14301"))
(def ^{:private true} +hoitokaudet+ (mapv (fn [vuosi]
                                            [(pvm/hoitokauden-alkupvm vuosi)
                                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))]) (range 2021 2026)))


(defn- hae-muutostyot []
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma) :hae-urakan-muutostyot +kayttaja-jvh+
                  {:urakka-id +urakka+ :valittu-hoitokausi (last +hoitokaudet+)})

        ;; Palauta ensimmäinen erillisrahoitettu muutostyö mikä kannasta löytyy
        erillisrahoitus-muutostyo (first (filter #(= "erillisrahoitus" (:alityyppi %)) vastaus))
        erillisrahoitus-muutostyo-id (:id erillisrahoitus-muutostyo)]

    {:muutostyo erillisrahoitus-muutostyo
     :id erillisrahoitus-muutostyo-id}))


(defn- hae-kustannusten-seuranta [{:keys [urakka hoitokauden-alkuvuosi alkupvm loppupvm]}]
  (kutsu-palvelua
    (:http-palvelin jarjestelma) :urakan-kustannusten-seuranta-paaryhmittain
    +kayttaja-jvh+
    {:urakka-id urakka
     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
     :alkupvm alkupvm
     :loppupvm loppupvm}))


(defn- tarkista-muutos-kulu-on-validi "Tarkistaa että vastaus on validi, ja kulu tallennettiin"
  [uusi-muutos-kulu kulu-erapaiva muutos-voimassa-alkaen]
  (is (= kulu-erapaiva (:erapaiva uusi-muutos-kulu)))
  (is (= 188M (:kokonaissumma uusi-muutos-kulu)))
  (is (= "lokakuu/5-hoitovuosi" (:koontilaskun-kuukausi uusi-muutos-kulu)))
  (is (= nil (:laskun-numero uusi-muutos-kulu)))
  (is (= nil (:lisatieto uusi-muutos-kulu)))

  (let [kohdistus (first (:kohdistukset uusi-muutos-kulu))]
    (is (= :true (:tavoitehintainen kohdistus)))
    (is (= "kokonaishintainen" (:maksueratyyppi kohdistus)))
    (is (= 188M (:summa kohdistus)))
    (is (= muutos-voimassa-alkaen (:muutos-voimassa-alkaen kohdistus)))
    (is (= nil (:tehtavaryhma kohdistus)))
    (is (= nil (:muokkausaika kohdistus)))
    (is (= "erillisrahoitettu-muutos" (:tyyppi kohdistus)))
    (is (= "Erillisrahoitettu sorastusmuutos" (:muutos-nimi kohdistus)))
    (is (= 0 (:rivi kohdistus)))))


(deftest kustannusten-seuranta-toimii-muutoksissa
  (testing "Erillisrahoitettu muutos kulu näkyy kustannusten seurannassa"
    (let [erillisrahoitettu-muutostyo (hae-muutostyot)
          kulu {:kokonaissumma 250,
                :erapaiva (pvm/->pvm "02.10.2025"),
                :kohdistukset [{:rivi 0
                                :summa 250,
                                :tavoitehintainen :true,
                                :valittu-muutostyo (:muutostyo erillisrahoitettu-muutostyo),
                                :lukittu? false,
                                :lisatyo? false,
                                :poistettu false,
                                :toimenpideinstanssi +tpi+,
                                :tyyppi :erillisrahoitettu-muutos}],
                :urakka +urakka+,
                :liitteet [],
                :tyyppi "laskutettava",
                :koontilaskun-kuukausi "lokakuu/5-hoitovuosi"}

          kulu-vastaus (kutsu-palvelua
                         (:http-palvelin jarjestelma)
                         :tallenna-kulu +kayttaja-jvh+
                         {:urakka-id +urakka+
                          :kulu-kohdistuksineen kulu})

          kustannusten-seuranta (hae-kustannusten-seuranta {:urakka +urakka+
                                                            :alkupvm "2025-10-01"
                                                            :loppupvm "2026-09-30"
                                                            :hoitokauden-alkuvuosi 2025})
          erillisrahoitettu-kulu-seurannassa (filter #(= "erillisrahoitettu-muutos" (:kulu_tyyppi %)) kustannusten-seuranta)
          e (first erillisrahoitettu-kulu-seurannassa)]

      ;; Erillisrahoitettu muutos kulu on nyt tallennettu, ja pitäisi näkyä seurannassa
      (is (= 250M (:kokonaissumma kulu-vastaus)) "Kulu tallentui kantaan")

      (is (= "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua."
            (:muutostyo_syy e)) "Syyn pitää näkyä seurannassa")

      (is (= "kokonaishintainen" (:maksutyyppi e)))
      (is (= "erillisrahoitettu-muutos" (:kulu_tyyppi e)))
      (is (= "muutokset" (:paaryhma e)) "Pääryhmä täytyy olla muutokset")
      (is (= "KORVAUSINVESTOINTI" (:tehtava_nimi e)))
      (is (= "toteutunut" (:toteutunut e)))
      (is (= 3000M (:budjetoitu_summa e)))
      (is (= "hankinta" (:toimenpideryhma e)))
      (is (= 3000M (:budjetoitu_summa_indeksikorjattu e)) "Muutoksissa budjetoitu == indeksikorjattu")
      (is (= 250M (:toteutunut_summa e)))
      (is (= "MHU Korvausinvestointi" (:toimenpide e)))
      (is (= "2025-10-02" (str (:ajankohta e))))))


  (testing "Pysyvät muutokset näkyvät kustannusten seurannassa (testidata)"
    (let [kustannusten-seuranta (hae-kustannusten-seuranta {:urakka +urakka+
                                                            :alkupvm "2025-10-01"
                                                            :loppupvm "2026-09-30"
                                                            :hoitokauden-alkuvuosi 2025})
          pysyvat-muutokset-seurannassa (filter #(= "pysyva" (:kulu_tyyppi %)) kustannusten-seuranta)
          v1 (first (filter #(= "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot." (:muutostyo_syy %)) 
                            pysyvat-muutokset-seurannassa))]

      (is (= "pysyva" (:kulu_tyyppi v1)))
      (is (= "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot." (:muutostyo_syy v1)))
      (is (= "muutokset" (:paaryhma v1)))
      (is (= "Pysyvä muutos" (:tehtava_nimi v1)))
      (is (= "toteutunut" (:toteutunut v1)))
      (is (= 1000M (:budjetoitu_summa v1)))
      (is (= "hankinta" (:toimenpideryhma v1)))
      (is (= 1000M (:budjetoitu_summa_indeksikorjattu v1)))
      (is (= 0M (:toteutunut_summa v1)))))


  (testing "Johto- ja hallinto muutokset näkyvät kustannusten seurannassa (testidata)"
    (let [kustannusten-seuranta (hae-kustannusten-seuranta {:urakka +urakka+
                                                            :alkupvm "2025-10-01"
                                                            :loppupvm "2026-09-30"
                                                            :hoitokauden-alkuvuosi 2025})
          pysyvat-muutokset-seurannassa (filter #(= "jjh-muutos" (:kulu_tyyppi %)) kustannusten-seuranta)
          v1 (first pysyvat-muutokset-seurannassa)]

      (is (= "jjh-muutos" (:kulu_tyyppi v1)))
      (is (= "Työmääräarviot ylittyivät" (:muutostyo_syy v1)))
      (is (= "muutokset" (:paaryhma v1)))
      (is (= "J - Johto- ja hallintokorvaus" (:tehtava_nimi v1)))
      (is (= "toteutunut" (:toteutunut v1)))
      (is (= 1230M (:budjetoitu_summa v1)))
      (is (= "hankinta" (:toimenpideryhma v1)))
      (is (= 1230M (:budjetoitu_summa_indeksikorjattu v1)))
      (is (= 1230M (:toteutunut_summa v1))))))


(deftest muutos-kulun-tallennus-sekä-validointi-toimii
  (let [erillisrahoitettu-muutostyo (hae-muutostyot)
        erillisrahoitettu-muutostyo (assoc erillisrahoitettu-muutostyo :tavoitehinnan-muutos 10000M)
        ;; Kulu joka tallennetaan kantaan
        uusi-muutos-kulu {:kokonaissumma 188,
                          :kohdistukset [{:rivi 0
                                          :summa 188,
                                          :tavoitehintainen :true,
                                          :valittu-muutostyo (:muutostyo erillisrahoitettu-muutostyo),
                                          :lukittu? false,
                                          :lisatyo? false,
                                          :poistettu false,
                                          :toimenpideinstanssi +tpi+,
                                          :tyyppi :erillisrahoitettu-muutos}],
                          :urakka +urakka+,
                          :liitteet [],
                          :tyyppi "laskutettava",
                          :koontilaskun-kuukausi "lokakuu/5-hoitovuosi"}

        paivita-erapaivat-fn (fn [muutostyo-voimassa kulu-erapaiva]
                               (let [_ (i (format
                                            "UPDATE mhu_muutos SET voimassa_alkaen = '%s' WHERE id = %s;"
                                            muutostyo-voimassa (:id erillisrahoitettu-muutostyo)))
                                     ;; Hae päivitetyt muutostyöt
                                     paivitetty-muutostyo (hae-muutostyot)]
                                 ;; Assoccaa eräpäivät, sekä päivitetty muutostyö uuteen kuluun
                                 (-> uusi-muutos-kulu
                                   (assoc :erapaiva kulu-erapaiva)
                                   (update :kohdistukset #(assoc-in % [0 :valittu-muutostyo] (:muutostyo paivitetty-muutostyo))))))]


    (testing "Muutostyön kulun kirjaus toimii, sama voimassa alkaen ja eräpäivä -> OK"
      (let [muutostyo-voimassa "2025-10-02" ;; <- sama kun eräpäivä
            kulu-erapaiva (pvm/->pvm "02.10.2025") ;; <- sama kun voimassa
            muutos-voimassa-inst (pvm/->pvm "02.10.2025")
            uusi-muutos-kulu (paivita-erapaivat-fn muutostyo-voimassa kulu-erapaiva)
            vastaus (kutsu-palvelua
                      (:http-palvelin jarjestelma)
                      :tallenna-kulu +kayttaja-jvh+
                      {:urakka-id +urakka+
                       :kulu-kohdistuksineen uusi-muutos-kulu})]
        ;; Tarkista, että kulu palautuu validina
        (tarkista-muutos-kulu-on-validi vastaus kulu-erapaiva muutos-voimassa-inst)))


    (testing "Muutostyön kulun kirjaus toimii, eräpäivä on voimassa alkaen jälkeen -> OK"
      (let [muutostyo-voimassa "2025-10-01"
            muutos-voimassa-inst (pvm/->pvm "01.10.2025")
            kulu-erapaiva (pvm/->pvm "03.10.2025") ;; <- voimassa alkaen jälkeen - OK
            uusi-muutos-kulu (paivita-erapaivat-fn muutostyo-voimassa kulu-erapaiva)
            vastaus (kutsu-palvelua
                      (:http-palvelin jarjestelma)
                      :tallenna-kulu +kayttaja-jvh+
                      {:urakka-id +urakka+
                       :kulu-kohdistuksineen uusi-muutos-kulu})]
        ;; Tarkista, että kulu palautuu validina
        (tarkista-muutos-kulu-on-validi vastaus kulu-erapaiva muutos-voimassa-inst)))


    (testing "Muutostyön kulun kirjaus heittää virheen, eräpäivä on voimassa alkaen ennen -> VIRHE"
      (let [muutostyo-voimassa "2025-10-03"
            kulu-erapaiva (pvm/->pvm "01.10.2025") ;; <- voimassa alkaen ennen -> ERROR
            uusi-muutos-kulu (paivita-erapaivat-fn muutostyo-voimassa kulu-erapaiva)
            odotettu-poikkeus "Tallennus epäonnistui. Muutostyö ei ole voimassa laskun päivämääränä."

            ;; Ei pitäisi mennä läpi
            vastaus (try
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-kulu +kayttaja-jvh+
                        {:urakka-id +urakka+
                         :kulu-kohdistuksineen uusi-muutos-kulu})
                      (catch Exception e e))]

        (is
          (true? (str/includes? vastaus odotettu-poikkeus))
          "Odotettu virhe heitetään, 
           eräpäivä ei voi olla ennenkuin muutostyö on voimassa")))


    (testing "Muutostyön kulun kirjaus heittää virheen, lasku ei osu muutostyön hoitokaudelle"
      (let [muutostyo-voimassa "2025-10-01"
            kulu-erapaiva (pvm/->pvm "01.10.2025")
            uusi-muutos-kulu (paivita-erapaivat-fn muutostyo-voimassa kulu-erapaiva)

            ;; Aseta muutostyölle aikaisemman hoitokauden voimassa alkaen
            vanha-voimassa-alkaen (pvm/->pvm "01.10.2023") ;; <- erillä hoitokaudella -> ERROR
            uusi-muutos-kulu (-> uusi-muutos-kulu
                               (assoc :erapaiva kulu-erapaiva)
                               (update :kohdistukset #(assoc-in % [0 :valittu-muutostyo :voimassa_alkaen] vanha-voimassa-alkaen)))

            odotettu-poikkeus "Tallennus epäonnistui. Muutostyö ei ole voimassa laskun päivämääränä."
            ;; Ei pitäisi mennä läpi, muutostyö on hoitokausikohtainen
            vastaus (try
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-kulu +kayttaja-jvh+ {:urakka-id +urakka+
                                                       :kulu-kohdistuksineen uusi-muutos-kulu})
                      (catch Exception e e))]
        (is
          (true? (str/includes? vastaus odotettu-poikkeus))
          "Odotettu virhe heitetään, 
           eräpäivä ei voi olla erillä hoitokaudella muutostyön voimassaolosta")))


    (testing "Muutostyön kulun kirjaus heittää virheen, budjetti ylittyy"
      (let [odotettu-poikkeus "Tallennus epäonnistui. Erillisrahoitetun muutostyön budjetti ylittyy"
            kulu-erapaiva (pvm/->pvm "03.10.2025")
            uusi-muutos-kulu (paivita-erapaivat-fn "2025-10-01" kulu-erapaiva)

            uusi-muutos-kulu (-> uusi-muutos-kulu
                               ;; Laita summaksi yli budjetin (budjetti = tavoitehinnan muutos)
                               (assoc :kokonaissumma 100000M)
                               (update :kohdistukset #(assoc-in % [0 :summa] 100000M)))

            vastaus (try
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-kulu +kayttaja-jvh+
                        {:urakka-id +urakka+
                         :kulu-kohdistuksineen uusi-muutos-kulu})
                      (catch Exception e e))]
        (is
          (true? (str/includes? vastaus odotettu-poikkeus))
          "Odotettu virhe heitetään, 
           erillisrahoitetun muutostyön budjetti ylittyy")))


    (testing "Tallennus validointi toimii, kun kuluja on jo kirjattu (erillisrahoitus)"
      (let [virheellinen-voimassa-alkaen (pvm/->pvm "11.11.2025")
            odotettu-poikkeus "Muutostyölle on jo kirjattu kuluja ennen 11.11.2025"
            muutos erillisrahoitettu-muutostyo
            muutos (assoc muutos
                     :tyyppi "muutostyo"
                     :alityyppi :erillisrahoitus
                     :voimassa_alkaen virheellinen-voimassa-alkaen)

            payload {:muutos muutos
                     :urakka-id +urakka+
                     :hoitokaudet +hoitokaudet+
                     :valittu-hoitokausi (last +hoitokaudet+)}

            vastaus (try
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-muutos +kayttaja-jvh+ payload)
                      (catch Exception e e))]
        (is
          (true? (str/includes? vastaus odotettu-poikkeus))
          "Odotettu virhe heitetään, 
           voimassa ennen ei voi asettaa kirjattujen kulujen eräpäivien jälkeen")))


    (testing "Tallennus validointi toimii, budjetti ylittyy (erillisrahoitus)"
      (let [virheellinen-voimassa-alkaen (pvm/->pvm "11.11.2025")
            odotettu-poikkeus "Muutostyön budjetti ylittyy."

            muutos erillisrahoitettu-muutostyo
            muutos (assoc muutos
                     :tyyppi "muutostyo"
                     :alityyppi :erillisrahoitus
                     :tavoitehinnan-muutos 0M ;; Ei voi olla 0, kun kuluja on jo kirjattu
                     :voimassa_alkaen virheellinen-voimassa-alkaen)

            payload {:muutos muutos
                     :urakka-id +urakka+
                     :hoitokaudet +hoitokaudet+
                     :valittu-hoitokausi (last +hoitokaudet+)}

            vastaus (try
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-muutos +kayttaja-jvh+ payload)
                      (catch Exception e e))]
        (is
          (true? (str/includes? vastaus odotettu-poikkeus))
          "Odotettu virhe heitetään, 
           tavoitehinnan muutos ei voi olla 0, kun kuluja on jo kirjattu")))))
