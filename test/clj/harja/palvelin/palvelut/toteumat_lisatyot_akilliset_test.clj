(ns harja.palvelin.palvelut.toteumat-lisatyot-akilliset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.tehtavamaarat :as tehtavamaarat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                        (component/system-map
                          :db tietokanta
                          :db-replica tietokanta
                          :http-palvelin (testi-http-palvelin)
                          :karttakuvat (component/using
                                         (karttakuvat/luo-karttakuvat)
                                         [:http-palvelin :db])
                          :integraatioloki (component/using
                                             (integraatioloki/->Integraatioloki nil)
                                             [:db])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat])
                          :tehtavamaarat (component/using
                                           (tehtavamaarat/->Tehtavamaarat false)
                                           [:http-palvelin :db]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; MH-urakoille määrien toteumat
(def default-toteuma-maara {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                            :toimenpide {:id (hae-tehtavaryhman-id "ELY-rahoitteiset, ylläpito (E)")
                                         :otsikko "8 MUUTA"}
                            :loppupvm (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy") "24.06.2020")
                            :tyyppi :maaramitattava
                            :toteumat [{:tehtava {:id (hae-toimenpidekoodin-id "Pysäkkikatoksen uusiminen" "20191")
                                                  :otsikko "Pysäkkikatoksen uusiminen" :yksikko "kpl"}
                                        :ei-sijaintia true
                                        :sijainti {:numero nil
                                                   :alkuosa nil
                                                   :alkuetaisyys nil
                                                   :loppuosa nil
                                                   :loppuetaisyys nil}
                                        :lisatieto nil
                                        :maara 1
                                        :toteuma-id nil
                                        :toteuma-tehtava-id nil}]})

(defn hae-default-lisatyon-tehtava []
  (let [res (first (q
                     (str "SELECT id,nimi,yksikko FROM tehtava where nimi = 'Lisätyö (talvihoito)'")))]
    {:id (first res)
     :otsikko (second res)
     :yksikko (nth res 2)}))

(def default-lisatyo {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                      :toimenpide {:id (hae-tehtavaryhman-id "Alataso Lisätyöt")
                                   :otsikko "9 LISÄTYÖT"}
                      :loppupvm (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy") "26.06.2020")
                      :tyyppi :lisatyo
                      :toteumat [{:tehtava {:id (:id (hae-default-lisatyon-tehtava))
                                            :otsikko (:otsikko (hae-default-lisatyon-tehtava))
                                            :yksikko (:yksikko (hae-default-lisatyon-tehtava))}
                                  :ei-sijaintia true
                                  :sijainti {:numero nil
                                             :alkuosa nil
                                             :alkuetaisyys nil
                                             :loppuosa nil
                                             :loppuetaisyys nil}
                                  :lisatieto nil
                                  :toteuma-id nil
                                  :toteuma-tehtava-id nil
                                  :maara 1M}]})

(defn- tyyppi-str->keyword [tyyppi]
  (get {"kokonaishintainen" :maaramitattava
        "akillinen-hoitotyo" :akillinen-hoitotyo
        "lisatyo" :lisatyo} tyyppi))

(defn- muokkaa-toteuman-arvot-palvelua-varten
  "Muokataan toteumat tiedot palvelulle lähetettävään muotoon ja samalla hieman muutetaan arvoja, jotta
  voidaan varmistua siitä, että toteuman muokkaus toimii."
  [t urakka-id]
  {:urakka-id urakka-id
   :toimenpide {:id (:toimenpide_id t)
                :otsikko (:toimpenide_otsikko t)}
   :loppupvm (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy") (pvm/pvm (:toteuma_aika t)))
   :tyyppi (tyyppi-str->keyword (:tyyppi t))
   :toteumat [{:tehtava {:id (:tehtava_id t)
                         :otsikko (:tehtava t)
                         :yksikko (:yksikko t)}
               :ei-sijaintia (:ei-sijaintia t)
               :sijainti {:numero (:sijainti_numero t)
                          :alkuosa (:sijainti_alku t)
                          :alkuetaisyys (:sijainti_alkuetaisyys t)
                          :loppuosa (:sijainti_loppu t)
                          :loppuetaisyys (:sijainti_loppuetaisyys t)}
               :lisatieto (str (:lisatieto t) "-muokattu")
               :toteuma-id (:toteuma_id t)
               :toteuma-tehtava-id (:toteuma_tehtava_id t)
               :maara (when (:toteutunut t)
                        (inc (:toteutunut t)))}]})

(defn- lisaa-toteuma [toteuma]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-toteuma +kayttaja-jvh+
                  toteuma))

;;
(deftest lisaa-maarien-toteuma-test
  (let [;; Varmista, että urakka on tallennettu
        _ (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        _ (lisaa-toteuma default-toteuma-maara)
        hoitokauden-alkuvuosi 2019
        ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
        toteumat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                         {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                          :tehtavaryhma 0 ;;"Kaikki"
                                          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        tallennettu-toteuma (keep #(when (= "Pysäkkikatoksen uusiminen" (:tehtava %))
                                     %)
                                  toteumat-vastaus)
        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id tallennettu-toteuma)})]
    (is (= 1 (count tallennettu-toteuma)) "Yksi lisätty toteuma pitäisi löytyä")))

(deftest lisaa-virheellinen-toteuma-test
  (let [virheellinen-toteuma (update default-toteuma-maara :toteumat :maara -1)]
    (is (thrown? Exception (lisaa-toteuma virheellinen-toteuma)))))

(deftest muokkaa-maarien-toteuma-test
  (let [tallennettu-toteuma (lisaa-toteuma default-toteuma-maara)
        ;; :hae-maarien-toteuma ottaa hakuparametrina: id (toteuma-id)
        haettu-toteuma (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :hae-maarien-toteuma +kayttaja-jvh+
                                       {:id (first tallennettu-toteuma)})
        ;; Muokataan tietoja
        muokattava (muokkaa-toteuman-arvot-palvelua-varten haettu-toteuma (hae-oulun-maanteiden-hoitourakan-2019-2024-id))

        muokattu (lisaa-toteuma muokattava)
        haettu-muokattu-toteuma (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-maarien-toteuma +kayttaja-jvh+
                                                {:id (first muokattu)})

        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id haettu-toteuma)})]

    (is (= (:lisatieto haettu-toteuma) (:lisatieto (first (:toteumat default-toteuma-maara))))
      "Toteuman lisätieto täsmää tallennuksen jälkeen")
    (is (= (:tehtava haettu-toteuma) (get-in (first (:toteumat default-toteuma-maara)) [:tehtava :otsikko]))
      "Toteuman tehtava täsmää tallennuksen jälkeen")
    (is (= (:toimenpide_otsikko haettu-toteuma) (get-in default-toteuma-maara [:toimenpide :otsikko]))
      "Toteuman tehtäväryhmä/toimenpide täsmää tallennuksen jälkeen")
    (is (= (:toteutunut haettu-toteuma) (bigdec (:maara (first (:toteumat default-toteuma-maara)))))
      "Toteuman määrä täsmää tallennuksen jälkeen")
    ; Yksikköä ei voi vertailla, koska lokaalissa testikannassa voi olla tilanne, että toimenpidekoodien suunnitteluyksikkö on lisäämättä.
    ; Laitetaan tämä päälle, kunhan toistuvasti ajettavat flyway tiedostot (ns. R__ tiedostot) on saatu oikeaan ajojärjestykseen.
    ;(is (= (:yksikko haettu-toteuma) (get-in (first (:toteumat default-toteuma-maara)) [:tehtava :yksikko])) "Toteuman yksikkö täsmää tallennuksen jälkeen")
    (is (= (tyyppi-str->keyword (:tyyppi haettu-toteuma)) (:tyyppi default-toteuma-maara))
      "Toteuman tyyppi täsmää tallennuksen jälkeen")

    ;; Muokattu toteuma
    (is (not (nil? haettu-muokattu-toteuma)))
    (is (= "-muokattu" (:lisatieto haettu-muokattu-toteuma)))
    (is (= (inc (:toteutunut haettu-toteuma)) (:toteutunut haettu-muokattu-toteuma)))))

(deftest lisaa-lisatyo-test
  (let [t (lisaa-toteuma default-lisatyo)
        hoitokauden-alkuvuosi 2019
        ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
        lisatyo-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-mhu-toteumatehtavat +kayttaja-jvh+
                                        {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                         :tehtavaryhma 0 ;;"Kaikki"
                                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})

        tallennettu-lisatyo (keep #(when (= "Lisätyö (talvihoito)" (:tehtava %))
                                     %)
                                  lisatyo-vastaus)
        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id tallennettu-lisatyo)})]
    (is (= 1 (count tallennettu-lisatyo)) "Yksi lisätty toteuma pitäisi löytyä")))

(deftest muokkaa-lisatyo-test
  (let [tallennettu-lisatyo (lisaa-toteuma default-lisatyo)
        ;; :hae-maarien-toteuma ottaa hakuparametrina: id (toteuma-id)
        haettu-lisatyo (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :hae-maarien-toteuma +kayttaja-jvh+
                                       {:id (first tallennettu-lisatyo)})
        muokattava (muokkaa-toteuman-arvot-palvelua-varten haettu-lisatyo (hae-oulun-maanteiden-hoitourakan-2019-2024-id))
        muokattu (lisaa-toteuma muokattava)
        haettu-muokattu-lisatyo (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-maarien-toteuma +kayttaja-jvh+
                                                {:id (first muokattu)})
        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id haettu-lisatyo)})]

    (is (= (:lisatieto haettu-lisatyo) (:lisatieto default-lisatyo))
      "Toteuman lisätieto täsmää tallennuksen jälkeen")
    (is (= (:tehtava haettu-lisatyo) (get-in (first (:toteumat default-lisatyo)) [:tehtava :otsikko]))
      "Toteuman tehtava täsmää tallennuksen jälkeen")
    (is (= (:toimenpide_otsikko haettu-lisatyo) (get-in default-lisatyo [:toimenpide :otsikko]))
      "Toteuman tehtäväryhmä/toimenpide täsmää tallennuksen jälkeen")
    (is (= (:toteutunut haettu-lisatyo) (:maara (first (:toteumat default-lisatyo))))
      "Toteuman määrä täsmää tallennuksen jälkeen")
    (is (= (:yksikko haettu-lisatyo) (get-in default-lisatyo [:tehtava :yksikko]))
      "Toteuman yksikkö täsmää tallennuksen jälkeen")
    (is (= (tyyppi-str->keyword (:tyyppi haettu-lisatyo)) (:tyyppi default-lisatyo))
      "Toteuman tyyppi täsmää tallennuksen jälkeen")

    ;; Muokattu toteuma
    (is (not (nil? haettu-muokattu-lisatyo)))
    (is (= "-muokattu" (:lisatieto haettu-muokattu-lisatyo)))
    (is (= 1M (:toteutunut haettu-muokattu-lisatyo)))))

#_ (deftest hae-toteumalistaus-test
    (let [hoitokauden-alkuvuosi 2019
          _ (lisaa-toteuma default-toteuma-maara)
          alkupvm "2019-10-01"
          loppupvm "2020-09-30"
          ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
          toteumat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-maarien-toteumat +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :tehtavaryhma "Kaikki"
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
          _ (log/debug "toteumat-vastaus " (pr-str toteumat-vastaus))
          ;; Haetaan suunniteltuja tehtäviä
          ;; tehtavamaara-hierarkia ottaa hakuparametreina: urakka-id hoitokauden-alkuvuosi
          suunnitelmat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-mhu-suunniteltavat-tehtavat +kayttaja-jvh+
                                               {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          _ (log/debug "suunnitelmat-vastaus" (pr-str suunnitelmat-vastaus))
          ;; Tarkistetaan, että kaikki 2019 alkavan hoitokauden suunnitellut tehtävät löytyy vastauksesta
          ]
      (is (= 1 (count toteumat-vastaus)) "Yksi lisätty toteuma pitäisi löytyä")))

(deftest jos-sijainti-pakollinen-maaratoteumassa-saadaan-poikkeus
  (let [sijainti-virheella-1 {:numero nil :alkuosa 2 :alkuetaisyys 3}
        sijainti-virheella-2 {:numero 1 :alkuosa nil :alkuetaisyys 3}
        sijainti-virheella-3 {:numero 1 :alkuosa 2}
        sijainti-virheella-4 {:numero 1 :alkuosa 2 :alkuetaisyys "kissa"}
        sijainti-ok-1 {:numero 1 :alkuosa 2 :alkuetaisyys 3}
        sijainti-ok-2 {:numero 1 :alkuosa 2 :alkuetaisyys 3 :loppuosa 4 :loppuetaisyys 5}
        toteuma (fn [sijainti]
                  {:tyyppi :maaramitattava
                   :urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
                   :loppupvm (pvm/->pvm "19.9.2024")
                   :toteumat [{:maara 123 :tehtava {:id 3118 :tehtava "KT-valuasfalttipaikkaus K" :yksikko "tonni", :rahavaraus nil}
                               :sijainti sijainti}]})]
    (is (thrown? Exception (lisaa-toteuma (toteuma sijainti-virheella-1))) "Arvo puuttuu")
    (is (thrown? Exception (lisaa-toteuma (toteuma sijainti-virheella-2))) "Arvo puuttuu")
    (is (thrown? Exception (lisaa-toteuma (toteuma sijainti-virheella-3))) "Arvo puuttuu")
    (is (thrown? Exception (lisaa-toteuma (toteuma sijainti-virheella-4))) "Osoitearvojen oltava numeerit")

    (is (not (thrown? Exception (lisaa-toteuma (toteuma sijainti-ok-1)))))
    (is (not (thrown? Exception (lisaa-toteuma (toteuma sijainti-ok-2)))))))
