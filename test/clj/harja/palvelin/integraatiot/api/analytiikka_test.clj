(ns harja.palvelin.integraatiot.api.analytiikka-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
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
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
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
  (is (str/includes? vastaus "reittipiste"))
  (is (str/includes? vastaus "yksikko"))
  (is (str/includes? vastaus "tehtava"))
  (is (str/includes? vastaus "muutostiedot")))

(deftest hae-toteumat-test-aikaraja-ylittyy
  ;; Rajapinta rajoitettu hakemaan max 24h aikavälin
  ;; Testataan että rajoitus toimii 
  (let [alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-20T00:00:00+03"
        vastaus-ok (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))

        ;; Asetetaan ajaksi yli 24 tuntia
        alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-21T00:00:00+03"
        vastaus-epaonnistuu (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    
    ;; Ensimmäinen kutsi pitäisi mennä läpi
    (is (= 200 (:status @vastaus-ok)))
    (sisaltaa-perustiedot (:body @vastaus-ok))
    ;; Toisen pitäisi epäonnistua ja antaa virhekoodin
    (is (= 400 (:status @vastaus-epaonnistuu)))
    (is (str/includes? (-> @vastaus-epaonnistuu :body) "Aikaväli ylittää sallitun rajan"))))

(deftest hae-toteumat-test-yksinkertainen-onnistuu
  (let [; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-10-19T00:00:00+03"
        loppuaika "2004-10-20T00:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 200 (:status @vastaus)))
    (sisaltaa-perustiedot (:body @vastaus))))

(deftest hae-toteumat-test-reitillinen-onnistuu
  (let [alkuaika "2015-01-19T00:00:00+03"
        loppuaika "2015-01-19T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 200 (:status @vastaus)))
    (sisaltaa-perustiedot (:body @vastaus))))

(deftest hae-toteumat-test-ei-kayttoikeutta
  (let [kuukausi-sitten (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* 30 86400 1000))))
        nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" kuukausi-sitten "/" nyt)] kayttaja-yit portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "virheet"))
    (is (str/includes? (:body vastaus) "koodi"))
    (is (str/includes? (:body vastaus) "tuntematon-kayttaja"))))

(deftest hae-toteumat-test-vaara-paivamaaraformaatti
  (let [alkuaika "2005-01-01T00:00:00"
        loppuaika "2005-12-31T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 400 (:status @vastaus)))
    (is (str/includes? (:body @vastaus) "Alkuaika väärässä muodossa"))))


(deftest hae-toteumat-test-poistettu-onnistuu
  (let [;; Merkitään toteuma poistetuksi
        _ (u (str "UPDATE toteuma SET poistettu = true, muokattu = NOW() WHERE id = 9;"))
        ;; Päivitetään analytiikka_toteumat taulun tiedot
        _ (q (str "SELECT siirra_toteumat_analytiikalle(NOW()::TIMESTAMP WITH TIME ZONE)"))
        ;; Haetaan poistetut, jotka on muuttuneet tänään (eli muokkauksen jälkeen)
        paivan-alussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-alussa (pvm/nyt)))
        paivan-lopussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-lopussa (pvm/nyt)))
        
        poistetut (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" paivan-alussa "/" paivan-lopussa)] kayttaja-analytiikka portti))
        _ (u (str "UPDATE toteuma SET poistettu = false, muokattu = null WHERE id = 9;"))
        _ (Thread/sleep 3500)
        poistetut-body (-> (:body @poistetut)
                         (json/read-str)
                         (clojure.walk/keywordize-keys))]
    (is (= 200 (:status @poistetut)))
    (sisaltaa-perustiedot (:body @poistetut))
    (is (= true (:poistettu (first (:reittitoteumat poistetut-body)))))))

(deftest materiaalin-maara-muuttuu
  (let [paiva-alussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-alussa (pvm/luo-pvm 2004 9 19)))
        paiva-lopussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-lopussa (pvm/luo-pvm 2004 9 19)))

        nyt-paivan-alussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-alussa (pvm/nyt)))
        nyt-paivan-lopussa (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (pvm/paivan-lopussa (pvm/nyt)))

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
        _ (u (format "UPDATE toteuma_materiaali set muokattu = NOW(), maara=%s where toteuma=9" uusi-maara))
        _ (q (str "SELECT siirra_toteumat_analytiikalle(NOW()::TIMESTAMP WITH TIME ZONE)"))

        muokattu-vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" nyt-paivan-alussa "/" nyt-paivan-lopussa)] kayttaja-analytiikka portti))
        muokattu-vastaus-body (-> (:body @muokattu-vastaus)
                                (json/read-str)
                                (clojure.walk/keywordize-keys))

        toteuma-9-muokattu (first (filter
                                    #(when (= 9 (get-in % [:toteuma :tunniste :id]))
                                       %)
                                    (:reittitoteumat muokattu-vastaus-body)))

        ;; Vaihda määrä takaisin 
        _ (u (str "UPDATE toteuma_materiaali set muokattu = NOW(), maara=25 where toteuma=9"))
        _ (q (str "SELECT siirra_toteumat_analytiikalle(NOW()::TIMESTAMP WITH TIME ZONE)"))

        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" nyt-paivan-alussa "/" nyt-paivan-lopussa)] kayttaja-analytiikka portti))
        vastaus-body (-> (:body @vastaus)
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
    (is (= uusi-maara muokattu-maara))))

(deftest hae-turvallisuuspoikkeamat-analytiikalle-ei-kayttajaa
  (let [alkuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
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
    (is (str/includes? (:body vastaus) "tuntematon-kayttaja"))))

(deftest hae-turvallisuuspoikkeamat-analytiikalle-onnistuu
  (let [;; Luo väliaikainen turvallisuuspoikkeama
        tapahtuma-paiva "2016-01-30T12:00:01Z"
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
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

(deftest hae-turvallisuuspoikkeamat-analytiikalle-epaonnistuu
  (let [;; Luo väliaikainen turvallisuuspoikkeama
        urakka (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
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
            odotettu-vastaus "{\"virheet\":[{\"virhe\":{\"koodi\":\"puutteelliset-parametrit\",\"viesti\":\"Alkuaika väärässä muodossa: 234 Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03\"}}]}"]
        (is (= 400 (:status vastaus)))
        (is (= odotettu-vastaus (:body vastaus)))))
    (testing "Loppuaika on väärässä muodossa "
      (let [alkuaika (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 50000)
            loppuaika "234"
            vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/turvallisuuspoikkeamat/" alkuaika "/" loppuaika)]
                      "analytiikka-testeri" portti)
            odotettu-vastaus "{\"virheet\":[{\"virhe\":{\"koodi\":\"puutteelliset-parametrit\",\"viesti\":\"Loppuaika väärässä muodossa: 234 Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-02T00:00:00+03\"}}]}"]
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
