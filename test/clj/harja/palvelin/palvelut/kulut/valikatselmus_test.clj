(ns harja.palvelin.palvelut.kulut.valikatselmus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.kyselyt.valikatselmus :as q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kulut.valikatselmukset :as valikatselmukset]
            [harja.palvelin.palvelut.lupaukset-tavoitteet.lupaukset :as lupaukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all])
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

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn filtteroi-oikaisut-selitteella [oikaisut selite]
  (filter #(= selite (::valikatselmus/selite %))
          oikaisut))

(defn kayttaja [urakka-id]
  (assoc +kayttaja-tero+
    :urakkaroolit {urakka-id #{"ELY_Urakanvalvoja"}}))

;;Oikaisut
(deftest tavoitehinnan-oikaisu-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        ;; With-redefsillä laitetaan (pvm/nyt) palauttamaan tietty ajankohta. Tämä sen takia, että
        ;; rajapinta antaa virheen, mikäli kutsuhetkellä ei saa tehdä tavoitehinnan oikaisuja.
        ;; Tätä tulee käyttää varoen, koska tämä ylirjoittaa kaikki (pvm/nyt) kutsut blokin sisällä, joita saattaa
        ;; tapahtua pinnan alla.
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/summa 9001
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (some? vastaus))
    (is (= (::valikatselmus/summa vastaus) 9001M))))

(deftest oikaisun-teko-epaonnistuu-alkuvuodesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        virheellinen-vastaus (try
                               (with-redefs [pvm/nyt #(pvm/luo-pvm 2022 5 20)]
                                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :tallenna-tavoitehinnan-oikaisu
                                                 (kayttaja urakka-id)
                                                 {::urakka/id urakka-id
                                                  ::valikatselmus/otsikko "Oikaisu"
                                                  ::valikatselmus/hoitokauden-alkuvuosi 2019
                                                  ::valikatselmus/summa 1000
                                                  ::valikatselmus/selite "Juhannusmenot hidasti"}))
                               (catch Exception e e))]
    (is (= ExceptionInfo (type virheellinen-vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa käsitellä ainoastaan aikavälillä 1.9. - 31.12." (-> virheellinen-vastaus ex-data :virheet :viesti)))))

(deftest virheellisen-oikaisun-teko-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id]
    (is (thrown? Exception (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-tavoitehinnan-oikaisu
                                             (kayttaja urakka-id)
                                             {::urakka/id urakka-id
                                              ::valikatselmus/hoitokauden-alkuvuosi 2019
                                              ::valikatselmus/otsikko "Oikaisu"
                                              ::valikatselmus/summa "Kolmesataa"
                                              ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))))))

(deftest muokkaa-tavoitehinnan-oikaisua
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        oikaisut (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tavoitehintojen-oikaisut
                                      (kayttaja urakka-id)
                                      {::urakka/id urakka-id}) 2021)
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  (assoc muokattava-oikaisu ::valikatselmus/summa 50000)))]
    (is (= 1 vastaus) "Summan muokkaus ei onnistunut")

    (let [oikaisut-jalkeen (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-tavoitehintojen-oikaisut
                                                (kayttaja urakka-id)
                                                {::urakka/id urakka-id}) 2021)
          muokattu-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut-jalkeen "Muokattava testioikaisu"))]
      (is (= 50000M (::valikatselmus/summa muokattu-oikaisu))))))

(deftest tavoitehinnan-oikaisun-muokkaus-ei-onnistu-tammikuussa
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        oikaisut (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tavoitehintojen-oikaisut
                                      (kayttaja urakka-id)
                                      {::urakka/id urakka-id}) 2021)
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (try (with-redefs [pvm/nyt #(pvm/luo-pvm 2022 0 15)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       (kayttaja urakka-id)
                                       (assoc muokattava-oikaisu ::valikatselmus/summa 1)))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa käsitellä ainoastaan aikavälillä 1.9. - 31.12." (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisun-poisto-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-tavoitehintojen-oikaisut
                        +kayttaja-jvh+
                        {::urakka/id urakka-id})
        poistettava (first (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %))
                                   (get (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-tavoitehintojen-oikaisut
                                                        +kayttaja-jvh+
                                                        {::urakka/id urakka-id}) 2021)))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
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
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 9001
                                        ::valikatselmus/hoitokauden-alkuvuosi 2019
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa tehdä ainoastaan teiden hoitourakoille" (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisu-onnistuu-urakanvalvojalla
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa 12345
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (= 12345M (::valikatselmus/summa vastaus)))))

(deftest tavoitehinnan-oikaisu-epaonnistuu-sepolla
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       +kayttaja-seppo+
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 12345
                                        ::valikatselmus/hoitokauden-alkuvuosi 2019
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch ExceptionInfo e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= EiOikeutta (type (ex-data vastaus))))))

(deftest tavoitehinnan-miinusmerkkinen-oikaisu-onnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa -2000
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/selite "Seppo kävi töissä, päällystykset valmistui odotettua nopeampaa"}))]
    (is (= -2000M (::valikatselmus/summa vastaus)))))

;; Päätökset
(deftest tee-paatos-tavoitehinnan-ylityksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/tilaajan-maksu 7000.00
                                   ::valikatselmus/urakoitsijan-maksu 3000.00}))]
    (is (= 7000M (::valikatselmus/tilaajan-maksu vastaus)))
    (is (= 2019 (::valikatselmus/hoitokauden-alkuvuosi vastaus)))))

(deftest muokkaa-tavoitehinnan-ylityksen-paatosta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        luotu (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-paatos
                                (kayttaja urakka-id)
                                {::urakka/id urakka-id
                                 ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                 ::valikatselmus/hoitokauden-alkuvuosi 2021
                                 ::valikatselmus/tilaajan-maksu 7000.00
                                 ::valikatselmus/urakoitsijan-maksu 3000.00}))
        muokattu (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-urakan-paatos
                                   (kayttaja urakka-id)
                                   {::urakka/id urakka-id
                                    ::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id luotu)
                                    ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                    ::valikatselmus/hoitokauden-alkuvuosi 2021
                                    ::valikatselmus/tilaajan-maksu 14000.00
                                    ::valikatselmus/urakoitsijan-maksu 6000.00}))]
    (is (= 14000M (::valikatselmus/tilaajan-maksu muokattu)))
    (is (= 2021 (::valikatselmus/hoitokauden-alkuvuosi muokattu)))))

(deftest tavoitehinnan-ylityksen-siirto-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2026)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi 2026
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                        ::valikatselmus/siirto 10000}))
                     (catch Exception e e))]
    (is (= "Tavoitehinnan ylitystä ei voi siirtää ensi vuodelle" (-> vastaus ex-data :virheet :viesti)))))

(deftest tee-paatos-kattohinnan-ylityksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/urakoitsijan-maksu 20000}))]
    (is (= 20000M (::valikatselmus/urakoitsijan-maksu vastaus)))))

(deftest kattohinnan-ylitys-siirto
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/siirto 20000}))]
    (is (= 20000M (::valikatselmus/siirto vastaus)))))

(deftest kattohinnan-ylitys-siirto-viimeisena-vuotena
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2025)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi 2025
                                        ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                        ::valikatselmus/siirto 20000}))
                     (catch Exception e e))]
    (= "Kattohinnan ylitystä ei voi siirtää ensi vuodelle urakan viimeisena vuotena" (-> vastaus ex-data :virheet :viesti))))

(deftest kattohinnan-ylityksen-maksu-onnistuu-viimeisena-vuotena
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2025)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi 2025
                                   ::valikatselmus/tyyppi ::valikatselmus/kattohinnan-ylitys
                                   ::valikatselmus/urakoitsijan-maksu 20000}))]
    (is (= 20000M (::valikatselmus/urakoitsijan-maksu vastaus)))
    (is (= 2025 (::valikatselmus/hoitokauden-alkuvuosi vastaus)))))

(deftest paatosta-ei-voi-tehda-urakka-ajan-ulkopuolella
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2019)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi 2018
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                        ::valikatselmus/siirto 10000}))
                     (catch Exception e e))]
    (is (= "Urakan päätöksiä ei voi käsitellä urakka-ajan ulkopuolella" (-> vastaus ex-data :virheet :viesti)))))

(deftest tee-paatos-tavoitehinnan-alituksesta
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                              q/hae-oikaistu-tavoitehinta (constantly 100000)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  (kayttaja urakka-id)
                                  {::urakka/id urakka-id
                                   ::valikatselmus/hoitokauden-alkuvuosi 2019
                                   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                   ::valikatselmus/urakoitsijan-maksu -3000}))]
    (is (= -3000M (::valikatselmus/urakoitsijan-maksu vastaus)))))

(deftest tavoitehinnan-alitus-maksu-yli-kolme-prosenttia
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                                   q/hae-oikaistu-tavoitehinta (constantly 13000)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-urakan-paatos
                                       (kayttaja urakka-id)
                                       {::urakka/id urakka-id
                                        ::valikatselmus/hoitokauden-alkuvuosi 2019
                                        ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-alitus
                                        ::valikatselmus/urakoitsijan-maksu -900
                                        ::valikatselmus/tilaajan-maksu -2100}))
                     (catch Exception e e))]
    (is (= "Urakoitsijalle maksettava summa ei saa ylittää 3% tavoitehinnasta" (-> vastaus ex-data :virheet :viesti)))))

(deftest lupaus-bonus-paatos-test-toimii
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        bonuksen-maara 1500M
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                               ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                               lupaukset/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
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
                                    ::valikatselmus/hoitokauden-alkuvuosi 2019
                                    ::valikatselmus/tyyppi ::valikatselmus/lupaus-bonus
                                    ::valikatselmus/tilaajan-maksu bonuksen-maara}))
                  (catch Exception e e))]
    (is (= bonuksen-maara (::valikatselmus/tilaajan-maksu vastaus)) "Lupausbonuspäätöslukemat täsmää validoinnin jälkeen")))

(deftest lupaus-bonus-paatos-test-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        bonuksen-maara 1500M
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaukset/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
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
                                     ::valikatselmus/hoitokauden-alkuvuosi 2019
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaus-bonus
                                     ::valikatselmus/tilaajan-maksu bonuksen-maara}))
                  (catch Exception e e))]
    (is (= "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa." (-> vastaus ex-data :virheet :viesti)))))

(deftest lupaus-sanktio-paatos-test-toimii
  (let [db (:db jarjestelma)
        urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        hoitokauden-alkuvuosi 2021
        _ (is (false? (lupaukset/valikatselmus-tehty-urakalle? db urakka-id)) "Välikatselmusta ei ole vielä tehty")
        _ (is (false? (lupaukset/valikatselmus-tehty-hoitokaudelle? db urakka-id hoitokauden-alkuvuosi)) "Välikatselmusta ei ole vielä tehty")
        sanktion-maara -1500M
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaukset/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
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
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaus-sanktio
                                     ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))]
    (is (= sanktion-maara (::valikatselmus/urakoitsijan-maksu vastaus)) "Lupaussanktiopäätöslukemat täsmää validoinnin jälkeen")
    (is (true? (lupaukset/valikatselmus-tehty-urakalle? db urakka-id)) "Välikatselmus pitäisi nyt olla tehty")
    (is (true? (lupaukset/valikatselmus-tehty-hoitokaudelle? db urakka-id hoitokauden-alkuvuosi)) "Välikatselmus pitäisi nyt olla tehty")))

(deftest lupaus-sanktio-paatos-test-epaonnistuu
  (let [urakka-id @iin-maanteiden-hoitourakan-2021-2026-id
        sanktion-maara -1500M
        vastaus (try
                  (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2022)
                                ;; Feikataan vastaus lupausten hakemiseen, koska kenelläkään ei oikein ole testidatassa valmiita lupausvastauksia
                                lupaukset/hae-urakan-lupaustiedot-hoitokaudelle (fn [db hakuparametrit]
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
                                     ::valikatselmus/hoitokauden-alkuvuosi 2019
                                     ::valikatselmus/tyyppi ::valikatselmus/lupaus-sanktio
                                     ::valikatselmus/urakoitsijan-maksu sanktion-maara}))
                  (catch Exception e e))]
    (is (= "Lupaussanktion urakoitsijan maksun summa ei täsmää lupauksissa lasketun sanktion kanssa." (-> vastaus ex-data :virheet :viesti)))))