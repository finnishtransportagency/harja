(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.kyselyt.valikatselmus :as q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.tyokalut.yleiset :refer [round2]])
  (:import (clojure.lang ExceptionInfo)
           (harja.domain.roolit EiOikeutta)))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :valikatselmus (component/using
                                         (valikatselmukset/->Valikatselmukset)
                                         [:http-palvelin :db :db-replica])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Helpperit
(defn filtteroi-oikaisut-selitteella [oikaisut selite]
  (filter #(= selite (::valikatselmus/selite %))
          oikaisut))

(defn kayttaja [urakka-id]
  (assoc +kayttaja-tero+
    :urakkaroolit {urakka-id #{"ELY_Urakanvalvoja"}}))

;; Tavoitehinnan oikaisut
(deftest tavoitehinnan-oikaisu-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        ;; With-redefsillä laitetaan (pvm/nyt) palauttamaan tietty ajankohta. Tämä sen takia, että
        ;; rajapinta antaa virheen, mikäli kutsuhetkellä ei saa tehdä tavoitehinnan oikaisuja.
        ;; Tätä tulee käyttää varoen, koska tämä ylirjoittaa kaikki (pvm/nyt) kutsut blokin sisällä, joita saattaa
        ;; tapahtua pinnan alla.
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/summa 9001
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
        vuoden-2021-oikaisut (vals (get-in (:tavoitehinnan-muutokset vastaus) [hoitokauden-alkuvuosi]))
        viimeisin-oikaisu (last vuoden-2021-oikaisut)]
    (is (some? vastaus))
    (is (= (::valikatselmus/summa viimeisin-oikaisu) 9001M))))

(deftest tavoitehinnan-oikaisu-muuttaa-kattohintaa-onnistuneesti
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        oikaisun-summa 9001M
        oikaistu-tavoitehinta-ennen (q/hae-oikaistu-tavoitehinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                              :urakka-id urakka-id})
        oikaistu-kattohinta-ennen (q/hae-oikaistu-kattohinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                                    :urakka-id urakka-id})
        ;; With-redefsillä laitetaan (pvm/nyt) palauttamaan tietty ajankohta. Tämä sen takia, että
        ;; rajapinta antaa virheen, mikäli kutsuhetkellä ei saa tehdä tavoitehinnan oikaisuja.
        ;; Tätä tulee käyttää varoen, koska tämä ylirjoittaa kaikki (pvm/nyt) kutsut blokin sisällä, joita saattaa
        ;; tapahtua pinnan alla.
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-tavoitehinnan-oikaisu
                    (kayttaja urakka-id)
                    {::urakka/id urakka-id
                     ::valikatselmus/otsikko "Oikaisu"
                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                     ::valikatselmus/summa oikaisun-summa
                     ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
        vuoden-2021-oikaisut (vals (get-in (:tavoitehinnan-muutokset vastaus) [hoitokauden-alkuvuosi]))
        viimeisin-oikaisu (last vuoden-2021-oikaisut)
        oikaistu-tavoitehinta-jalkeen (q/hae-oikaistu-tavoitehinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                                    :urakka-id urakka-id})
        oikea-tavoitehinta (+ oikaistu-tavoitehinta-ennen oikaisun-summa)
        oikaistu-kattohinta-jalkeen (q/hae-oikaistu-kattohinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                                :urakka-id urakka-id})
        ;; Kattohinta kasvaa 10% myös tavoitehinnan oikaisusta
        oikea-kattohinta (+ (* oikaisun-summa 1.1M) oikaistu-kattohinta-ennen)]
    (is (some? vastaus))
    ;; Menikö oikaisu oikein?
    (is (= (::valikatselmus/summa viimeisin-oikaisu) oikaisun-summa))
    ;; Muuttuiko tavoitehihinta?
    (is (= oikaistu-tavoitehinta-jalkeen oikea-tavoitehinta))
    ;; Muuttuiko kattohinta?
    (is (= oikaistu-kattohinta-jalkeen oikea-kattohinta))))

;; Tämä ominaisuus on otettu toistaiseksi pois käytöstä
#_ (deftest oikaisun-teko-epaonnistuu-alkuvuodesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        virheellinen-vuosi (+ hoitokauden-alkuvuosi 2)
        virheellinen-vastaus (try
                               (with-redefs [pvm/nyt #(pvm/luo-pvm virheellinen-vuosi 5 20)]
                                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :tallenna-tavoitehinnan-oikaisu
                                                 (kayttaja urakka-id)
                                                 {::urakka/id urakka-id
                                                  ::valikatselmus/otsikko "Oikaisu"
                                                  ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                  ::valikatselmus/summa 1000
                                                  ::valikatselmus/selite "Juhannusmenot hidasti"}))
                               (catch Exception e e))]
    (is (= ExceptionInfo (type virheellinen-vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa käsitellä ainoastaan sallitulla aikavälillä." (-> virheellinen-vastaus ex-data :virheet :viesti)))))

(deftest virheellisen-oikaisun-teko-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021]
    (is (thrown? Exception (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-tavoitehinnan-oikaisu
                                             (kayttaja urakka-id)
                                             {::urakka/id urakka-id
                                              ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                              ::valikatselmus/otsikko "Oikaisu"
                                              ::valikatselmus/summa "Kolmesataa"
                                              ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))))))

(deftest muokkaa-tavoitehinnan-oikaisua
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) (kayttaja urakka-id)
          {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})
        oikaisut (vals (get (:tavoitehinnan-muutokset vastaus) hoitokauden-alkuvuosi))
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  (assoc muokattava-oikaisu ::valikatselmus/summa 50000)))
        vuoden-2021-oikaisut (vals (get-in (:tavoitehinnan-muutokset vastaus) [hoitokauden-alkuvuosi]))
        vastauksen-oikaisu (first (filter #(= "Muokattava testioikaisu" (:harja.domain.kulut.valikatselmus/selite %)) vuoden-2021-oikaisut))
        ;; Ajankohtien millisekunnit hieman heittävät tallennuksen yhteydessä, niin trimmataan niitä hieman
        odotettu-vastaus (-> vastauksen-oikaisu
                           (update :harja.domain.muokkaustiedot/muokattu #(pvm/aika-iso8601-ilman-millisekunteja %))
                           (update :harja.domain.muokkaustiedot/luotu #(pvm/aika-iso8601-ilman-millisekunteja %)))
        muokattava-oikaisu (-> muokattava-oikaisu
                             (assoc ::valikatselmus/summa 50000M) ;; Summa muuttuu 2000 -> 50000 ja tätä nimen omaan testataan
                             (assoc :harja.domain.muokkaustiedot/muokattu (pvm/aika-iso8601-ilman-millisekunteja
                                                                            (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))))
                             (update :harja.domain.muokkaustiedot/luotu #(pvm/aika-iso8601-ilman-millisekunteja %)))]
    (is (= muokattava-oikaisu odotettu-vastaus) "Summan muokkaus ei onnistunut")

    (let [oikaisut-jalkeen (get (:tavoitehinnan-muutokset (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) (kayttaja urakka-id)
                                   {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})) hoitokauden-alkuvuosi)
          vuoden-2021-oikaisut (vals oikaisut-jalkeen)
          muokattu-oikaisu (first (filtteroi-oikaisut-selitteella vuoden-2021-oikaisut "Muokattava testioikaisu"))]
      (is (= 50000M (::valikatselmus/summa muokattu-oikaisu))))))

;; Tarkistus otettu toistaiseksi pois käytöstä
#_ (deftest tavoitehinnan-oikaisun-muokkaus-ei-onnistu-tammikuussa
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        virheellinen-vuosi (+ 2 hoitokauden-alkuvuosi)
        oikaisut (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tavoitehintojen-oikaisut
                                      (kayttaja urakka-id)
                                      {::urakka/id urakka-id}) hoitokauden-alkuvuosi)
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (try (with-redefs [pvm/nyt #(pvm/luo-pvm virheellinen-vuosi 0 15)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       (kayttaja urakka-id)
                                       (assoc muokattava-oikaisu ::valikatselmus/summa 1)))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa käsitellä ainoastaan sallitulla aikavälillä." (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisun-poisto-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        tavoitehintojen-oikaisut (vals (get (:tavoitehinnan-muutokset (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) (kayttaja urakka-id)
                                                                        {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})) hoitokauden-alkuvuosi))
        poistettava (first (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %)) tavoitehintojen-oikaisut))
        uudet-oikaisut (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                         (vals (get (:tavoitehinnan-muutokset (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                :poista-tavoitehinnan-oikaisu
                                                                (kayttaja urakka-id)
                                                                {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id poistettava)})) hoitokauden-alkuvuosi)))]
    (is (= (count tavoitehintojen-oikaisut) (inc (count uudet-oikaisut))))
    (is (empty? (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %)) uudet-oikaisut)))))

(deftest tavoitehinnan-oikaisu-epaonnistuu-alueurakalle
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        hoitokauden-alkuvuosi 2019
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 9001
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa tehdä ainoastaan teiden hoitourakoille" (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisu-onnistuu-urakanvalvojalla
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2022
        tavoitehinnan-muutokset (vals (get (:tavoitehinnan-muutokset (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                                                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-tavoitehinnan-oikaisu
                                                         (kayttaja urakka-id)
                                                         {::urakka/id urakka-id
                                                          ::valikatselmus/otsikko "Oikaisu"
                                                          ::valikatselmus/summa 12345
                                                          ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                          ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))) hoitokauden-alkuvuosi))
        viimeisin (last tavoitehinnan-muutokset)]
    (is (= 12345M (::valikatselmus/summa viimeisin)))))

(deftest tavoitehinnan-oikaisu-epaonnistuu-sepolla
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       +kayttaja-seppo+
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 12345
                                        ::valikatselmus/hoitokauden-alkuvuosi 2021
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch ExceptionInfo e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= EiOikeutta (type (ex-data vastaus))))))

(deftest tavoitehinnan-miinusmerkkinen-oikaisu-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        tavoitehinnan-muutokset (vals (get (:tavoitehinnan-muutokset (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                                                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-tavoitehinnan-oikaisu
                                                         (kayttaja urakka-id)
                                                         {::urakka/id urakka-id
                                                          ::valikatselmus/otsikko "Oikaisu"
                                                          ::valikatselmus/summa -2000
                                                          ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                          ::valikatselmus/selite "Seppo kävi töissä, päällystykset valmistui odotettua nopeampaa"}))) hoitokauden-alkuvuosi))
        viimeisin (last tavoitehinnan-muutokset)]
    (is (= -2000M (::valikatselmus/summa viimeisin)))))


(deftest hae-valikatselmuksen-tiedot-hoitovuodelle-2019-2019-test-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        hoitokauden-alkuvuosi 2019
        useri (kayttaja urakka-id)
        vastaus (try
                  (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) useri
                    {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})
                  (catch Exception e e))]
    (is (not= (count vastaus) 0))
    (is (not (nil? (:paatokset vastaus))) "Päätökset pitäisi löytyä")
    (is (not (nil? (get-in vastaus [:yhteenveto :sanktiot]))) "Sanktiot pitäisi löytyä")
    (is (not (nil? (get-in vastaus [:yhteenveto :bonukset]))) "Bonukset pitäisi löytyä")))

(deftest hae-valikatselmuksen-tiedot-hoitovuodelle-2019-2021-test-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        hoitokauden-alkuvuosi 2021
        useri (kayttaja urakka-id)
        vastaus (try
                  (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) useri
                    {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})
                  (catch Exception e e))]
    (is (not= (count vastaus) 0))
    (is (not (nil? (:paatokset vastaus))) "Päätökset pitäisi löytyä")
    (is (not (nil? (get-in vastaus [:yhteenveto :sanktiot]))) "Sanktiot pitäisi löytyä")
    (is (not (nil? (get-in vastaus [:yhteenveto :bonukset]))) "Bonukset pitäisi löytyä")))

;; mhu+ urakka
(deftest hae-valikatselmuksen-tiedot-hoitovuodelle-2025-2025-test-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokauden-alkuvuosi 2025
        useri (kayttaja urakka-id)
        vastaus (try
                  (with-redefs [;; Validoinnin takia päätöksiä ei saada kuluvalle hoitovuodelle haettua, joten feikataan nykyhetki tulevaisuuteen
                                pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2026 10 15))]
                    (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) useri
                      {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi}))
                  (catch Exception e e))]
    (is (not= (count vastaus) 0))
    (is (not (nil? (:paatokset vastaus))) "Päätökset pitäisi löytyä")
    (is (= "Lupaukset" (:nimi (some :lupaukset (:paatokset vastaus)))) "Lupauspäätös pitäisi löytyä")
    (is (= "Tavoitehinnan pysyvät muutokset" (:nimi (some :tavoitehinnan-pysyvat-muutokset (:paatokset vastaus)))) "Tavoitehinnan muutokset -päätös pitäisi löytyä")
    (is (= "Hoitovuoden lopun indeksikorjaus" (:nimi (some :hoitovuoden-lopun-indeksikorjaus (:paatokset vastaus)))) "Hoitovuoden lopun indeksikorjaus -päätös pitäisi löytyä")
    (is (= "Hoitovuoden lopun tavoite- ja kattohinta" (:nimi (some :hoitovuoden-lopun-tavoite-ja-kattohinta (:paatokset vastaus)))) "Hoitovuoden lopun tavoite- ja kattohinta -päätös pitäisi löytyä")
    (is (= "Tavoitehinnan alitus" (:nimi (some :tavoitehinnan-alitus (:paatokset vastaus)))) "Tavoitehinnan alitus -päätös pitäisi löytyä")
    (is (= "Hoidonjohtopalkkion muutos" (:nimi (some :hoidonjohtopalkkion-muutos (:paatokset vastaus)))) "Hoidonjohtopalkkion muutos -päätös pitäisi löytyä")
    (is (= "Välikatselmuspöytäkirjaan liitettävät raportit" (:nimi (some :valikatselmuspoytakirjaan-liitettavat-raportit (:paatokset vastaus)))) "Välikatselmuspöytäkirjaan liitettävät raportit -päätös pitäisi löytyä")))

(deftest hae-valikatselmuksen-tiedot-hoitovuodelle-2025-arvonvahennykset-erotellaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokauden-alkuvuosi 2025
        testikayttaja (kayttaja urakka-id)
        luoja-id (:id (first (q-map "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'")))
        toimenpideinstanssi-id (:id (first (q-map (format "SELECT id FROM toimenpideinstanssi WHERE urakka = %s ORDER BY id LIMIT 1" urakka-id))))
        sanktiotyyppi-id (:id (first (q-map "SELECT id FROM sanktiotyyppi WHERE koodi = 0 LIMIT 1")))
        tavallinen-sanktio-kuvaus "Välikatselmuksen arvonvähennystesti - tavallinen sanktio"
        arvonvahennys-kuvaus "Välikatselmuksen arvonvähennystesti - arvonvähennys"
        tavallinen-sanktio-maara 1234.56M
        arvonvahennys-maara 2345.67M]
    (try
      (let [tavallinen-laatupoikkeama-id
            (i (format (str "INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, "
                            "tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, "
                            "tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys) "
                            "VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', "
                            "'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Testin vuoksi lisätty sanktio', 123, %s, NOW(), "
                            "'2025-10-11 06:06.37', '2025-10-11 06:06.37', FALSE, FALSE, %s, '%s', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5)")
                       luoja-id urakka-id tavallinen-sanktio-kuvaus))
            _ (i (format (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja) "
                              "VALUES ('A'::SANKTIOLAJI, %s, '2025-10-12 06:06.37', '2025-10-11 06:06.37', 'MAKU 2015', %s, %s, %s, FALSE, %s)")
                         tavallinen-sanktio-maara tavallinen-laatupoikkeama-id toimenpideinstanssi-id sanktiotyyppi-id luoja-id))
            arvonvahennys-laatupoikkeama-id
            (i (format (str "INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, "
                            "tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, "
                            "tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, sijainti, tr_alkuetaisyys) "
                            "VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '', "
                            "'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Testin vuoksi lisätty arvonvähennys', 123, %s, NOW(), "
                            "'2025-11-11 06:06.37', '2025-11-11 06:06.37', FALSE, FALSE, %s, '%s', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5)")
                       luoja-id urakka-id arvonvahennys-kuvaus))
            _ (i (format (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, maarattypvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja) "
                              "VALUES ('arvonvahennyssanktio'::SANKTIOLAJI, %s, '2025-11-12 06:06.37', '2025-11-11 06:06.37', 'MAKU 2015', %s, %s, %s, FALSE, %s)")
                         arvonvahennys-maara arvonvahennys-laatupoikkeama-id toimenpideinstanssi-id sanktiotyyppi-id luoja-id))
            vastaus (with-redefs [;; Validoinnin takia päätöksiä ei saada kuluvalle hoitovuodelle haettua, joten feikataan nykyhetki tulevaisuuteen
                                  pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2026 10 15))]
                      (valikatselmukset/hae-valikatselmuksen-tiedot-hoitovuodelle (:db jarjestelma) testikayttaja
                        {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi}))
            sanktiot (get-in vastaus [:yhteenveto :sanktiot])
            arvonvahennykset (get-in vastaus [:yhteenveto :arvonvahennykset])]
        (is (seq sanktiot) "Sanktiot pitäisi löytyä")
        (is (seq arvonvahennykset) "Arvonvähennykset pitäisi löytyä")
        (is (some #(= (- tavallinen-sanktio-maara) (:maara %)) sanktiot) "Tavallisen sanktion löytyy")
        (is (not-any? #(= "arvonvahennyssanktio" (:sakkoryhma %)) sanktiot) "Sanktiot-lista ei saa sisältää arvonvähennyksiä MHU 2025+ -urakalla")
        (is (every? #(= "arvonvahennyssanktio" (:sakkoryhma %)) arvonvahennykset) "Arvonvähennysten listalla saa olla vain arvonvähennyksiä")
        (is (some #(= (- arvonvahennys-maara) (:maara %)) arvonvahennykset) "Lisätty arvonvähennys löytyy")))))

(deftest onko-paatoksia-tekematta-vuodelle-2021-test
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        hoitokauden-alkuvuosi 2021
        kayttaja (kayttaja urakka-id)
        indeksi (:indeksi urakan-tiedot)
        hoitokauden-alun-tavoitehinta (valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (:db jarjestelma) {:urakka-id urakka-id :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        hoitokauden-lopun-tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta (:db jarjestelma) {:urakka-id urakka-id :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
    (testing "Ilman päätöksiä pitäisi olla päätökset tekemättä"
      (let [vastaus (try
                      (valikatselmukset/onko-paatoksia-tekematta (:db jarjestelma) kayttaja
                        {:urakkaid urakka-id :kuluva-hoitovuosi hoitokauden-alkuvuosi})
                      (catch Exception e e))]
        (is (= true vastaus))))
    (testing "Päätösten lisäämisen jälkeen kaikki pitäisi olla kunnossa"
      (let [;; Lupausbonus
            tyyppi "bonus"
            tavoitehinta 5M
            tarjous-tavoitehinta 5M
            luvatut-pisteet 76
            toteutuneet-pisteet 92
            lupausbonus 100M
            paatos-pvm (pvm/->pvm "12.05.2024")
            indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakka-id paatos-pvm indeksi lupausbonus false)
            lupaussanktio nil
            bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
            sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
            erilliskustannus-id 1
            sanktio-id 1
            lupauspaatos (paatos-apurit/lupauspaatos urakka-id hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                           lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id (:id kayttaja))
            _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)

            ;; Tavoitehinnan alitus
            toteutuneet-kustannukset (- hoitokauden-alun-tavoitehinta 10000)
            alituksen-maara (- hoitokauden-alun-tavoitehinta toteutuneet-kustannukset)
            siirto-ed-vuodelta 60000.0M
            tavoitepalkkio 10000.0M
            tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
            tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
            kulu-id 1
            viimeinen_hoitokausi false
            ;; Lisää siirretyt kulut Välikatselmuksesta "edelliseltä vuodelta" tekemällä tavoitehinnan alituspäätös
            alituspaatos (paatos-apurit/tavoitehinnan-alituspaatos urakka-id hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                     alituksen-maara siirto-ed-vuodelta tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id
                     viimeinen_hoitokausi (:id kayttaja))
            _ (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) alituspaatos)

            ;; Tavoitehinnan muutos
            muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
            tavoitehinta 5M
            kattohinta 5M
            muutospaatos (paatos-apurit/tavoitehinnan-muutospaatos urakka-id hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta (:id kayttaja))
            _ (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) muutospaatos (:id kayttaja))

            vastaus (try
                      (valikatselmukset/onko-paatoksia-tekematta (:db jarjestelma) kayttaja
                        {:urakkaid urakka-id :kuluva-hoitovuosi hoitokauden-alkuvuosi})
                      (catch Exception e e))]
        (is (= false vastaus))))))

(deftest onko-paatoksia-tekematta-vuodelle-2024-test
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        hoitokauden-alkuvuosi 2024
        kayttaja (kayttaja urakka-id)
        indeksi (:indeksi urakan-tiedot)
        hoitokauden-alun-tavoitehinta 5
        hoitokauden-lopun-tavoitehinta 6]

    (with-redefs [valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta (fn [db hakuparametrit] hoitokauden-alun-tavoitehinta)
                  valikatselmus-q/hae-oikaistu-tavoitehinta (fn [db hakuparametrit] hoitokauden-lopun-tavoitehinta)]



      (testing "Päätösten lisäämisen jälkeen kaikki pitäisi olla kunnossa"
        (let [;; Lupausbonus
              tyyppi "bonus"
              tavoitehinta 5M
              tarjous-tavoitehinta 5M
              luvatut-pisteet 76
              toteutuneet-pisteet 92
              lupausbonus 100M
              paatos-pvm (pvm/->pvm "12.05.2024")
              indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakka-id paatos-pvm indeksi lupausbonus false)
              lupaussanktio nil
              bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
              sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
              erilliskustannus-id 1
              sanktio-id 1
              lupauspaatos (paatos-apurit/lupauspaatos urakka-id hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                             lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id (:id kayttaja))
              _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)

              ;; Tavoitehinnan alitus
              toteutuneet-kustannukset (- hoitokauden-alun-tavoitehinta 10000)
              alituksen-maara (- hoitokauden-alun-tavoitehinta toteutuneet-kustannukset)
              siirto-ed-vuodelta 60000.0M
              tavoitepalkkio 10000.0M
              tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
              tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)
              kulu-id 1
              viimeinen_hoitokausi false
              ;; Lisää siirretyt kulut Välikatselmuksesta "edelliseltä vuodelta" tekemällä tavoitehinnan alituspäätös
              alituspaatos (paatos-apurit/tavoitehinnan-alituspaatos urakka-id hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                             alituksen-maara siirto-ed-vuodelta tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id
                             viimeinen_hoitokausi (:id kayttaja))
              _ (paatos-kyselyt/tee-tavoitehinnan-alituspaatos (:db jarjestelma) alituspaatos)

              ;; Tavoitehinnan muutos
              muokkaa-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit)
              tavoitehinta 5M
              kattohinta 5M
              muutospaatos (paatos-apurit/tavoitehinnan-muutospaatos urakka-id hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta (:id kayttaja))
              _ (paatos-kyselyt/tee-tavoitehinnan-muutospaatos (:db jarjestelma) muutospaatos (:id kayttaja))

              ;; Indeksikorjauspäätös
              hv_alun_indkorj_tavoitehinta 2000000M
              hoitokauden-lopun-indeksikorjaus 40000M ;
              tavoitehinnan-muutokset 30000M
              hv_lopun_tavoitehinta_ennen_indkorj (+ hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset)
              hoitokauden-kuukaudet [{:kuukausi "Lokakuu 2021" :indeksiluku 112.4}
                                     {:kuukausi "Marraskuu 2021" :indeksiluku 112.5}
                                     {:kuukausi "Joulukuu 2021" :indeksiluku 112.6}
                                     {:kuukausi "Tammikuu 2022" :indeksiluku 112.7}
                                     {:kuukausi "Helmikuu 2022" :indeksiluku 112.8}
                                     {:kuukausi "Maaliskuu 2022" :indeksiluku 112.9}
                                     {:kuukausi "Huhtikuu 2022" :indeksiluku 113.0}
                                     {:kuukausi "Toukokuu 2022" :indeksiluku 113.1}
                                     {:kuukausi "Kesäkuu 2022" :indeksiluku 113.2}
                                     {:kuukausi "Heinäkuu 2022" :indeksiluku 113.3}
                                     {:kuukausi "Elokuu 2022" :indeksiluku 113.4}
                                     {:kuukausi "Syyskuu 2022" :indeksiluku 113.5}]
              kuukausien-keskiarvo (/ (apply + (map :indeksiluku hoitokauden-kuukaudet)) (count hoitokauden-kuukaudet))
              alkuperainen-pisteluku 112.5
              alkuperaisen-pisteluvun-kuukausi "elokuu 2023"
              pistelukujen-muutos 5.9
              pistelukujen-muutos-prosentteina (with-precision 4 (round2 1 (* (/ (- kuukausien-keskiarvo alkuperainen-pisteluku) kuukausien-keskiarvo) 100)))
              indeksikorotuksen-prosenttiosuus 3.9
              indeksikorjauspaatos (paatos-apurit/indeksikorjauspaatos urakka-id hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                       hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                       pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus hoitokauden-lopun-indeksikorjaus (:id kayttaja))
              _ (paatos-kyselyt/tee-indeksikorjauspaatos (:db jarjestelma) indeksikorjauspaatos)

              ;; Hoitovuoden lopun tavoite ja kattohintapäätös
              lisaa-tavoitehintaan-lopunindeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)
              tavoitehinta_ennen 2000000M
              hoitokauden-lopun-indeksikorjaus 40000M
              tavoitehinnan_muutokset 40000M
              tavoitehinta_jalkeen (+ tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus tavoitehinnan_muutokset)
              kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
              kattohinta (* kattohintakerroin tavoitehinta_jalkeen)
              lopun-hintapaatos (paatos-apurit/lopun-hintapaatos urakka-id hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                       tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus (:id kayttaja))

              _ (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos (:db jarjestelma) lopun-hintapaatos)

              ;; Hoidonjohtopalkkion muutos
              tavoitehinta 2100000M    ;; Hoitovuoden lopun tavoihinta ilman indeksikorjausta
              tarjouksen_tavoitehinta 2000000M
              muutosprosentti (* (- (/ tavoitehinta tarjouksen_tavoitehinta) 1) 100)
              hoidonjohtopalkkio 40000M
              hoidonjohtopalkkio_muutos (* hoidonjohtopalkkio muutosprosentti)
              kulu_id 1

              hoidojohtopalkkiomuutospaatos (paatos-apurit/hoidojohtopalkkiomuutospaatos urakka-id hoitokauden-alkuvuosi tavoitehinta tarjouksen_tavoitehinta
                       muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id (:id kayttaja))
              _ (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos (:db jarjestelma) hoidojohtopalkkiomuutospaatos)

              vastaus (try
                        (valikatselmukset/onko-paatoksia-tekematta (:db jarjestelma) kayttaja
                          {:urakkaid urakka-id :kuluva-hoitovuosi hoitokauden-alkuvuosi})
                        (catch Exception e e))]
          (is (= false vastaus)))))))
