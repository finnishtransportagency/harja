(ns harja.palvelin.palvelut.kulut.valikatselmus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.kyselyt.valikatselmus :as q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.erilliskustannus-kyselyt :as erilliskustannus-kyselyt]
            [harja.kyselyt.sanktiot :as sanktiot-q]
            [harja.palvelin.palvelut.kulut.valikatselmukset :as valikatselmukset]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [taoensso.timbre :as log])
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
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (some? vastaus))
    (is (= (::valikatselmus/summa vastaus) 9001M))))

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

        oikaistu-tavoitehinta-jalkeen (q/hae-oikaistu-tavoitehinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                                    :urakka-id urakka-id})
        oikea-tavoitehinta (+ oikaistu-tavoitehinta-ennen oikaisun-summa)
        oikaistu-kattohinta-jalkeen (q/hae-oikaistu-kattohinta (:db jarjestelma) {:hoitokauden-alkuvuosi 2021
                                                                                :urakka-id urakka-id})
        ;; Kattohinta kasvaa 10% myös tavoitehinnan oikaisusta
        oikea-kattohinta (+ (* oikaisun-summa 1.1M) oikaistu-kattohinta-ennen)]
    (is (some? vastaus))
    ;; Menikö oikaisu oikein?
    (is (= (::valikatselmus/summa vastaus) oikaisun-summa))
    ;; Muuttuiko tavoitehihinta?
    (is (= oikaistu-tavoitehinta-jalkeen oikea-tavoitehinta))
    ;; Muuttuiko kattohinta?
    (is (= oikaistu-kattohinta-jalkeen oikea-kattohinta))))

(deftest oikaisun-teko-epaonnistuu-alkuvuodesta
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
        oikaisut (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tavoitehintojen-oikaisut
                                      (kayttaja urakka-id)
                                      {::urakka/id urakka-id}) hoitokauden-alkuvuosi)
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  (assoc muokattava-oikaisu ::valikatselmus/summa 50000)))]
    (is (= 1 vastaus) "Summan muokkaus ei onnistunut")

    (let [oikaisut-jalkeen (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-tavoitehintojen-oikaisut
                                                (kayttaja urakka-id)
                                                {::urakka/id urakka-id}) hoitokauden-alkuvuosi)
          muokattu-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut-jalkeen "Muokattava testioikaisu"))]
      (is (= 50000M (::valikatselmus/summa muokattu-oikaisu))))))

(deftest tavoitehinnan-oikaisun-muokkaus-ei-onnistu-tammikuussa
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
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-tavoitehintojen-oikaisut
                        +kayttaja-jvh+
                        {::urakka/id urakka-id})
        poistettava (first (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %))
                                   (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-tavoitehintojen-oikaisut
                                                        +kayttaja-jvh+
                                                        {::urakka/id urakka-id}) hoitokauden-alkuvuosi)))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :poista-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id poistettava)}))]
    (is (= 1 vastaus))
    (is (empty? (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %))
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-tavoitehintojen-oikaisut
                                        (kayttaja urakka-id)
                                        {::urakka/id urakka-id}))))))

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
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa 12345
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (= 12345M (::valikatselmus/summa vastaus)))))

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
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa -2000
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/selite "Seppo kävi töissä, päällystykset valmistui odotettua nopeampaa"}))]
    (is (= -2000M (::valikatselmus/summa vastaus)))))

;; Kattohinnan oikaisut

(defn kattohinnan-oikaisu [urakka-id hoitokauden-alkuvuosi]
  (->
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :hae-kattohintojen-oikaisut
      (kayttaja urakka-id)
      {::urakka/id urakka-id})
    (get hoitokauden-alkuvuosi)))

(deftest kattohinnan-oikaisun-tallennus-ja-haku-onnistuu
  (try
    (let [hoitokauden-alkuvuosi 2021]
      (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
        (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
              oikaistu-tavoitehinta (valikatselmukset/oikaistu-tavoitehinta-vuodelle (:db jarjestelma) urakka-id hoitokauden-alkuvuosi)
              uusi-kattohinta-1 (+ oikaistu-tavoitehinta 1)
              uusi-kattohinta-2 (+ oikaistu-tavoitehinta 2)
              uusi-kattohinta-3 (+ oikaistu-tavoitehinta 3)
              ;; Lisätään kattohinnan oikaisu
              lisays-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-kattohinnan-oikaisu
                               (kayttaja urakka-id)
                               {::urakka/id urakka-id
                                ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                ::valikatselmus/uusi-kattohinta uusi-kattohinta-1})
              haku-vastaus (kattohinnan-oikaisu urakka-id hoitokauden-alkuvuosi)
              ;; Päivitetään
              paivitys-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-kattohinnan-oikaisu
                                 (kayttaja urakka-id)
                                 {::urakka/id urakka-id
                                  ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                  ::valikatselmus/uusi-kattohinta uusi-kattohinta-2})
              haku-vastaus-2 (kattohinnan-oikaisu urakka-id hoitokauden-alkuvuosi)
              ;; Merkitään poistetuksi
              poisto-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                               :poista-kattohinnan-oikaisu
                               (kayttaja urakka-id)
                               {::urakka/id urakka-id
                                ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
              haku-vastaus-3 (kattohinnan-oikaisu urakka-id hoitokauden-alkuvuosi)
              ;; Päivitetään, ja merkitään ei-poistetuksi
              paivitys-vastaus-2 (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-kattohinnan-oikaisu
                                   (kayttaja urakka-id)
                                   {::urakka/id urakka-id
                                    ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                    ::valikatselmus/uusi-kattohinta uusi-kattohinta-3})
              haku-vastaus-4 (kattohinnan-oikaisu urakka-id hoitokauden-alkuvuosi)]
          (is (= (::valikatselmus/uusi-kattohinta lisays-vastaus) (bigdec uusi-kattohinta-1)))
          (is (= (::valikatselmus/uusi-kattohinta haku-vastaus) (bigdec uusi-kattohinta-1)))
          (is (= (::valikatselmus/uusi-kattohinta paivitys-vastaus) (bigdec uusi-kattohinta-2)))
          (is (= (::valikatselmus/uusi-kattohinta haku-vastaus-2) (bigdec uusi-kattohinta-2)))
          (is (:harja.domain.muokkaustiedot/poistettu? poisto-vastaus))
          (is (not haku-vastaus-3) "Rivi on merkitty poistetuksi, eikä sitä enää palauteta")
          (is (= (::valikatselmus/uusi-kattohinta paivitys-vastaus-2) (bigdec uusi-kattohinta-3)))
          (is (= (::valikatselmus/uusi-kattohinta haku-vastaus-4) (bigdec uusi-kattohinta-3))))))
    (catch Throwable e
      (log/error e)
      (throw e))))

;; Testataan eri virhetilanteita samssa testissä, koska jokaiselle testille täytyy nollata tietokanta erikseen
(deftest kattohinnan-oikaisu-epaonnistuu
  (testing "Ei oikeutta"
    (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
          hoitokauden-alkuvuosi 2021
          vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-kattohinnan-oikaisu
                           +kayttaja-seppo+
                           {::urakka/id urakka-id
                            ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                            ::valikatselmus/uusi-kattohinta 9999999999}))
                       (catch ExceptionInfo e e))]
      (is (= ExceptionInfo (type vastaus)))
      (is (= EiOikeutta (type (ex-data vastaus))))))

  (testing "Alueurakka (ei MHU)"
    (let [urakka-id @kemin-alueurakan-2019-2023-id
          hoitokauden-alkuvuosi 2019
          vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-kattohinnan-oikaisu
                           (kayttaja urakka-id)
                           {::urakka/id urakka-id
                            ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                            ::valikatselmus/uusi-kattohinta 9999999999}))
                       (catch Exception e e))]
      (is (= ExceptionInfo (type vastaus)))
      (is (= "Kattohinnan oikaisuja saa tehdä ainoastaan teiden hoitourakoille" (-> vastaus ex-data :virheet :viesti)))))

  (testing "Kattohinta pienempi kuin tavoitehinta"
    (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
          hoitokauden-alkuvuosi 2021
          oikaistu-tavoitehinta (valikatselmukset/oikaistu-tavoitehinta-vuodelle (:db jarjestelma) urakka-id hoitokauden-alkuvuosi)
          vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-kattohinnan-oikaisu
                           (kayttaja urakka-id)
                           {::urakka/id urakka-id
                            ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                            ::valikatselmus/uusi-kattohinta (- oikaistu-tavoitehinta 1)}))
                       (catch ExceptionInfo e e))]
      (is (= ExceptionInfo (type vastaus)))
      (is (= "Kattohinnan täytyy olla suurempi kuin tavoitehinta" (-> vastaus ex-data :virheet :viesti))))))

;; Päätökset
(deftest tee-paatos-tavoitehinnan-ylityksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/tilaajan-maksu 7000.00
                                   ::valikatselmus/urakoitsijan-maksu 3000.00}))]
    (is (= 7000M (::valikatselmus/tilaajan-maksu vastaus)))
    (is (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi vastaus)))))

(deftest muokkaa-tavoitehinnan-ylityksen-paatosta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        luotu (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paatos
                                (kayttaja urakka-id)
                                {::urakka/id urakka-id
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                 ::valikatselmus/tilaajan-maksu 7000.00
                                 ::valikatselmus/urakoitsijan-maksu 3000.00}))
        muokattu (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-urakan-paatos
                                   (kayttaja urakka-id)
                                   {::urakka/id urakka-id
                                    ::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id luotu)
                                    ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                    ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                    ::valikatselmus/tilaajan-maksu 8000.00
                                    ::valikatselmus/urakoitsijan-maksu 2000.00}))]
    (is (= 8000M (::valikatselmus/tilaajan-maksu muokattu)))
    (is (= 2021 (::valikatselmus/hoitokauden-alkuvuosi muokattu)))))

(deftest tavoitehinnan-ylitys-liian-suurella-summalla
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/tilaajan-maksu 70000000.00
                                        ::valikatselmus/urakoitsijan-maksu 3000.00}))
                     (catch ExceptionInfo e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Maksujen osuus suurempi, kuin tavoitehinnan ja kattohinnan erotus." (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-ylityksen-siirto-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2025
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm hoitokauden-alkuvuosi)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                        ::valikatselmus/siirto 10000}))
                     (catch Exception e e))]
    (is (= "Tavoitehinnan ylityspäätös vaatii tavoitehinnan, tilaajan-maksun ja urajoitsijan-maksun." (-> vastaus ex-data :virheet :viesti)))))

(deftest tee-paatos-kattohinnan-ylityksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/urakoitsijan-maksu 20000}))]
    (is (= 20000M (::valikatselmus/urakoitsijan-maksu vastaus)))))

(deftest kattohinnan-ylitys-siirto
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/siirto 20000}))]
    (is (= 20000M (::valikatselmus/siirto vastaus)))))

(deftest kattohinnan-ylitys-siirto-viimeisena-vuotena
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2025
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm hoitokauden-alkuvuosi)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                        ::valikatselmus/siirto 20000}))
                     (catch Exception e e))]
    (= "Kattohinnan ylitystä ei voi siirtää ensi vuodelle urakan viimeisena vuotena" (-> vastaus ex-data :virheet :viesti))))

(deftest kattohinnan-ylityksen-maksu-onnistuu-viimeisena-vuotena
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2025
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm hoitokauden-alkuvuosi)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/urakoitsijan-maksu 20000}))]
    (is (= 20000M (::valikatselmus/urakoitsijan-maksu vastaus)))
    (is (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi vastaus)))))

(deftest paatosta-ei-voi-tehda-urakka-ajan-ulkopuolella
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2018
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                        ::valikatselmus/siirto 10000}))
                     (catch Exception e e))]
    (is (= "Urakan päätöksiä ei voi käsitellä urakka-ajan ulkopuolella" (-> vastaus ex-data :virheet :viesti)))))

(deftest tee-paatos-tavoitehinnan-alituksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                              q/hae-oikaistu-tavoitehinta (constantly 100000)
                              q/hae-oikaistu-kattohinta (constantly 110000)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                   ::valikatselmus/urakoitsijan-maksu -3000}))]
    (is (= -3000M (::valikatselmus/urakoitsijan-maksu vastaus)))))

(deftest tavoitehinnan-alitus-maksu-yli-kolme-prosenttia
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                   q/hae-oikaistu-tavoitehinta (constantly 13000)
                                   q/hae-oikaistu-kattohinta (constantly 14300)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                        ::valikatselmus/urakoitsijan-maksu -900
                                        ::valikatselmus/tilaajan-maksu -2100}))
                     (catch Exception e e))]
    (is (= "Urakoitsijalle maksettava summa ei saa ylittää 3% tavoitehinnasta" (-> vastaus ex-data :virheet :viesti)))))

(deftest lupausbonus-paatos-test-toimii
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        bonuksen-maara 1500M
        hoitokauden-alkuvuosi 2021
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                       {:lupaus-sitoutuminen {:pisteet 50}
                                                                                        :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                     :pisteet {:maksimi 100
                                                                                                               :ennuste 100
                                                                                                               :toteuma 100}
                                                                                                     :bonus-tai-sanktio {:bonus bonuksen-maara}
                                                                                                     :tavoitehinta 100000M
                                                                                                     :odottaa-kannanottoa 0
                                                                                                     :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-urakan-paatos
                      (kayttaja urakka-id)
                      {::urakka/id urakka-id
                       ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                       ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                       ::valikatselmus/tilaajan-maksu bonuksen-maara}))
                  (catch Exception e e))
        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupausbonus
        lupausbonus (first (erilliskustannus-kyselyt/hae-erilliskustannus (:db jarjestelma) {:urakka-id urakka-id
                                                                                             :id (::valikatselmus/erilliskustannus-id vastaus)}))]
    (is (= bonuksen-maara (::valikatselmus/tilaajan-maksu vastaus)) "Lupausbonuspäätöslukemat täsmää validoinnin jälkeen")
    (is (= {:bonus bonuksen-maara} (lupaus-palvelu/tallennettu-bonus-tai-sanktio (:db jarjestelma) urakka-id hoitokauden-alkuvuosi)) "Tallennetun bonuksen määrä pitäisi täsmätä")
    (is (= bonuksen-maara (:rahasumma lupausbonus)))))

(deftest lupausbonus-paatos-test-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        bonuksen-maara 1500M
        hoitokauden-alkuvuosi 2021
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc 2021))
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                       {:lupaus-sitoutuminen {:pisteet 50}
                                                                                        :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                     :pisteet {:maksimi 100
                                                                                                               :ennuste 100
                                                                                                               :toteuma 100}
                                                                                                     :bonus-tai-sanktio {:bonus 1}
                                                                                                     :tavoitehinta 100000M
                                                                                                     :odottaa-kannanottoa 0
                                                                                                     :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi 2021
                                     ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                                     ::valikatselmus/tilaajan-maksu bonuksen-maara}))
                  (catch Exception e e))]
    (is (= "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa." (-> vastaus ex-data :virheet :viesti)))))

(deftest lupaussanktio-paatos-test-toimii
  (let [db (:db jarjestelma)
        urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        _ (is (false? (lupaus-palvelu/valikatselmus-tehty-urakalle? db urakka-id)) "Välikatselmusta ei ole vielä tehty")
        _ (is (false? (lupaus-palvelu/valikatselmus-tehty-hoitokaudelle? db urakka-id hoitokauden-alkuvuosi)) "Välikatselmusta ei ole vielä tehty")
        _ (is (nil? (lupaus-palvelu/tallennettu-bonus-tai-sanktio db urakka-id hoitokauden-alkuvuosi)) "Bonus/sanktio ei ole vielä tallennettu")
        sanktion-maara -1500M
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                       {:lupaus-sitoutuminen {:pisteet 100}
                                                                                        :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                     :pisteet {:maksimi 100
                                                                                                               :ennuste 100
                                                                                                               :toteuma 50}
                                                                                                     :bonus-tai-sanktio {:sanktio sanktion-maara}
                                                                                                     :tavoitehinta 100000M
                                                                                                     :odottaa-kannanottoa 0
                                                                                                     :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaussanktio
                                     ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))
        ;; Kun tehdään lupaus päätös, siitä muodostetaan joko lupaussanktio tai lupausbonus, nyt on tehty lupausbonus
        lupaussanktio (first (sanktiot-q/hae-sanktio (:db jarjestelma) (::valikatselmus/sanktio-id vastaus)))]
    (is (= sanktion-maara (::valikatselmus/urakoitsijan-maksu vastaus)) "Lupaussanktiopäätöslukemat täsmää validoinnin jälkeen")
    (is (true? (lupaus-palvelu/valikatselmus-tehty-urakalle? db urakka-id)) "Välikatselmus pitäisi nyt olla tehty")
    (is (true? (lupaus-palvelu/valikatselmus-tehty-hoitokaudelle? db urakka-id hoitokauden-alkuvuosi)) "Välikatselmus pitäisi nyt olla tehty")
    (is (= {:sanktio sanktion-maara} (lupaus-palvelu/tallennettu-bonus-tai-sanktio db urakka-id hoitokauden-alkuvuosi)) "Tallennetun sanktion määrä pitäisi täsmätä")
    (is (= sanktion-maara (* -1 (:maara lupaussanktio))))))

(deftest lupaussanktio-paatos-test-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        sanktion-maara -1500M
        hoitokauden-alkuvuosi 2021
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                  {:lupaus-sitoutuminen {:pisteet 100}
                                                                                   :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                :pisteet {:maksimi 100
                                                                                                          :ennuste 100
                                                                                                          :toteuma 50}
                                                                                                :bonus-tai-sanktio {:sanktio -1}
                                                                                                :tavoitehinta 100000M
                                                                                                :odottaa-kannanottoa 0
                                                                                                :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaussanktio
                                     ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))]
    (is (= "Lupaussanktion urakoitsijan maksun summa ei täsmää lupauksissa lasketun sanktion kanssa." (-> vastaus ex-data :virheet :viesti)))))

(deftest lupaussanktio-paatos-test-epaonnistuu-tulevaisuuteen
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        sanktion-maara -1500M
        hoitokauden-alkuvuosi 2021
        vaara-vuosi 2023
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm vaara-vuosi) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
                                                                                  {:lupaus-sitoutuminen {:pisteet 100}
                                                                                   :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                :pisteet {:maksimi 100
                                                                                                          :ennuste 100
                                                                                                          :toteuma 50}
                                                                                                :bonus-tai-sanktio {:sanktio sanktion-maara}
                                                                                                :tavoitehinta 100000M
                                                                                                :odottaa-kannanottoa 0
                                                                                                :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaussanktio
                                     ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))]
    (is (= "Urakan päätöksiä saa käsitellä ainoastaan sallitulla aikavälillä." (-> vastaus ex-data :virheet :viesti)))))


(deftest lupausbonus-paatos-mh-2019-vuodelle-vuonna-2022-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        bonus-maara 1500M
        hoitokauden-alkuvuosi 2019
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle (fn [db kayttaja hakuparametrit]
                                                                                       {:lupaus-sitoutuminen {:pisteet 78}
                                                                                        :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                     :pisteet {:maksimi 100
                                                                                                               :ennuste 100
                                                                                                               :toteuma 82}
                                                                                                     :bonus-tai-sanktio {:bonus bonus-maara}
                                                                                                     :tavoitehinta 100000M
                                                                                                     :odottaa-kannanottoa 0
                                                                                                     :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                                     ::valikatselmus/tilaajan-maksu bonus-maara}))
                  (catch Exception e e))]
    (is (= (::valikatselmus/tilaajan-maksu vastaus) bonus-maara))))

(deftest lupausbonus-paatos-mh-2019-vuodelle-vuonna-2023-epaonnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        bonus-maara 1500M
        hoitokauden-alkuvuosi 2019
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2023) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle (fn [db kayttaja hakuparametrit]
                                                                                           {:lupaus-sitoutuminen {:pisteet 78}
                                                                                            :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                         :pisteet {:maksimi 100
                                                                                                                   :ennuste 100
                                                                                                                   :toteuma 82}
                                                                                                         :bonus-tai-sanktio {:bonus bonus-maara}
                                                                                                         :tavoitehinta 100000M
                                                                                                         :odottaa-kannanottoa 0
                                                                                                         :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                                     ::valikatselmus/tilaajan-maksu bonus-maara}))
                  (catch Exception e e))]
    (is (= "Urakan päätöksiä saa käsitellä ainoastaan sallitulla aikavälillä." (-> vastaus ex-data :virheet :viesti)))))

(deftest poista-lupausbonus-paatos-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        bonus-maara 1500M
        hoitokauden-alkuvuosi 2019
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle (fn [db kayttaja hakuparametrit]
                                                                                           {:lupaus-sitoutuminen {:pisteet 78}
                                                                                            :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                         :pisteet {:maksimi 100
                                                                                                                   :ennuste 100
                                                                                                                   :toteuma 82}
                                                                                                         :bonus-tai-sanktio {:bonus bonus-maara}
                                                                                                         :tavoitehinta 100000M
                                                                                                         :odottaa-kannanottoa 0
                                                                                                         :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                                     ::valikatselmus/tilaajan-maksu bonus-maara}))
                  (catch Exception e e))
        bonus-id (::valikatselmus/erilliskustannus-id vastaus)
        lupaus (first (q-map (format "select id, rahasumma, poistettu, urakka, tyyppi FROM erilliskustannus WHERE id = %s" bonus-id)))
        paatos-id (::valikatselmus/paatoksen-id vastaus)
        ;; Poistetaan päätös
        poisto-vastaus (try
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :poista-paatos
                                         (kayttaja urakka-id)
                                         {::valikatselmus/paatoksen-id paatos-id})
                         (catch Exception e e))
        poistettu-lupaus (first (q-map (format "select id, rahasumma, poistettu, urakka, tyyppi FROM erilliskustannus WHERE id = %s" bonus-id)))]
    (is (nil? (-> poisto-vastaus ex-data :virheet :viesti)))
    (is (= bonus-id (:id lupaus)))
    (is (= bonus-maara (:rahasumma lupaus)))
    (is (= false (:poistettu lupaus)))
    (is (= bonus-id (:id poistettu-lupaus)))
    (is (= bonus-maara (:rahasumma poistettu-lupaus)))
    (is (= true (:poistettu poistettu-lupaus)))))

(deftest poista-lupaussanktio-paatos-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        sanktion-maara -1500M
        hoitokauden-alkuvuosi 2019
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle (fn [db kayttaja hakuparametrit]
                                                                                           {:lupaus-sitoutuminen {:pisteet 100}
                                                                                            :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                         :pisteet {:maksimi 100
                                                                                                                   :ennuste 100
                                                                                                                   :toteuma 50}
                                                                                                         :bonus-tai-sanktio {:sanktio sanktion-maara}
                                                                                                         :tavoitehinta 100000M
                                                                                                         :odottaa-kannanottoa 0
                                                                                                         :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-urakan-paatos
                      (kayttaja urakka-id)
                      {::urakka/id urakka-id
                       ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                       ::valikatselmus/tyyppi ::valikatselmus/lupaussanktio
                       ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))
        sanktio-id (::valikatselmus/sanktio-id vastaus)
        sanktio (first (q-map (format "select id, maara, poistettu, tyyppi FROM sanktio WHERE id = %s" sanktio-id)))
        paatos-id (::valikatselmus/paatoksen-id vastaus)
        ;; Poistetaan päätös
        poisto-vastaus (try
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                           :poista-paatos
                           (kayttaja urakka-id)
                           {::valikatselmus/paatoksen-id paatos-id})
                         (catch Exception e e))
        poistettu-sanktio (first (q-map (format "select id, maara, poistettu, tyyppi FROM sanktio WHERE id = %s" sanktio-id)))]
    (is (nil? (-> poisto-vastaus ex-data :virheet :viesti)))
    (is (= sanktio-id (:id sanktio)))
    (is (= (* -1 sanktion-maara) (:maara sanktio)))         ; Sanktion eurot tallennetaan miinuksena kutsussa, mutta kantaan tallennetaan plussaa
    (is (= false (:poistettu sanktio)))
    (is (= sanktio-id (:id poistettu-sanktio)))
    (is (= (* -1 sanktion-maara) (:maara poistettu-sanktio))) ; Sanktion eurot tallennetaan miinuksena kutsussa, mutta kantaan tallennetaan plussaa
    (is (= true (:poistettu poistettu-sanktio)))))

(deftest poista-lupaus-paatos-epaonnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        bonus-maara 1500M
        hoitokauden-alkuvuosi 2019
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022) ;; Ollaan muka tulevaisuudessa ja tallennetaan menneisyyteen
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle (fn [db kayttaja hakuparametrit]
                                                                                           {:lupaus-sitoutuminen {:pisteet 78}
                                                                                            :yhteenveto {:ennusteen-tila :alustava-toteuma
                                                                                                         :pisteet {:maksimi 100
                                                                                                                   :ennuste 100
                                                                                                                   :toteuma 82}
                                                                                                         :bonus-tai-sanktio {:bonus bonus-maara}
                                                                                                         :tavoitehinta 100000M
                                                                                                         :odottaa-kannanottoa 0
                                                                                                         :merkitsevat-odottaa-kannanottoa 0}})]
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-urakan-paatos
                                    (kayttaja urakka-id)
                                    {::urakka/id urakka-id
                                     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     ::valikatselmus/tyyppi ::valikatselmus/lupausbonus
                                     ::valikatselmus/tilaajan-maksu bonus-maara}))
                  (catch Exception e e))
        paatos-id1 (rand-int 923424) ;; annetaan joku ihan random id, jota ei voi olla olemassa
        ;; Poistetaan päätös
        poisto-vastaus1 (try
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :poista-paatos
                                         (kayttaja urakka-id)
                                         {::valikatselmus/paatoksen-id paatos-id1})
                         (catch Exception e e))
        paatos-id2 nil
        ;; Poistetaan päätös
        poisto-vastaus2 (try
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :poista-paatos
                                         (kayttaja urakka-id)
                                         {::valikatselmus/paatoksen-id paatos-id2})
                         (catch Exception e e))]
    (is (not= poisto-vastaus1 paatos-id1))
    (is (= "Päätöksen id puuttuu!" (-> poisto-vastaus2 ex-data :virheet :viesti)))))
