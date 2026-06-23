(ns harja.palvelin.integraatiot.api.analytiikka-test
  (:require [clj-time.coerce :as t-coerce]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.turvallisuuspoikkeama :as turvallisuuspoikkeama]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
            [clojure.string :as str])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil nil nil) [:db])
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka false)
                       [:http-palvelin :db-replica :integraatioloki])
    :api-turvallisuuspoikkeama (component/using (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                 [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :each jarjestelma-fixture)

(defn poista-viimeisin-turpo []
  (let [;; Viimeisin turpo id
        id (:id (first (q-map (str "select id FROM turvallisuuspoikkeama order by id desc limit 1"))))
        ;; Haetaan mahdollinen liite-id
        liite-tulos (q-map (format "SELECT liite as liite_id FROM turvallisuuspoikkeama_liite WHERE turvallisuuspoikkeama = %s" id))
        liite-id (when-not (empty? liite-tulos)
                   (:liite_id (first liite-tulos)))
        _ (u (format "DELETE FROM turvallisuuspoikkeama_liite WHERE turvallisuuspoikkeama = %s", id))
        _ (when liite-id
            (u (format "DELETE FROM liite WHERE id = %s", liite-id)))
        _ (u (format "DELETE FROM turvallisuuspoikkeama_kommentti WHERE turvallisuuspoikkeama = %s", id))
        _ (u (format "DELETE FROM korjaavatoimenpide WHERE turvallisuuspoikkeama = %s", id))
        _ (u (format "DELETE FROM turvallisuuspoikkeama WHERE id = %s", id))]))

(defn sisaltaa-perustiedot [vastaus]
  (is (str/includes? vastaus "tyokone"))
  (is (str/includes? vastaus "materiaalit"))
  (is (str/includes? vastaus "reitti"))
  (is (str/includes? vastaus "poistettu"))
  (is (str/includes? vastaus "toteuma"))
  (is (str/includes? vastaus "muutostiedot"))
  (is (str/includes? vastaus "tehtavat"))
  (is (str/includes? vastaus "yksikko"))
  (is (str/includes? vastaus "tehtava")))

(deftest hae-toteumat-test-aikaraja-ylittyy
  ;; Rajapinta rajoitettu hakemaan max 24h aikavälin
  ;; Testataan että rajoitus toimii 
  (let [alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-20T00:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        vastaus-ok (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)
        ;; Asetetaan ajaksi yli 24 tuntia
        alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-21T00:00:00+03"
        vastaus-epaonnistuu (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)]
    ;; Ensimmäinen kutsi pitäisi mennä läpi
    (is (= 200 (:status vastaus-ok)))
    (sisaltaa-perustiedot (:body vastaus-ok))
    ;; Toisen pitäisi epäonnistua ja antaa virhekoodin
    (is (= 400 (:status vastaus-epaonnistuu)))
    (is (str/includes? (-> vastaus-epaonnistuu :body) "Aikaväli ylittää sallitun rajan"))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-toteumat-test-yksinkertainen-onnistuu
  (let [; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-20T00:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))
    (sisaltaa-perustiedot (:body vastaus))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-toteumat-test-reitillinen-onnistuu
  (let [alkuaika "2015-01-19T00:00:00+03"
        loppuaika "2015-01-19T21:00:00+03"
        ;; Poistetaan oikeudet 
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        ;; Näillä oikeuksilla ei pitäisi pystyä kutsumaan analytiikan rajapintoja 
        _ (anna-kirjoitusoikeus kayttaja-analytiikka)
        _ (anna-tielupaoikeus kayttaja-analytiikka)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)

        ;; Käyttäjällä ei ole analytiikkaoikeuksia 
        _ (is (= 403 (:status vastaus)) "Käyttäjältä ei löydy analytiikka api oikeuksia")
        _ (is (str/includes? (:body vastaus) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")

        ;; Annetaan oikeudet ja tehdään kutsu uudelleen
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))
    (sisaltaa-perustiedot (:body vastaus))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-toteumat-test-reitillinen-onnistuu-2
  (let [alkuaika-paiva-sitten (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* 1 86400 1000))))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        ; Poistetaan oikeudet 
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika-paiva-sitten "/" loppuaika)] kayttaja-analytiikka portti)
        ;; Käyttäjällä ei ole analytiikkaoikeuksia 
        _ (is (= 403 (:status vastaus)) "Käyttäjältä ei löydy analytiikka api oikeuksia")
        _ (is (str/includes? (:body vastaus) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")

        ;; Annetaan oikeudet ja tehdään kutsu uudelleen
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        uusi_vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika-paiva-sitten "/" loppuaika)] kayttaja-analytiikka portti)
        status (:status uusi_vastaus)
        lyhennetty-vastaus (subs (:body uusi_vastaus) 0 (min 2000 (count (:body uusi_vastaus))))]
    (is (= 200 status))
    ;; Tämä antaa 7 virhettä, mikäli lokaali kanta on liian vanha. Resetoi tietokanta, niin ongelmat korjautuu
     (sisaltaa-perustiedot lyhennetty-vastaus)
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-toteumat-test-ei-kayttoikeutta
  (let [kuukausi-sitten (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* 30 86400 1000))))
        nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" kuukausi-sitten "/" nyt)] kayttaja-yit portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "virheet"))
    (is (str/includes? (:body vastaus) "koodi"))
    (is (str/includes? (:body vastaus) "kayttajalla-puutteelliset-oikeudet"))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-toteumat-test-vaara-paivamaaraformaatti
  (let [alkuaika "2005-01-01T00:00:00"
        loppuaika "2005-12-31T21:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Alkuaika väärässä muodossa"))))

(deftest hae-toteumat-test-poistettu-onnistuu
  (let [random-paiva (pvm/ajan-muokkaus (pvm/luo-pvm 2004 3 12) false 1 :paiva)
        alkupaiva (t-coerce/to-sql-time (pvm/paivan-alussa random-paiva))
        alkupaiva-plus3-sql (t-coerce/to-sql-time (pvm/ajan-muokkaus alkupaiva true 3 :tunti))
        loppupaiva (t-coerce/to-sql-time (pvm/paivan-lopussa random-paiva))
        ;; Merkitään toteuma poistetuksi - ja muokkaus tapahtumaan eilen
        _ (u (format "UPDATE toteuma SET poistettu = true, muokattu = '%s' WHERE id = 9;" alkupaiva-plus3-sql))
        ;; Tyhjennä analytiikka_toteumat taulu
        _ (u "DELETE FROM analytiikka_toteumat;")
        ;; Päivitetään analytiikka_toteumat taulun tiedot
        _ (q (format "SELECT siirra_toteumat_analytiikalle('%s'::TIMESTAMP WITH TIME ZONE, '%s'::TIMESTAMP WITH TIME ZONE)"
               alkupaiva loppupaiva))
        _ (Thread/sleep 500)
        toteuma_maara (:maara (first (q-map "SELECT count(*) as maara from analytiikka_toteumat;")))

        ;; Haetaan poistetut, jotka on muuttuneet tänään (eli muokkauksen jälkeen)
        paivan-alussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (harja.kyselyt.konversio/java-date alkupaiva))
        paivan-lopussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (harja.kyselyt.konversio/java-date loppupaiva))
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)

        poistetut (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" paivan-alussa "/" paivan-lopussa)] kayttaja-analytiikka portti)
        _ (u (str "UPDATE toteuma SET poistettu = false, muokattu = null WHERE id = 9;"))
        _ (Thread/sleep 3500)
        poistetut-body (-> (:body poistetut)
                         (json/read-str)
                         (clojure.walk/keywordize-keys))]
    (is (= 200 (:status poistetut)))
    (is (= 1 toteuma_maara))                                ;; Vain yhtä muokattiin
    (is (= true (:poistettu (first (:reittitoteumat poistetut-body)))))))

(deftest hae-toteumat-test-koordinaattimuunnos-toimii
  (let [alkuaika "2015-12-17T00:00:00+03"
        loppuaika "2015-12-17T23:59:59+03"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika "/true/50")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))
    (sisaltaa-perustiedot (:body vastaus))
    ;; Sisältää lisäksi koordinaattimuunnoksen
    (is (true? (str/includes? (:body vastaus) "koordinaatit-4326")))))

(deftest hae-toteumat-test-koko-liian-pieni
  (let [alkuaika "2015-01-19T00:00:00+03"
        loppuaika "2015-01-19T21:00:00+03"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika "/true/0.001")] kayttaja-analytiikka portti)]
    (is (= 400 (:status vastaus)))
    (is (true? (str/includes? (:body vastaus) "Virhe: Liian suuri aineisto palautettavaksi.")))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest materiaalin-maara-muuttuu
  (let [paiva-alussa-plus3-sql (t-coerce/to-sql-time (pvm/ajan-muokkaus (pvm/luo-pvm 2004 9 19) true 3 :tunti))
        paiva-alussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-alussa (pvm/luo-pvm 2004 9 19)))
        paiva-lopussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-lopussa (pvm/luo-pvm 2004 9 19)))

        _ (anna-analytiikkaoikeus kayttaja-analytiikka)

        ;; Haetaan alkuperäinen tieto
        alkup-vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" paiva-alussa "/" paiva-lopussa)] kayttaja-analytiikka portti)
        alkup-vastaus-body (-> (:body alkup-vastaus)
                             (json/read-str)
                             (clojure.walk/keywordize-keys))

        toteuma-9 (first (filter
                           #(when (= 9 (get-in % [:toteuma :tunniste :id]))
                              %)
                           (:reittitoteumat alkup-vastaus-body)))

        ;; Muokkaa materiaalin muokattu aikaa ja määrää
        uusi-maara 114022
        _ (u (format "UPDATE toteuma_materiaali set muokattu = '%s', maara= %s where toteuma=9; " paiva-alussa-plus3-sql uusi-maara))
        _ (q (format "SELECT siirra_toteumat_analytiikalle('%s'::TIMESTAMP WITH TIME ZONE, '%s'::TIMESTAMP WITH TIME ZONE)"
               paiva-alussa paiva-lopussa))

        muokattu-vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" paiva-alussa "/" paiva-lopussa)] kayttaja-analytiikka portti)
        muokattu-vastaus-body (-> (:body muokattu-vastaus)
                                (json/read-str)
                                (clojure.walk/keywordize-keys))

        toteuma-9-muokattu (first (filter
                                    #(when (= 9 (get-in % [:toteuma :tunniste :id]))
                                       %)
                                    (:reittitoteumat muokattu-vastaus-body)))

        ;; Vaihda määrä takaisin 
        _ (u (format "UPDATE toteuma_materiaali set muokattu = '%s', maara=25 where toteuma = 9;" paiva-alussa-plus3-sql))
        _ (q (format "SELECT siirra_toteumat_analytiikalle('%s'::TIMESTAMP WITH TIME ZONE, '%s'::TIMESTAMP WITH TIME ZONE)"
               paiva-alussa paiva-lopussa))

        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" paiva-alussa "/" paiva-lopussa)] kayttaja-analytiikka portti)
        vastaus-body (-> (:body vastaus)
                       (json/read-str)
                       (clojure.walk/keywordize-keys))

        toteuma-9-lopullinen (first (filter
                                      #(when (= 9 (get-in % [:toteuma :tunniste :id]))
                                         %)
                                      (:reittitoteumat vastaus-body)))

        ;; Rajapinnan vastauksissa pitäisi olla nyt eri määrät, muokattu määrä on ensimmäinen arvo
        alkup-maara (get-in toteuma-9 [:toteuma :materiaalit 0 :maara :maara])
        muokattu-maara (get-in toteuma-9-muokattu [:toteuma :materiaalit 0 :maara :maara])
        lopullinen-maara (get-in toteuma-9-lopullinen [:toteuma :materiaalit 0 :maara :maara])]

    (is (= alkup-maara lopullinen-maara))
    (is (= uusi-maara muokattu-maara))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest hae-turvallisuuspoikkeamat-analytiikalle-ei-kayttajaa
  (let [alkuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        _ (anna-analytiikkaoikeus "olematonkäyttäjä")
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  "olematonkäyttäjä" portti)]
    ;; Harjan käyttöoikeuksien tarkistuksessa on virhe, joka aiheuttaa 500 errorin, jos käytetään haussa käyttäjää, jota ei ole olemassa.
    (is (= 500 (:status vastaus)))
    (is (str/includes? (:body vastaus) "tuntematon-kayttaja"))))

(deftest hae-turvallisuuspoikkeamat-analytiikalle-ei-kayttoikeutta
  (let [alkuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  kayttaja-yit portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "kayttajalla-puutteelliset-oikeudet"))))

(deftest hae-turvallisuuspoikkeamat-analytiikalle-onnistuu
  (let [;; Luo väliaikainen turvallisuuspoikkeama
        tapahtuma-paiva "2016-01-30T12:00:01Z"
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        _ (anna-kirjoitusoikeus "yit-rakennus")
        _ (anna-analytiikkaoikeus "analytiikka-testeri")
        _ (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/turvallisuuspoikkeama"]
            "yit-rakennus" portti
            (-> "test/resurssit/api/turvallisuuspoikkeama.json"
              slurp
              (.replace "__PAIKKA__" "Liukas tie keskellä metsää.")
              (.replace "__TAPAHTUMAPAIVAMAARA__" tapahtuma-paiva)))

        ;; Hae turvallisuuspoikkeamat uuden apin kautta
        alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 50)
        loppuaika (nykyhetki-iso8061-formaatissa-tulevaisuuteen 1)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)

        ;; Poista väliaikainen turvallisuuspoikkeama
        _ (poista-viimeisin-turpo)]
    (is (= 200 (:status vastaus)))
    ;; Tarkistetaan vain, että saadaan pitkä vastaus
    (is (< 1000 (count (:body vastaus))))))

(deftest hae-muokattu-turvallisuuspoikkeama-analytiikalle-onnistuu
  (let [;; Luo väliaikainen turvallisuuspoikkeama
        tapahtuma-paiva "2016-01-30T12:00:01Z"
        paikkakuvaus "Aivan superyksilöllinen paikkakuvaus"
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        _ (anna-kirjoitusoikeus "yit-rakennus")
        _ (anna-analytiikkaoikeus "analytiikka-testeri")
        _ (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/turvallisuuspoikkeama"]
            "yit-rakennus" portti
            (-> "test/resurssit/api/turvallisuuspoikkeama.json"
              slurp
              (.replace "__PAIKKA__" paikkakuvaus)
              (.replace "__TAPAHTUMAPAIVAMAARA__" tapahtuma-paiva)))

        ;; Hae turvallisuuspoikkeamat uuden apin kautta
        alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 50)
        loppuaika (nykyhetki-iso8061-formaatissa-tulevaisuuteen 1)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)
        body (cheshire/decode (:body vastaus))

        ;; Muokataan tietokantaan muokattu ja luotu -kentät, ja katsotaan löydetäänkö turpo vaikka luotu aika ei enää osukaan aikamäärävälille
        luotu-hetki-sitten (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja (* 12000))
        muokattu-nyt (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja 1)
        _ (u (format "update turvallisuuspoikkeama SET luotu = '%s', muokattu = '%s' WHERE paikan_kuvaus = '%s'",
               luotu-hetki-sitten
               muokattu-nyt
               paikkakuvaus))
        muokattu-vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                           "analytiikka-testeri" portti)
        muokattu-body (cheshire/decode (:body muokattu-vastaus))

        tapahtumakasittely1 (get (first (get body "turvallisuuspoikkeamat")) "tapahtumankasittely")
        tapahtumakasittely2 (get (first (get muokattu-body "turvallisuuspoikkeamat")) "tapahtumankasittely")

        ;; Poistetaan molemmista tapahtumakäsittelyt, koska siellä on luontipäivä muuttunut
        body (update body "turvallisuuspoikkeamat" (fn [turvallisuuspoikkeamat]
                                                     (map #(dissoc % "tapahtumankasittely") turvallisuuspoikkeamat)))
        muokattu-body (update body "turvallisuuspoikkeamat" (fn [turvallisuuspoikkeamat]
                                                              (map #(dissoc % "tapahtumankasittely") turvallisuuspoikkeamat)))
        ;; Poista väliaikainen turvallisuuspoikkeama
        _ (poista-viimeisin-turpo)]
    (is (= 200 (:status vastaus)))
    (is (= body muokattu-body))
    (is (not= tapahtumakasittely1 tapahtumakasittely2))))


(deftest hae-turvallisuuspoikkeamat-analytiikalle-epaonnistuu
  (let [;; Luo väliaikainen turvallisuuspoikkeama
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        _ (anna-analytiikkaoikeus "234")
        _ (anna-analytiikkaoikeus "yit-rakennus")
        _ (anna-analytiikkaoikeus "analytiikka-testeri")
        _ (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/turvallisuuspoikkeama"]
            "yit-rakennus" portti
            (-> "test/resurssit/api/turvallisuuspoikkeama.json"
              slurp
              (.replace "__PAIKKA__" "Liukas tie keskellä metsää.")
              (.replace "__TAPAHTUMAPAIVAMAARA__" "2016-01-30T12:00:00Z")))]
    (testing "Alkuaika on väärässä muodossa "
      (let [alkuaika "234"
            loppuaika (nykyhetki-iso8061-formaatissa-tulevaisuuteen 10)
            vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                      "analytiikka-testeri" portti)
            odotettu-vastaus "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-aikavali\",\"viesti\":\"Alkuaika väärässä muodossa: 234 Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03\"}}]}"]
        (is (= 400 (:status vastaus)))
        (is (= odotettu-vastaus (:body vastaus)))))
    (testing "Loppuaika on väärässä muodossa "
      (let [alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 50000)
            loppuaika "234"
            vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                      "analytiikka-testeri" portti)
            odotettu-vastaus "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-aikavali\",\"viesti\":\"Loppuaika väärässä muodossa: 234 Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-02T00:00:00+03\"}}]}"]
        (is (= 400 (:status vastaus)))
        (is (= odotettu-vastaus (:body vastaus)))))
    (testing "Haussa on paljon asioita väärin "
      (let [alkuaika "Mies joka tunki koodia päivämäärään."
            loppuaika "Voi olla autuaan tietämätön väärästä päätöksestään."
            vastaus (try (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                           "234" portti)
                      (catch Exception e
                        e))]
        (is (str/includes? vastaus "URISyntaxException"))))

    ;; Poista väliaikainen turvallisuuspoikkeama
    _ (poista-viimeisin-turpo)))

(deftest hae-turpot-analytiikalle-tiedosto-onnistuu
  (let [tapahtumahetki (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 10)
        alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 11)
        loppuaika (nykyhetki-iso8061-formaatissa-tulevaisuuteen 1)

        ;; Luo väliaikainen turvallisuuspoikkeama
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        _ (anna-kirjoitusoikeus "yit-rakennus")
        _ (anna-analytiikkaoikeus "analytiikka-testeri")
        _ (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/turvallisuuspoikkeama"]
            "yit-rakennus" portti
            (-> "test/resurssit/api/turvallisuuspoikkeama.json"
              slurp
              (.replace "__PAIKKA__" "Liukas tie keskellä metsää.")
              (.replace "__TAPAHTUMAPAIVAMAARA__" tapahtumahetki)))

        ;; Hae apista
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)
        vastaus-body (-> (:body vastaus)
                       (json/read-str)
                       (clojure.walk/keywordize-keys))]

    ;; Poista väliaikainen turvallisuuspoikkeama
    _ (poista-viimeisin-turpo)
    ;; Oletetaan että ensimmäisellä on tiedosto
    (is (not (nil? (:tiedosto (first (:poikkeamaliite (first (:turvallisuuspoikkeamat vastaus-body))))))))))

(deftest hae-turpot-analytiikalle-kaikki-tiedot-onnistuu
  (let [tapahtumahetki (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 10)
        alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 11)
        loppuaika (nykyhetki-iso8061-formaatissa-tulevaisuuteen 1)

        ;; Luo väliaikainen turvallisuuspoikkeama
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        _ (anna-kirjoitusoikeus "yit-rakennus")
        _ (anna-analytiikkaoikeus "analytiikka-testeri")
        _ (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/turvallisuuspoikkeama"]
            "yit-rakennus" portti
            (-> "test/resurssit/api/turvallisuuspoikkeama.json"
              slurp
              (.replace "__PAIKKA__" "Liukas tie keskellä metsää.")
              (.replace "__TAPAHTUMAPAIVAMAARA__" tapahtumahetki)))

        lahetetty-turpo (first (:turvallisuuspoikkeamat (-> (slurp "test/resurssit/api/turvallisuuspoikkeama.json")
                                                          (json/read-str)
                                                          (clojure.walk/keywordize-keys))))
        ;; Hae apista
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)
        vastaus-body (-> (:body vastaus)
                       (json/read-str)
                       (clojure.walk/keywordize-keys))
        turpo (first (:turvallisuuspoikkeamat vastaus-body))]

    ;; Poista väliaikainen turvallisuuspoikkeama
    _ (poista-viimeisin-turpo)

    ;; Varmista, että vastaanotetut tiedot täsmäävät lähetettäviin tietoihin
    ;; Syyt ja seuraukset
    (is (true? (lahes-sama? (get-in lahetetty-turpo [:juurisyy1 :juurisyy]) (get-in turpo [:syytjaseuraukset :juurisyy1]))))
    (is (lahes-sama? (get-in lahetetty-turpo [:henkilovahinko :tyontekijanammatti]) (get-in turpo [:syytjaseuraukset :ammatti])))
    (is (= (get-in lahetetty-turpo [:henkilovahinko :ammatinselite]) (get-in turpo [:syytjaseuraukset :ammattimuutarkenne])))
    (is (= (get-in lahetetty-turpo [:henkilovahinko :sairauspoissaolopaivat]) (get-in turpo [:syytjaseuraukset :sairauspoissaolot])))
    (is (= (get-in lahetetty-turpo [:seuraukset]) (get-in turpo [:syytjaseuraukset :seuraukset])))
    ;; Lähetettävässä datassa voi olla useita vammautuneita paikkoja. Meidän järjestelmä tukee vain yhtä
    (is (lahes-sama? (first (get-in lahetetty-turpo [:henkilovahinko :vahingoittuneetRuumiinosat])) (str/lower-case (get-in turpo [:syytjaseuraukset :vahingoittunutruumiinosa]))))
    ;; Myöskään vammoja tallennetaan vain yksi, vaikka niitä lähetettäisiin useita
    (is (lahes-sama? (first (get-in lahetetty-turpo [:henkilovahinko :aiheutuneetVammat])) (str/lower-case (get-in turpo [:syytjaseuraukset :vammanlaatu]))))

    ;; Tapahtumatiedot
    (is (= (get-in lahetetty-turpo [:vaylamuoto]) (str/lower-case (get-in turpo [:tapahtumantiedot :urakkavaylamuoto]))))
    (is (= (:kuvaus lahetetty-turpo) (get-in turpo [:tapahtumantiedot :kuvaus])))

    ;; Tapahtumakäsittely
    (is (= (get-in lahetetty-turpo [:otsikko]) (get-in turpo [:tapahtumankasittely :otsikko])))

    ;; Poikkeamatoimenpide
    (is (= (get-in lahetetty-turpo [:korjaavatToimenpiteet 0 :otsikko]) (get-in turpo [:poikkeamatoimenpide 0 :otsikko])))
    (is (= (get-in lahetetty-turpo [:korjaavatToimenpiteet 0 :toteuttaja]) (get-in turpo [:poikkeamatoimenpide 0 :toteuttaja])))
    (is (= (get-in lahetetty-turpo [:korjaavatToimenpiteet 0 :kuvaus]) (get-in turpo [:poikkeamatoimenpide 0 :kuvaus])))

    ;; Tapahtumapaikka
    (is (= (get-in lahetetty-turpo [:sijainti :tie :numero]) (get-in turpo [:tapahtumapaikka :tienumero])))
    (is (= (get-in lahetetty-turpo [:sijainti :tie :aosa]) (get-in turpo [:tapahtumapaikka :tieaosa])))
    (is (= (get-in lahetetty-turpo [:sijainti :tie :aet]) (get-in turpo [:tapahtumapaikka :tieaet])))
    (is (= (get-in lahetetty-turpo [:sijainti :tie :losa]) (get-in turpo [:tapahtumapaikka :tielosa])))
    (is (= (get-in lahetetty-turpo [:sijainti :tie :let]) (get-in turpo [:tapahtumapaikka :tielet])))))

(deftest api-analytiikka-suunnitellut-tehtavat-virheellinen-alkuvuosi-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-tehtavat/abc/2023")] kayttaja-analytiikka portti)]
    (is (= 400 (:status vastaus)))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest api-analytiikka-suunnitellut-tehtavat-virheellinen-loppuvuosi-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-tehtavat/2020/xyz")] kayttaja-analytiikka portti)]
    ;; API palauttaa 500 kun konversio->int epäonnistuu
    (is (= 500 (:status vastaus)))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest api-analytiikka-suunnitellut-tehtavat-alkuvuosi-suurempi-kuin-loppuvuosi-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-tehtavat/2023/2020")] kayttaja-analytiikka portti)]
    (is (= 400 (:status vastaus)))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest api-analytiikka-suunnitellut-tehtavat-onnistunut-kutsu-test
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-tehtavat/2020/2023")] kayttaja-analytiikka portti)
        body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (sequential? body))
    ;; Tarkista että palautetaan urakan tiedot
    (when (seq body)
      (let [ensimmainen (first body)]
        (is (contains? ensimmainen :urakka))
        (is (contains? ensimmainen :urakka-id))
        (is (contains? ensimmainen :vuosittaiset-suunnitelmat))
        (is (sequential? (:vuosittaiset-suunnitelmat ensimmainen)))))))

(deftest api-analytiikka-suunnitellut-tehtavat-tietojen-validointi-test
  ;; Testaa että tehtävämäärät haetaan oikein
  (let [vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/suunnitellut-tehtavat/2019/2024")] kayttaja-analytiikka portti)
        body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (sequential? body))
    ;; Tarkista että ainakin joiltain urakoilta löytyy suunnitelmia
    (when (seq body)
      (let [ensimmainen (first body)]
        (is (contains? ensimmainen :vuosittaiset-suunnitelmat))
        (is (sequential? (:vuosittaiset-suunnitelmat ensimmainen)))
        (when (seq (:vuosittaiset-suunnitelmat ensimmainen))
          (let [vuosi (first (:vuosittaiset-suunnitelmat ensimmainen))]
            (is (contains? vuosi :hoitokauden-alkuvuosi))
            (is (contains? vuosi :suunnitellut-tehtavat))
            (is (sequential? (:suunnitellut-tehtavat vuosi)))))))))

;; Testit toteumat-ilman-reittipisteita API:lle

(defn validoi-vastaus-ilman-reittipistetta
  "Varmistaa että vastaus sisältää odotetut kentät ilman reittipisteitä"
  [vastaus-teksti]
  (is (str/includes? vastaus-teksti "toteumat"))
  (is (str/includes? vastaus-teksti "toteuma"))
  (is (str/includes? vastaus-teksti "materiaalit"))
  (is (str/includes? vastaus-teksti "tehtavat"))
  (is (str/includes? vastaus-teksti "poistettu"))
  (is (str/includes? vastaus-teksti "muutostiedot"))
  (is (not (str/includes? vastaus-teksti "reitti"))))

(deftest toteuma-haku-ilman-reittipistetta-aikavali-rajoitus
  (let [alku "2004-10-19T00:00:00+03"
        loppu "2004-10-20T00:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        sallittu-haku (api-tyokalut/get-kutsu
                        [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku "/" loppu)]
                        kayttaja-analytiikka portti)

        alku-pitka "2004-10-19T00:00:00+03"
        loppu-pitka "2004-10-21T02:00:00+03"
        hylattava-haku (api-tyokalut/get-kutsu
                         [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-pitka "/" loppu-pitka)]
                         kayttaja-analytiikka portti)

        alku-ei-validi "2005-10-19T00:00:00+03"
        loppu-ei-validi "2004-10-21T02:00:00+03"
        hylattava-aikavali (api-tyokalut/get-kutsu
                             [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-ei-validi "/" loppu-ei-validi)]
                             kayttaja-analytiikka portti)]

    (is (= 200 (:status sallittu-haku)))
    (validoi-vastaus-ilman-reittipistetta (:body sallittu-haku))
    (is (= 400 (:status hylattava-haku)))
    (is (str/includes? (:body hylattava-haku) "Aikaväli ylittää sallitun rajan"))
    (is (= 400 (:status hylattava-aikavali)))
    (is (str/includes? (:body hylattava-aikavali) "Alkuaika ei voi olla loppuajan jälkeen."))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest toteuma-haku-ilman-reittipistetta-perustoiminnallisuus
  (let [alku-aika "2004-10-19T00:00:00+03"
        loppu-aika "2004-10-20T00:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        tulos (api-tyokalut/get-kutsu
                [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-aika "/" loppu-aika)]
                kayttaja-analytiikka portti)]
    (is (= 200 (:status tulos)))
    (validoi-vastaus-ilman-reittipistetta (:body tulos))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest toteuma-haku-ilman-reittipistetta-puutteelliset-oikeudet
  (let [alku-aika "2004-10-19T00:00:00+03"
        loppu-aika "2004-10-20T00:00:00+03"
        hylattava (api-tyokalut/get-kutsu
                    [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-aika "/" loppu-aika)]
                    kayttaja-yit portti)]
    (is (= 403 (:status hylattava)))
    (is (str/includes? (:body hylattava) "virheet"))
    (is (str/includes? (:body hylattava) "kayttajalla-puutteelliset-oikeudet"))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest toteuma-haku-ilman-reittipistetta-virheellinen-aikaformaatti
  (let [vaara-alku "2005-01-01T00:00:00"
        oikea-loppu "2005-12-31T21:00:00+03"
        _ (anna-analytiikkaoikeus kayttaja-analytiikka)
        virheellinen (api-tyokalut/get-kutsu
                       [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" vaara-alku "/" oikea-loppu)]
                       kayttaja-analytiikka portti)]
    (is (= 400 (:status virheellinen)))
    (is (str/includes? (:body virheellinen) "Alkuaika väärässä muodossa"))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))

(deftest toteuma-haku-ilman-reittipistetta-oikeustarkastus
  (let [alku-hetki "2015-01-19T00:00:00+03"
        loppu-hetki "2015-01-19T21:00:00+03"
        _ (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
        _ (anna-kirjoitusoikeus kayttaja-analytiikka)
        _ (anna-tielupaoikeus kayttaja-analytiikka)
        kielletty (api-tyokalut/get-kutsu
                    [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-hetki "/" loppu-hetki)]
                    kayttaja-analytiikka portti)]
    (is (= 403 (:status kielletty)))
    (is (str/includes? (:body kielletty) "Käyttäjätunnuksella puutteelliset oikeudet"))
    (poista-kayttajan-api-oikeudet kayttaja-analytiikka)
    (anna-analytiikkaoikeus kayttaja-analytiikka)
    (let [sallittu (api-tyokalut/get-kutsu
                     [(str "/api/analytiikka/toteumat-ilman-reittipisteita/" alku-hetki "/" loppu-hetki)]
                     kayttaja-analytiikka portti)]
      (is (= 200 (:status sallittu)))
      (validoi-vastaus-ilman-reittipistetta (:body sallittu)))
    ;; Annetaan async integraatioloki-säikeelle aikaa valmistua
    ;; ennen kuin fixture sulkee DB-poolin
    (Thread/sleep 300)))
