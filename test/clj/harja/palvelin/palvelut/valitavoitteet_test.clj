(ns harja.palvelin.palvelut.valitavoitteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valitavoitteet :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :as testi]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                 (->Valitavoitteet)
                 [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each jarjestelma-fixture)

(deftest urakan-valitavoitteiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-valitavoitteet +kayttaja-jvh+ (hae-oulun-alueurakan-2014-2019-id))]

    ;(log/debug vastaus)
    (is (>= (count vastaus) 4)))

  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-valitavoitteet +kayttaja-jvh+ (hae-urakan-id-nimella "Muhoksen päällystysurakka"))]

    (is (some :yllapitokohde-id vastaus)
      "Ainakin yksi on liitetty ylläpitokohteeseen")
    (is (>= (count vastaus) 4))))

(deftest urakkakohtaisen-valitavoitteen-tallentaminen-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        valitavoitteet [{:nimi "testi566", :takaraja (c/to-date (t/now)),
                         :aloituspvm (c/to-date (t/now))
                         :yllapitokohde-id yllapitokohde-id
                         :valmispvm (c/to-date (t/now)), :valmis-kommentti "valmis!"}
                        {:nimi "testi34554", :takaraja (c/to-date (t/now)),
                         :valmispvm (c/to-date (t/now)), :valmis-kommentti "valmis tämäkin!"}
                        {:nimi "melko tyhjä vt", :takaraja nil,
                         :valmispvm nil, :valmis-kommentti nil}]
        vt-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-urakan-valitavoitteet +kayttaja-jvh+
                          urakka-id)
        _ (kutsu-palvelua
            (:http-palvelin jarjestelma)
            :tallenna-urakan-valitavoitteet
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valitavoitteet valitavoitteet})
        vt-lisayksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-urakan-valitavoitteet +kayttaja-jvh+
                               urakka-id)]

    ;; Määrä lisääntyi oikein
    (is (= (+ (count vt-ennen-testia) 3)
          (count vt-lisayksen-jalkeen)))

    ;; Tiedot tallentuivat oikein
    (let [vt1 (first (filter #(= (:nimi %) "testi566") vt-lisayksen-jalkeen))
          vt2 (first (filter #(= (:nimi %) "testi34554") vt-lisayksen-jalkeen))
          vt3 (first (filter #(= (:nimi %) "melko tyhjä vt") vt-lisayksen-jalkeen))]
      (is vt1)
      (is vt2)
      (is vt3)

      ;; VT1 tallentui oikein
      (is (some? (:valmis-merkitsija vt1)))
      (is (some? (:aloituspvm vt1)))
      (is (some? (:valmispvm vt1)))
      (is (= (:yllapitokohde-id vt1) yllapitokohde-id))
      (is (nil? (:valtakunnallinen-id vt1)))
      (is (= (:urakka-id vt1) urakka-id))
      (is (some? (:takaraja vt1)))
      (is (= (:valmis-kommentti vt1) "valmis!"))

      ;; VT2 tallentui oikein
      (is (some? (:valmis-merkitsija vt2)))
      (is (some? (:valmispvm vt2)))
      (is (nil? (:valtakunnallinen-id vt2)))
      (is (= (:urakka-id vt2) urakka-id))
      (is (some? (:takaraja vt2)))
      (is (= (:valmis-kommentti vt2) "valmis tämäkin!"))

      ;; VT3 tallentui oikein
      (is (some? (:valmis-merkitsija vt3)))
      (is (nil? (:valmispvm vt3)))
      (is (nil? (:valtakunnallinen-id vt3)))
      (is (= (:urakka-id vt3) urakka-id))
      (is (nil? (:takaraja vt3)))
      (is (nil? (:valmis-kommentti vt3))))

    ;; Päivitys toimii
    (let [paivitetty-yllapitokohde (hae-yllapitokohteen-id-nimella "Oulun ohitusramppi")
          muokattu-vt (->> vt-lisayksen-jalkeen
                        (filter #(or (= (:nimi %) "testi566")
                                   (= (:nimi %) "testi34554")))
                        (mapv #(if (= (:nimi %) "testi566")
                                 (assoc % :valmis-kommentti "hyvin tehty"
                                   :yllapitokohde-id paivitetty-yllapitokohde)
                                 %)))
          _ (kutsu-palvelua
              (:http-palvelin jarjestelma)
              :tallenna-urakan-valitavoitteet
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :valitavoitteet muokattu-vt})
          vt-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-urakan-valitavoitteet +kayttaja-jvh+
                                   urakka-id)]

      ;; Määrä edelleen sama oikein
      (is (= (count vt-lisayksen-jalkeen)
            (count vt-paivityksen-jalkeen)))

      ;; VT1 päivittyi oikein
      (let [vt1 (first (filter #(= (:nimi %) "testi566") vt-paivityksen-jalkeen))]
        (is (= (:valmis-kommentti vt1) "hyvin tehty"))
        (is (= (:yllapitokohde-id vt1) paivitetty-yllapitokohde))))


    ;; Siivoa sotkut
    (u "DELETE FROM valitavoite WHERE nimi = 'testi566' OR nimi = '34554';")))

(deftest urakkakohtaisen-valitavoitteen-tallentaminen-epaonnistuu-virheelliseen-kohteeseen
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        valitavoitteet [{:nimi "Oops...", :takaraja (c/to-date (t/now)),
                         :aloituspvm (c/to-date (t/now))
                         :yllapitokohde-id yllapitokohde-id
                         :valmispvm (c/to-date (t/now)), :valmis-kommentti "valmis!"}]]
    (is (thrown? SecurityException
          (kutsu-palvelua
            (:http-palvelin jarjestelma)
            :tallenna-urakan-valitavoitteet
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valitavoitteet valitavoitteet}))
      "Ei voi lisätä kohdetta, joka ei kuulu urakkaan")))

(deftest urakkakohtaisen-valitavoitteen-tallentaminen-ei-toimi-ilman-oikeuksia
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        valitavoitteet [{:nimi "testi566", :takaraja (c/to-date (t/now)),
                         :valmispvm (c/to-date (t/now)), :valmis-kommentti "valmis!"}
                        {:nimi "testi34554", :takaraja (c/to-date (t/now)),
                         :valmispvm (c/to-date (t/now)), :valmis-kommentti "valmis tämäkin!"}]]
    (is (thrown? Exception (kutsu-palvelua
                             (:http-palvelin jarjestelma)
                             :tallenna-urakan-valitavoitteet
                             +kayttaja-ulle+
                             {:urakka-id urakka-id
                              :valitavoitteet valitavoitteet})))))

(deftest toistuvan-valtakunnallisen-valitavoitteen-lisaaminen-toimii
  (let [rovaniemen-urakan-vanhat-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                  :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                  (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))
        lisatyt-valtakunnalliset (kutsu-palvelua
                                   (:http-palvelin jarjestelma)
                                   :tallenna-valtakunnalliset-valitavoitteet
                                   +kayttaja-jvh+
                                   {:valitavoitteet [{:id -5, :nimi "Sepon mökkitien vuosittainen auraus",
                                                      :takaraja nil, :tyyppi :toistuva,
                                                      :urakkatyyppi :hoito, ;; Välitavoitteissa vain tyyppi hoito. Tavoitteet kopioituvat myös teiden hoidon urakoille.
                                                      :takaraja-toistopaiva 1,
                                                      :takaraja-toistokuukausi 7}]})
        rovaniemen-urakan-paivitetyt-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                      (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))
        odotetut-toistovuodet (range (t/year (t/now)) (+ (t/year (t/now)) 5))]

    ;; Rovaniemen urakassa ei entuudestaan ole välitavoitteita.
    ;; Rovaniemen urakan jäljellä oleville vuosille (5) luotiin uusi välitavoite.
    (is (= (count rovaniemen-urakan-paivitetyt-valitavoitteet)
          (-> (count rovaniemen-urakan-vanhat-valitavoitteet)
            (+ (count odotetut-toistovuodet)))))
    (is (not (empty? odotetut-toistovuodet))) ;; Urakka päättynyt, päivitä testi

    (u (str "DELETE FROM valitavoite WHERE valtakunnallinen_valitavoite IS NOT NULL"))
    (u (str "DELETE FROM valitavoite WHERE urakka IS NULL"))))

(def valtakunnallinen-valitavoite-hoitokauden-lopussa
  [{:id -99 :nimi "Pyyhi pölyt ja sammuta valot hoitokauden lopussa", :takaraja nil, :tyyppi :toistuva, :urakkatyyppi :hoito, :takaraja-toistopaiva 30, :takaraja-toistokuukausi 9}])

(deftest valtakunnallinen-valitavoite-hoitokauden-viimeiselle-paivalle
  (let [raahen-mhu-urakan-id (hae-urakan-id-nimella "Raahen MHU 2023-2028")
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :tallenna-valtakunnalliset-valitavoitteet
                  +kayttaja-jvh+
                  {:valitavoitteet valtakunnallinen-valitavoite-hoitokauden-lopussa})
        ei-luoda-urakan-ulkopuolelle (first (q (str "SELECT takaraja, nimi from valitavoite where nimi = 'Pyyhi pölyt ja sammuta valot hoitokauden lopussa' AND takaraja = '2023-09-30' AND urakka = " raahen-mhu-urakan-id ";")))
        raahen-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-valitavoitteet +kayttaja-jvh+
                                raahen-mhu-urakan-id)
        tallennetut (filter #(and
                               (= raahen-mhu-urakan-id (:urakka-id %))
                               (= (:nimi %) "Pyyhi pölyt ja sammuta valot hoitokauden lopussa")) raahen-valitavoitteet)]
    (is (empty? ei-luoda-urakan-ulkopuolelle) "Ei saa edes luoda urakan ulkopuolelle")
    (is (= 3 (count tallennetut)) "Viidelle hoitokaudelle replikoitu")
    (is (nil? (some #(= (:takaraja %)
                       (pvm/->pvm "30.9.2023")) raahen-valitavoitteet)) "2023 tiedot oikein")
    (is (nil? (some #(= (:takaraja %)
                       (pvm/->pvm "30.9.2024")) raahen-valitavoitteet)) "2024 tiedot oikein")
    (is (nil? (some #(= (:takaraja %)
                        (pvm/->pvm "30.9.2025")) raahen-valitavoitteet)) "2025 tiedot oikein")
    (is (some? (some #(= (:takaraja %)
                        (pvm/->pvm "30.9.2026")) raahen-valitavoitteet)) "2026 tiedot oikein")
    (is (some? (some #(= (:takaraja %)
                        (pvm/->pvm "30.9.2027")) raahen-valitavoitteet)) "2027 tiedot oikein")
    (is (some? (some #(= (:takaraja %)
                        (pvm/->pvm "30.9.2028")) raahen-valitavoitteet)) "2028 tiedot oikein"))


  (u "DELETE from valitavoite where nimi = 'Pyyhi pölyt ja sammuta valot hoitokauden lopussa'"))

(deftest valtakunnallisten-valitavoitteiden-kasittely-toimii
  (let [rovaniemen-urakan-vanhat-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                  :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                  (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))
        utajarven-urakan-vanhat-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                 (hae-urakan-id-nimella "Utajärven päällystysurakka"))
        lisatyt-valtakunnalliset
        (kutsu-palvelua
          (:http-palvelin jarjestelma)
          :tallenna-valtakunnalliset-valitavoitteet
          +kayttaja-jvh+
          {:valitavoitteet [{:id -2, :nimi "Kertaluontoinen",
                             :takaraja (c/to-date (t/plus (t/now) (t/years 5))),
                             :tyyppi :kertaluontoinen, :urakkatyyppi :hoito,
                             :takaraja-toistopaiva nil, :takaraja-toistokuukausi nil}
                            {:id -5, :nimi "Sepon mökkitien vuosittainen auraus",
                             :takaraja nil, :tyyppi :toistuva,
                             :urakkatyyppi :hoito, :takaraja-toistopaiva 1,
                             :takaraja-toistokuukausi 7}]})
        rovaniemen-urakan-paivitetyt-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                      (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))
        utajarven-urakan-paivitetyt-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                     (hae-urakan-id-nimella "Utajärven päällystysurakka"))]

    ;; Uudet valtakunnalliset lisätty ok
    (is (= (count lisatyt-valtakunnalliset) 2))
    (is (= (count (filter #(= (:tyyppi %) :kertaluontoinen) lisatyt-valtakunnalliset)) 1))
    (is (= (count (filter #(= (:tyyppi %) :toistuva) lisatyt-valtakunnalliset)) 1))

    ;; Oulun hoidon urakalle tuli lisää välitavoitteita
    (is (> (count rovaniemen-urakan-paivitetyt-valitavoitteet) (count rovaniemen-urakan-vanhat-valitavoitteet)))
    (is (some :valtakunnallinen-id rovaniemen-urakan-paivitetyt-valitavoitteet))
    ;; Muhokselle ei tullut, koska oli eri urakkatyyppi
    (is (= (count utajarven-urakan-paivitetyt-valitavoitteet) (count utajarven-urakan-vanhat-valitavoitteet)))

    ;; Päivitä urakkakohtaista tavoitetta ja sen jälkeen valtakunnallista
    (let [random-tavoite-id-urakassa (first (first (q (str
                                                        "SELECT id FROM valitavoite
                                                         WHERE urakka = " (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
                                                        " AND valtakunnallinen_valitavoite IS NOT NULL
                                                        AND poistettu IS NOT TRUE
                                                        LIMIT 1;"))))
          _ (is (integer? random-tavoite-id-urakassa))
          _ (u (str "UPDATE valitavoite set muokattu = NOW() WHERE id = " random-tavoite-id-urakassa))
          paivitetyt-valtakunnalliset
          (kutsu-palvelua
            (:http-palvelin jarjestelma)
            :tallenna-valtakunnalliset-valitavoitteet
            +kayttaja-jvh+
            {:valitavoitteet (mapv
                               #(assoc % :nimi "PÄIVITÄ")
                               lisatyt-valtakunnalliset)})
          rovaniemen-urakan-paivitetyt-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                        (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))]
      ;; Ei muokatut välitavoitteet päivittyivät myös urakkaan
      (is (every? #(= (:nimi %) "PÄIVITÄ")
            (filter #(and (:valtakunnallinen-id %)
                       (nil? (:muokattu %)))
              rovaniemen-urakan-paivitetyt-valitavoitteet)))

      ;; Muokatut välitavoitteet eivät päivittyneet
      (is (every? #(not= (:nimi %) "PÄIVITÄ")
            (filter #(and (:valtakunnallinen-id %)
                       (some? (:muokattu %)))
              rovaniemen-urakan-paivitetyt-valitavoitteet)))

      ;; Kaikkien linkitettyjen välitavoitteiden "emo" näkyy kuitenkin päivitettynä
      (is (every? #(= (:valtakunnallinen-nimi %) "PÄIVITÄ")
            (filter :valtakunnallinen-id rovaniemen-urakan-paivitetyt-valitavoitteet)))

      ;; Poistetaan valtakunnalliset välitavoitteet (mutta ei valmiita)
      (let [random-tavoite-id-urakassa (first (first (q (str
                                                          "SELECT id FROM valitavoite
                                                           WHERE urakka = " (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
                                                          " AND valtakunnallinen_valitavoite IS NOT NULL
                                                          AND poistettu IS NOT TRUE
                                                          LIMIT 1;"))))
            _ (is (integer? random-tavoite-id-urakassa))
            _ (u (str "UPDATE valitavoite set valmis_pvm = NOW() WHERE id = " random-tavoite-id-urakassa))
            poistetut-valtakunnalliset (kutsu-palvelua
                                         (:http-palvelin jarjestelma)
                                         :tallenna-valtakunnalliset-valitavoitteet
                                         +kayttaja-jvh+
                                         {:valitavoitteet (mapv
                                                            #(assoc % :poistettu true)
                                                            paivitetyt-valtakunnalliset)})
            rovaniemen-urakan-poistetut-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                         (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)"))
            utajarven-urakan-poistetut-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                        (hae-urakan-id-nimella "Utajärven päällystysurakka"))]
        ;; R.I.P valtakunnalliset välitavoitteet
        (is (empty? poistetut-valtakunnalliset))
        ;; Muokattu välitavoite säilyi Oulun urakassa
        (is (= (count (filter :valtakunnallinen-id rovaniemen-urakan-poistetut-valitavoitteet)) 1))
        ;; Muhoksen urakassa ei valtakunnallisia tavoitteita koskaan ollutkaan, eikä ole vieläkään
        (is (empty? (filter :valtakunnallinen-id utajarven-urakan-poistetut-valitavoitteet)))

        (u (str "DELETE FROM valitavoite WHERE valtakunnallinen_valitavoite IS NOT NULL"))
        (u (str "DELETE FROM valitavoite WHERE urakka IS NULL"))))))

(deftest valtakunnallisten-valitavoitteiden-valmispvm-voi-poistaa
  (let [muhoksen-urakan-vanhat-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                (hae-urakan-id-nimella "Muhoksen päällystysurakka"))
        rivi (first (filter #(= "Se iso kivi siirretty pois tieltä" (:nimi %)) muhoksen-urakan-vanhat-valitavoitteet))
        urakka-id (ffirst (q (str "SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Muhoksen päällystysurakka'")))

        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :tallenna-urakan-valitavoitteet
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :valitavoitteet [(assoc rivi :valmispvm nil)]})
        muhoksen-urakan-paivitetyt-valitavoitteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :hae-urakan-valitavoitteet +kayttaja-jvh+
                                                    (hae-urakan-id-nimella "Muhoksen päällystysurakka"))]
    (is (nil? (:valmispvm (first (filter #(= "Se iso kivi siirretty pois tieltä" (:nimi %)) muhoksen-urakan-paivitetyt-valitavoitteet)))))))

(deftest valitavoitteiden-kopiointi-tuleville-vuosille-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        _ (u (str "DELETE FROM valitavoite WHERE urakka = " urakka-id ";"))
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2021 2026))

        tallenna-fn (fn [params]
                      (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :tallenna-urakan-valitavoitteet +kayttaja-jvh+
                        params))

        hae-fn (fn [urakka]
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                   :hae-urakan-valitavoitteet +kayttaja-jvh+
                   urakka))

        pvm-2023 #inst "2023-10-30T21:00:00.000-00:00"
        pvm-2022 #inst "2022-10-30T21:00:00.000-00:00"
        valitavoitteet [;; Kopioidaan nämä tuleville hoitovuosille
                        {:nimi "iin 23 kopioitava tavoite 1",
                         :takaraja pvm-2023,
                         :aloituspvm nil
                         :yllapitokohde-id nil
                         :valmispvm nil,
                         :valmis-kommentti nil}
                        {:nimi "iin 23 kopioitava tavoite 2",
                         :takaraja pvm-2023,
                         :aloituspvm nil
                         :yllapitokohde-id nil
                         :valmispvm nil,
                         :valmis-kommentti nil}

                        ;; Tämän pitäisi jäädä ennalleen kopioinnin jälkeen 
                        {:nimi "iin 22 tavoite - ei kosketa",
                         :takaraja pvm-2022,
                         :aloituspvm nil
                         :yllapitokohde-id nil
                         :valmispvm nil,
                         :valmis-kommentti nil}]]


    (testing "Välitavoitteen tallennus ennen kopiointia Iin mhu"
      (let [vastaus (hae-fn urakka-id)
            _ (is (empty? vastaus) "Iin välitavoitteita ei ole")

            _ (tallenna-fn {:urakka-id urakka-id :valitavoitteet valitavoitteet})
            vastaus (hae-fn urakka-id)
            _ (is (= (count vastaus) 3) "Iin välitavoitteet lisättiin")

            tallennetut-21 (filter #(= (pvm/vuosi (:takaraja %)) 2021) vastaus)
            tallennetut-22 (filter #(= (pvm/vuosi (:takaraja %)) 2022) vastaus)
            tallennetut-23 (filter #(= (pvm/vuosi (:takaraja %)) 2023) vastaus)
            tallennetut-24 (filter #(= (pvm/vuosi (:takaraja %)) 2024) vastaus)
            _ (is (= (count tallennetut-21) 0) "2021 välitavoitteita on 0 kpl")
            _ (is (= (count tallennetut-22) 1) "2022 välitavoitteita on 1 kpl - Ei kosketa")
            _ (is (= (count tallennetut-23) 2) "2023 välitavoitteita on 2 kpl - Kopioitavat")
            _ (is (= (count tallennetut-24) 0) "2024 välitavoitteita on 0 kpl - Ei vielä kopioitu")

            _ (is (=
                    #{"iin 23 kopioitava tavoite 1"
                      "iin 23 kopioitava tavoite 2"}
                    (set (map :nimi tallennetut-23))) "Tallennetut nimet ovat kannassa")]))


    (testing "Välitavoitteiden kopiointi toimii"
      (let [hoitokausi-23 (nth hoitokaudet 2)
            params {:urakka-id urakka-id
                    :hoitokaudet hoitokaudet
                    :valittu-hoitokausi hoitokausi-23}

            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                :kopioi-urakan-valitavoitteet-tuleville-hk +kayttaja-jvh+
                params)

            vastaus (hae-fn urakka-id)
            tallennetut-21 (filter #(= (pvm/vuosi (:takaraja %)) 2021) vastaus)
            tallennetut-22 (filter #(= (pvm/vuosi (:takaraja %)) 2022) vastaus)
            tallennetut-23 (filter #(= (pvm/vuosi (:takaraja %)) 2023) vastaus)
            tallennetut-24 (filter #(= (pvm/vuosi (:takaraja %)) 2024) vastaus)
            tallennetut-25 (filter #(= (pvm/vuosi (:takaraja %)) 2025) vastaus)
            _ (is (= (count tallennetut-21) 0) "2021 välitavoitteita on 0 kpl")
            _ (is (= (count tallennetut-22) 1) "2022 välitavoitteita on 1 kpl - Ei kosketa")
            _ (is (= (count tallennetut-23) 2) "2023 välitavoitteita on 2 kpl - Kopioitavat")
            _ (is (= (count tallennetut-24) 2) "2024 välitavoitteita on 2 kpl - Kopioitu")
            _ (is (= (count tallennetut-25) 2) "2025 välitavoitteita on 2 kpl - Kopioitu")
            _ (is (= (count vastaus) 7) "Iin 23 välitavoitteet kopioitiin")

            _ (is (=
                    #{"iin 23 kopioitava tavoite 1"
                      "iin 23 kopioitava tavoite 2"}
                    (set (map :nimi tallennetut-24))) "Välitavoitteet kopioitiin 24 vuodelle")

            _ (is (=
                    #{"iin 23 kopioitava tavoite 1"
                      "iin 23 kopioitava tavoite 2"}
                    (set (map :nimi tallennetut-25))) "Välitavoitteet kopioitiin 25 vuodelle")]))))
