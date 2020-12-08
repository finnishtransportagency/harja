(ns harja.palvelin.palvelut.tilannekuva-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tilannekuva :refer :all]
            [harja.palvelin.palvelut.interpolointi :as interpolointi]
            [harja.testi :refer :all]
            [harja.paneeliapurit :as paneeli]
            [harja.domain.oikeudet :as oikeudet]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.tilannekuva :as tk]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.transit :as transit]
            [clojure.core.async :refer [<!!]])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :karttakuvat (component/using
                                       (karttakuvat/luo-karttakuvat)
                                       [:http-palvelin :db])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :tilannekuva (component/using
                                       (->Tilannekuva)
                                       [:http-palvelin :db :karttakuvat :fim])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures jarjestelma-fixture urakkatieto-fixture))

(def alku (c/to-date (t/local-date 2000 1 1)))
(def loppu (c/to-date (t/local-date 2030 1 1)))
(def urakoitsija nil)
(def urakkatyyppi :hoito)

(def hakuargumentit-laaja-historia
  {:urakoitsija urakoitsija
   :urakkatyyppi urakkatyyppi
   :nykytilanne? false
   :alue {:xmin -550093.049087613, :ymin 6372322.595126259,
          :xmax 1527526.529326106, :ymax 7870243.751025201} ; Koko Suomi
   :alku alku
   :loppu loppu
   :yllapito {tk/paallystys true
              tk/paikkaus true
              tk/tietyomaat true
              tk/paaasfalttilevitin true
              tk/tiemerkintakone true
              tk/kuumennuslaite true
              tk/sekoitus-ja-stabilointijyrsin true
              tk/tma-laite true
              tk/jyra true}
   :ilmoitukset {:tyypit {tk/tpp true
                          tk/urk true
                          tk/tur true}
                 :tilat #{:kuittaamaton :vastaanotto :aloitus :lopetus :muutos :vastaus}}
   :turvallisuus {tk/turvallisuuspoikkeamat true}
   :tietyoilmoitukset {tk/tietyoilmoitukset true}
   :laatupoikkeamat {tk/laatupoikkeama-tilaaja true
                     tk/laatupoikkeama-urakoitsija true
                     tk/laatupoikkeama-konsultti true}
   :tarkastukset {tk/tarkastus-tiesto true
                  tk/tarkastus-talvihoito true
                  tk/tarkastus-soratie true
                  tk/tarkastus-laatu true
                  tk/tilaajan-laadunvalvonta true}
   :talvi {tk/auraus-ja-sohjonpoisto true
           tk/suolaus true
           tk/pistehiekoitus true
           tk/linjahiekoitus true
           tk/lumivallien-madaltaminen true
           tk/sulamisveden-haittojen-torjunta true
           tk/aurausviitoitus-ja-kinostimet true
           tk/lumensiirto true
           tk/paannejaan-poisto true
           tk/muu true
           tk/pinnan-tasaus true}
   :kesa {tk/koneellinen-niitto true
          tk/koneellinen-vesakonraivaus true
          tk/liikennemerkkien-puhdistus true
          tk/liikennemerkkien-opasteiden-ja-liikenteenohjauslaitteiden-hoito-seka-reunapaalujen-kunnossapito true
          tk/palteen-poisto true
          tk/paallystetyn-tien-sorapientareen-taytto true
          tk/ojitus true
          tk/sorapientareen-taytto true
          tk/sorateiden-muokkaushoylays true
          tk/sorateiden-polynsidonta true
          tk/sorateiden-tasaus true
          tk/sorastus true
          tk/harjaus true
          tk/paallysteiden-paikkaus true
          tk/paallysteiden-juotostyot true
          tk/siltojen-puhdistus true
          tk/l-ja-p-alueiden-puhdistus true
          tk/muu true}})

(def parametrit-laaja-nykytilanne (assoc hakuargumentit-laaja-historia :nykytilanne? true))

(defn aseta-filtterit-falseksi [parametrit ryhma]
  (assoc parametrit ryhma (reduce
                            (fn [eka toka]
                              (assoc eka toka false))
                            (ryhma parametrit)
                            (keys (ryhma parametrit)))))

(defn hae-urakat-tilannekuvaan [kayttaja parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-tilannekuvaan kayttaja
                  {:nykytilanne? (:nykytilanne? parametrit)
                   :alku (:alku parametrit)
                   :loppu (:loppu parametrit)
                   :urakoitsija (:urakoitsija parametrit)
                   :urakkatyyppi (:urakkatyyppi parametrit)}))

(defn hae-tk
  ([hakuargumentit] (hae-tk +kayttaja-jvh+ hakuargumentit nil))
  ([kayttaja hakuargumentit] (hae-tk kayttaja hakuargumentit nil))
  ([kayttaja hakuargumentit urakka-idt]
   (let [haun-urakka-idt (or urakka-idt
                             (mapcat
                               (fn [aluekokonaisuus]
                                 (map :id (:urakat aluekokonaisuus)))
                               (hae-urakat-tilannekuvaan kayttaja hakuargumentit)))]
     (kutsu-palvelua (:http-palvelin jarjestelma)
                     :hae-tilannekuvaan kayttaja
                     (tk/valitut-suodattimet (assoc hakuargumentit
                                               :urakat (set haun-urakka-idt)))))))

(defn hae-klikkaus
  ([koordinaatti taso suodattimet] (hae-klikkaus +kayttaja-jvh+ koordinaatti taso suodattimet))
  ([kayttaja [x y] taso suodattimet]
   (let [extent 350]
     (kutsu-palvelua (:http-palvelin jarjestelma) :karttakuva-klikkaus kayttaja
                     {:koordinaatti [x y]
                      :extent [(- x extent) (- y extent) (+ x extent) (+ y extent)]
                      :parametrit {"ind" "1"
                                   "_" (name taso)
                                   "tk" (-> suodattimet
                                            transit/clj->transit
                                            (java.net.URLEncoder/encode))}}))))

(deftest hae-asioita-tilannekuvaan
  (let [vastaus (hae-tk hakuargumentit-laaja-historia)]
    (is (= (count (:toteumat vastaus)) 2))
    ;; Testaa, että toteuma selitteissä on enemmän kuin 1 toimenpidekoodi
    (is (> (count (distinct (map :toimenpidekoodi (:selitteet (:toteumat vastaus))))) 1))
    (is (= (count (:turvallisuuspoikkeamat vastaus)) 7))
    (is (not (contains? vastaus :tarkastus)))
    (is (= (count (:laatupoikkeamat vastaus)) 47))
    (is (= (count (:paallystys vastaus)) 1))
    (is (= (count (:paikkaus vastaus)) 6))
    (is (= (count (:ilmoitukset vastaus)) 48))
    (is (= (count (:tietyomaat vastaus)) 1))
    (is (= (count (:tietyoilmoitukset vastaus)) 4))))

(deftest ala-hae-laatupoikkeamia
  (let [parametrit (aseta-filtterit-falseksi hakuargumentit-laaja-historia :laatupoikkeamat)
        vastaus (hae-tk parametrit)]
    (is (= (count (:laatupoikkeamat vastaus)) 0))))

(deftest ala-hae-toteumia
  (let [parametrit (-> hakuargumentit-laaja-historia
                       (aseta-filtterit-falseksi :kesa)
                       (aseta-filtterit-falseksi :talvi))
        vastaus (hae-tk parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

;; Urakkatyyppi ei vaikuta enää hakutuloksiin
(deftest urakkatyyppi-ei-vaikuta-hakutuloksiin
  (let [parametrit-paallystys (assoc hakuargumentit-laaja-historia :urakkatyyppi :paallystys)
        vastaus (hae-tk hakuargumentit-laaja-historia)
        vastaus-paallystys (hae-tk parametrit-paallystys)]
    (is (= (set (:selitteet (:toteumat vastaus)))
           (set (:selitteet (:toteumat vastaus-paallystys)))))))

(deftest ala-hae-tarkastuksia
  (let [parametrit (aseta-filtterit-falseksi hakuargumentit-laaja-historia :tarkastukset)
        vastaus (hae-tk parametrit)]
    (is (= (count (:tarkastukset vastaus)) 0))))

(deftest ala-hae-turvallisuuspoikkeamia
  (let [parametrit (aseta-filtterit-falseksi hakuargumentit-laaja-historia :turvallisuus)
        vastaus (hae-tk parametrit)]
    (is (= (count (:turvallisuus vastaus)) 0))))

(deftest ala-hae-tietyoilmoituksia
  (let [parametrit (aseta-filtterit-falseksi hakuargumentit-laaja-historia :tietyoilmoitukset)
        vastaus (hae-tk parametrit)]
    (is (= (count (:tietyoilmoitukset vastaus)) 0))))

(deftest ala-hae-ilmoituksia
  (let [parametrit (assoc hakuargumentit-laaja-historia :ilmoitukset {:tyypit {:toimenpidepyynto false
                                                                               :kysely false
                                                                               :tiedoitus false}
                                                                      :tilat #{:avoimet :suljetut}})
        vastaus (hae-tk parametrit)]
    (is (= (count (:ilmoitukset vastaus)) 0))))

(deftest ala-hae-tyokoneita-historianakymaan
  (let [vastaus (hae-tk hakuargumentit-laaja-historia)]
    (is (= (count (:tyokoneet vastaus)) 0))))

(deftest loyda-vahemman-asioita-tiukalla-aikavalilla
  (let [vastaus-pitka-aikavali (hae-tk hakuargumentit-laaja-historia)
        parametrit (-> hakuargumentit-laaja-historia
                       (assoc :alku (c/to-date (t/local-date 2005 1 1)))
                       (assoc :loppu (c/to-date (t/local-date 2010 1 1))))
        vastaus-lyhyt-aikavali (hae-tk parametrit)]
    (is (< (count (:selitteet (:toteumat vastaus-lyhyt-aikavali)))
           (count (:selitteet (:toteumat vastaus-pitka-aikavali)))))))

(deftest hae-tyokoneet-nykytilaan
  (let [parametrit (assoc hakuargumentit-laaja-historia :nykytilanne? true)
        vastaus (hae-tk parametrit)]
    ;; Työkonetehtäviä löytyi
    (is (not (empty? (:tehtavat (:tyokoneet vastaus)))))))

(deftest ala-hae-toteumia-liian-lahelle-zoomatussa-historianakymassa
  (let [parametrit (assoc hakuargumentit-laaja-historia :alue {:xmin 0,
                                                               :ymin 0,
                                                               :xmax 1,
                                                               :ymax 1})
        vastaus (hae-tk parametrit)]
    (is (= (count (:selitteet (:toteumat vastaus))) 0))))

(deftest ala-hae-tyokoneita-liian-lahelle-zoomatussa-nykytilannenakymassa
  (let [parametrit (-> hakuargumentit-laaja-historia
                       (assoc :alue {:xmin 0,
                                     :ymin 0,
                                     :xmax 1,
                                     :ymax 1})
                       (assoc :nykytilanne? true))
        vastaus (hae-tk parametrit)]
    (is (empty? (:tehtavat (:tyokoneet vastaus))))))

(defn- insert-tyokone [urakka organisaatio]
  (let [x 523892
        y 7229981
        sql (str "INSERT INTO tyokonehavainto "
                 "(tyokoneid, jarjestelma, organisaatio, viestitunniste,lahetysaika,tyokonetyyppi,"
                 "sijainti,urakkaid,tehtavat) "
                 "VALUES (666, 'yksikkötesti', " organisaatio ",666,NOW(),'yksikkötesti',"
                 "ST_MakePoint(" x ", " y ")::GEOMETRY, "
                 (if urakka urakka "NULL") ", '{harjaus}')")]
    (u sql)))


(deftest vain-tilaaja-ja-urakoitsija-itse-nakee-urakattomat-tyokoneet
  (let [parametrit (assoc hakuargumentit-laaja-historia :nykytilanne? true)
        urakoitsija (hae-oulun-alueurakan-2005-2012-urakoitsija)
        hae #(let [vastaus (hae-tk % parametrit)
                   tehtavat (:tehtavat (:tyokoneet vastaus))]
               (tehtavat #{"harjaus"}))]
    ;; Insert menee ok
    (is (= 1 (insert-tyokone nil urakoitsija)) "Urakattoman työkonehavainnon voi insertoida")

    ;; jvh näkee työkoneen
    (is (hae +kayttaja-jvh+) "jvh näkee työkoneen")

    ;; ely käyttäjä näkee
    (is (hae +kayttaja-tero+) "ELYläinen näkee työkoneen")

    ;; saman urakoitsijaorganisaation käyttäjä näkee työkoneen
    (is (hae +kayttaja-yit_uuvh+) "Saman urakoitsijan käyttäjä näkee työkoneen")


    ;; eri urakoitsijaorganisaation käyttä ei näe työkonetta
    (is (not (hae +kayttaja-ulle+)) "Eri urakoitsijan käyttäjä ei näe työkonetta")))


(deftest toteuman-klikkauksen-aika-interpolointi
  (let [asia-sisaan {:tierekisteriosoite
                     {:numero 4,
                      :alkuosa 364,
                      :alkuetaisyys 3308,
                      :loppuosa 403,
                      :loppuetaisyys 2780},
                     :alkanut #inst "2015-02-01T17:00:00.000000000-00:00",
                     :tehtava
                     {:id 10, :toimenpide "Suolaus", :yksikko "tiekm", :maara 123M},
                     :paattynyt #inst "2015-02-01T18:05:00.000000000-00:00",
                     :suorittaja {:nimi "Tehotekijät Oy"},
                     :tyyppi-kartalla :toteuma,
                     :id 10,
                     :toimenpide "Suolaus"}
        parametrit-sisaan {:y 7214206.908812937,
                           :talvi #{27 24 50 51 25 34 23 35 26 52 49},
                           :toleranssi 1198.8469917446225,
                           "ind" "1",
                           "_" "tilannekuva-toteumat",
                           :ilmoitukset {},
                           :urakat #{4},
                           :x 427840.00001579785,
                           :alku #inst "2015-01-25T00:00:00.000-00:00",
                           :nykytilanne? false,
                           :loppu #inst "2017-01-25T23:59:59.000-00:00"}
        asia-ulos (interpolointi/interpoloi-toteuman-aika-pisteelle asia-sisaan parametrit-sisaan (:db jarjestelma))
        interpoloitu-aika (:aika-pisteessa asia-ulos)
        pisteen-tr-osoite (:tierekisteriosoite asia-ulos)]

    (is (some? interpoloitu-aika))
    (is (= interpoloitu-aika #inst "2015-02-01T15:17:23.112-00:00"))
    (is (= {:numero 4, :alkuosa 403, :alkuetaisyys 173} pisteen-tr-osoite))))

(deftest infopaneelin-skeemojen-luonti
  (testing "Frontilla piirrettäville jutuille saadaan tehtyä skeemat."
    (let [vastaus (hae-tk hakuargumentit-laaja-historia)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tietyomaa (:tietyomaat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? (map
                                                       #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
                                                       (:ilmoitukset vastaus))))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :laatupoikkeama (:laatupoikkeamat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :turvallisuuspoikkeama (:turvallisuuspoikkeamat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tietyoilmoitus (:tietyoilmoitukset vastaus)))))

  (testing "Infopaneeli saadaan luotua myös palvelimella piirretyille asioille."
    (let [toteuma (kutsu-karttakuvapalvelua
                    (:http-palvelin jarjestelma)
                    :tilannekuva-toteumat +kayttaja-jvh+
                    {:alku alku :loppu loppu :talvi #{27 24 50 51 25 34 23 35 26 52 49} :urakat #{4}}
                    [447806 7191966] nil)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :toteuma toteuma))
      (is (not (paneeli/skeeman-luonti-onnistuu-kaikille? :laatupoikkeama toteuma))))

    (let [tarkastus (kutsu-karttakuvapalvelua
                      (:http-palvelin jarjestelma)
                      :tilannekuva-tarkastukset +kayttaja-jvh+
                      {:ilmoitukset {}, :urakat #{4}, :tarkastukset #{7 6 9 8}, :alku #inst "2015-12-31T22:00:00.000-00:00", :nykytilanne? false, :loppu #inst "2016-12-31T21:59:59.000-00:00"}
                      [436352 7216512] nil)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tarkastus tarkastus))
      (is (not (paneeli/skeeman-luonti-onnistuu-kaikille? :toteuma tarkastus))))

    (let [tyokone (kutsu-karttakuvapalvelua
                    (:http-palvelin jarjestelma)
                    :tilannekuva-tyokoneet +kayttaja-jvh+
                    {:talvi #{27 24 50 51 25 34 23 35 26 52 49} :aikavalinta 504, :ilmoitukset {}, :urakat #{4}, :x 429312, :nykytilanne? true}
                    [429312 7208832] nil)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tyokone tyokone))
      (is (not (paneeli/skeeman-luonti-onnistuu-kaikille? :laatupoikkeama tyokone))))))

(deftest klikatun-paallystyksen-infopaneeli
  (let [urakat (set (map :id (q-map "SELECT id FROM urakka")))
        ei-loydy-koordinaatti [392327.9999989789 7212239.931808539]
        ei-loydy-vastaus (hae-klikkaus ei-loydy-koordinaatti :tilannekuva-paallystys
                                       (assoc hakuargumentit-laaja-historia
                                         :urakat urakat))
        loytyy-koordinaatti [445582.99999998405 7224316.998934508]
        loytyy-vastaus (hae-klikkaus loytyy-koordinaatti :tilannekuva-paallystys
                                     (assoc hakuargumentit-laaja-historia
                                       :urakat urakat))]

    (is (= [] ei-loydy-vastaus) "Ahvenanmaan keskeltä ei löydy päällystyskohteita")

    (is (= 2 (count loytyy-vastaus)) "Yksi kohde löytyy pisteelle")

    (let [oulun-ohitusramppi (first (filter #(= "Oulun ohitusramppi" (get-in % [:yllapitokohde :nimi])) loytyy-vastaus))]
      (is (= "Oulun ohitusramppi" (get-in  oulun-ohitusramppi [:yllapitokohde :nimi] )))
      (is (= "308a" (get-in  oulun-ohitusramppi [:yllapitokohde :kohdenumero])))
      (is (= "Oulun kohdeosa" (:nimi oulun-ohitusramppi))))

    (is (paneeli/skeeman-luonti-onnistuu-kaikille? loytyy-vastaus))))

(defn- poista-urakan-alue
  "Poistaa Tilannekuvan aluekokonaisuudesta geometrian, jotta nopeampi assertoida."
  [aluekokonaisuudet]
  (map
    (fn [aluekokonaisuus]
      (assoc aluekokonaisuus
        :urakat (map (fn [u]
                       (dissoc u :alue))
                     (:urakat aluekokonaisuus))))
    aluekokonaisuudet))

(deftest hae-urakat-tilannekuvaan-jvh
  (let [vastaus (hae-urakat-tilannekuvaan +kayttaja-jvh+ hakuargumentit-laaja-historia)
        elynumerot (set (distinct (keep #(get-in % [:hallintayksikko :elynumero]) vastaus)))

        odotettu-ilman-alueita '({:tyyppi :paallystys, :hallintayksikko {:id 7, :nimi "Kaakkois-Suomi", :elynumero 3}, :urakat ({:id 19, :nimi "Tienpäällystysurakka KAS ELY 1 2015", :urakkanro "TIEPAA124"})} {:tyyppi :paallystys, :hallintayksikko {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero 12}, :urakat ({:id 5, :nimi "Muhoksen päällystysurakka", :urakkanro "muho1"} {:id 8, :nimi "YHA-päällystysurakka", :urakkanro "YHA1"} {:id 7, :nimi "Utajärven päällystysurakka", :urakkanro "uta1"} {:id 28, :nimi "Aktiivinen Oulu Päällystys Testi", :urakkanro "ouluPaa"} {:id 24, :nimi "Oulun päällystyksen palvelusopimus", :urakkanro "3003"} {:id 10, :nimi "YHA-päällystysurakka (sidottu)", :urakkanro "YHA3"})} {:tyyppi :hoito, :hallintayksikko {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero 12}, :urakat ({:id 1, :nimi "Oulun alueurakka 2005-2012", :urakkanro "1250"} {:id 4, :nimi "Oulun alueurakka 2014-2019", :urakkanro "1238"} {:id 27, :nimi "Aktiivinen Kajaani Testi", :urakkanro "12502"} {:id 35, :nimi "Oulun MHU 2019-2024", :urakkanro "1238"} {:id 26, :nimi "Aktiivinen Oulu Testi", :urakkanro "12501"} {:id 21, :nimi "Kajaanin alueurakka 2014-2019", :urakkanro "1236"} {:id 2, :nimi "Pudasjärven alueurakka 2007-2012", :urakkanro "1229"})} {:tyyppi :valaistus, :hallintayksikko {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero 12}, :urakat ({:id 16, :nimi "Kempeleen valaistusurakka", :urakkanro "valai1"} {:id 20, :nimi "Tievalaistuksen palvelusopimus 2015-2020", :urakkanro "TIEVALAISTUS"} {:id 13, :nimi "Oulun valaistuksen palvelusopimus 2013-2050", :urakkanro "9991"})} {:tyyppi :paallystys, :hallintayksikko {:id 6, :nimi "Varsinais-Suomi", :elynumero 2}, :urakat ({:id 11, :nimi "Porintien päällystysurakka", :urakkanro "PORI"})} {:tyyppi :tiemerkinta, :hallintayksikko {:id 7, :nimi "Kaakkois-Suomi", :elynumero 3}, :urakat ({:id 18, :nimi "Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017", :urakkanro "TIE600"})} {:tyyppi :hoito, :hallintayksikko {:id 8, :nimi "Pirkanmaa", :elynumero 4}, :urakat ({:id 25, :nimi "Tampereen alueurakka 2017-2022", :urakkanro "tre123"})} {:tyyppi :paikkaus, :hallintayksikko {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero 12}, :urakat ({:id 9, :nimi "YHA-paikkausurakka", :urakkanro "YHA2"} {:id 17, :nimi "Muhoksen paikkausurakka", :urakkanro "muho2"})} {:tyyppi :tiemerkinta, :hallintayksikko {:id 13, :nimi "Lappi", :elynumero 14}, :urakat ({:id 15, :nimi "Lapin tiemerkinnän palvelusopimus 2013-2018", :urakkanro "LAPPI123"})} {:tyyppi :tiemerkinta, :hallintayksikko {:id 8, :nimi "Pirkanmaa", :elynumero 4}, :urakat ({:id 14, :nimi "Pirkanmaan tiemerkinnän palvelusopimus 2013-2018", :urakkanro "tiem1"})} {:tyyppi :hoito, :hallintayksikko {:id 13, :nimi "Lappi", :elynumero 14}, :urakat ({:id 34, :nimi "Ivalon MHU testiurakka (uusi)", :urakkanro "13374"} {:id 33, :nimi "Kemin MHU testiurakka (5. hoitovuosi)", :urakkanro "13373"} {:id 32, :nimi "Pellon MHU testiurakka (3. hoitovuosi)", :urakkanro "13372"} {:id 31, :nimi "Rovaniemen MHU testiurakka (1. hoitovuosi)", :urakkanro "13371"})} {:tyyppi :vesivayla-hoito, :hallintayksikko {:id 3, :nimi "Meriväylät", :elynumero nil}, :urakat ({:id 37, :nimi "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL", :urakkanro "2"} {:id 36, :nimi "Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL", :urakkanro "1"})} {:tyyppi :vesivayla-hoito, :hallintayksikko {:id 2, :nimi "Sisävesiväylät", :elynumero nil}, :urakat ({:id 40, :nimi "Pyhäselän urakka", :urakkanro "3"} {:id 41, :nimi "Rentoselän urakka", :urakkanro "4"})} {:tyyppi :tekniset-laitteet, :hallintayksikko {:id 8, :nimi "Pirkanmaa", :elynumero 4}, :urakat ({:id 30, :nimi "PIR RATU IHJU", :urakkanro "3007"})} {:tyyppi :paallystys, :hallintayksikko {:id 5, :nimi "Uusimaa", :elynumero 1}, :urakat ({:id 6, :nimi "Porvoon päällystysurakka", :urakkanro "por1"})} {:tyyppi :hoito, :hallintayksikko {:id 5, :nimi "Uusimaa", :elynumero 1}, :urakat ({:id 22, :nimi "Vantaan alueurakka 2009-2019", :urakkanro "131"} {:id 23, :nimi "Espoon alueurakka 2014-2019", :urakkanro "130"})} {:tyyppi :siltakorjaus, :hallintayksikko {:id 7, :nimi "Kaakkois-Suomi", :elynumero 3}, :urakat ({:id 29, :nimi "KAS siltojen ylläpidon palvelusopimus Etelä-Karjala", :urakkanro "5003"})} {:tyyppi :hoito, :hallintayksikko {:id 6, :nimi "Varsinais-Suomi", :elynumero 2}, :urakat ({:id 3, :nimi "Porin alueurakka 2007-2012", :urakkanro "pori666"})} {:tyyppi :vesivayla-kanavien-hoito, :hallintayksikko {:id 1, :nimi "Kanavat ja avattavat sillat", :elynumero nil}, :urakat ({:id 42, :nimi "Saimaan kanava", :urakkanro "089123"} {:id 43, :nimi "Joensuun kanava", :urakkanro "089123"})} {:tyyppi :tiemerkinta, :hallintayksikko {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero 12}, :urakat ({:id 12, :nimi "Oulun tiemerkinnän palvelusopimus 2013-2022", :urakkanro "OULU_TIE"})})]

    (is (= (poista-urakan-alue vastaus) odotettu-ilman-alueita))
    (is (>= (count elynumerot) 6)
        "JVH:n pitäisi nähdä kaikki ELY:t")))

(deftest hae-urakat-tilannekuvaan-urakanvalvoja
  (let [vastaus (hae-urakat-tilannekuvaan +kayttaja-tero+ hakuargumentit-laaja-historia)
        elynumerot (set (distinct (keep #(get-in % [:hallintayksikko :elynumero]) vastaus)))]
    (is (= (count elynumerot) 6)) "Urakanvalvojan pitäisi nähdä kaikki ELY:t"))

(deftest hae-urakat-tilannekuvaan-ei-nay-mitaan
  (let [vastaus (hae-urakat-tilannekuvaan +kayttaja-seppo+ hakuargumentit-laaja-historia)
        elynumerot (set (distinct (keep #(get-in % [:hallintayksikko :elynumero]) vastaus)))]

    (is (= (count elynumerot) 0))))

(deftest hae-urakat-tilannekuvaan-urakan-vastuuhenkilo-lisaoikeus
    ;; Käyttäjänä Oulun 2014 urakan vastuuhenkilö, jolla pitäisi olla Roolit-excelissä
    ;; erikoisoikeus oman-urakan-ely --> näkyvyys ELY:n kaikkiin urakoihin
    (let [vastaus (hae-urakat-tilannekuvaan (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia)
          elynumerot (set (distinct (keep #(get-in % [:hallintayksikko :elynumero]) vastaus)))
          eka-ely (first elynumerot)]

      (is (= eka-ely 12))
      (is (every? #(= % eka-ely) elynumerot)
          "Pääsy vain omaan urakkaan ja sen ELY:n urakoihin --> kaikki ELY-numerot tulee olla samoja")))

(deftest hae-urakat-tilannekuvaan-urakan-vastuuhenkilo-ilman-lisaoikeutta
    ;; Ilman lisäoikeutta näkyvyys vain omaan urakkaan
    (with-redefs [oikeudet/tilannekuva-historia {:roolien-oikeudet {"vastuuhenkilo" #{"R"}}}]
      (let [vastaus (hae-urakat-tilannekuvaan (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia)]
        (is (every?
              (fn [hy]
                (every?
                  (fn [u] (some? (:alue u)))
                  (:urakat hy)))
              vastaus))
        (is (= (mapv (fn [hy] (update hy :urakat (fn [urt] (into #{} (map #(assoc % :alue nil) urt))))) vastaus)
               [{:tyyppi :hoito
                 :hallintayksikko {:id 12
                                   :nimi "Pohjois-Pohjanmaa"
                                   :elynumero 12}
                 :urakat #{{:id 4
                            :nimi "Oulun alueurakka 2014-2019"
                            :urakkanro "1238"
                            :alue nil}}}])))))

(deftest hae-asiat-tilannekuvaan-urakan-vastuuhenkilo-lisaoikeudella-ja-ilman
    (let [urakat-ilman-lisaoikeutta
          ;; Ilman lisäoikeutta asiat tulee vain omasta urakasta
          (with-redefs [oikeudet/tilannekuva-historia {:roolien-oikeudet {"vastuuhenkilo" #{"R"}}}]
            (map :id (mapcat :urakat (hae-urakat-tilannekuvaan (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia))))
          urakat-lisaoikeudella ;; Oman urakan ELY -lisäoikeus pitäisi olla määritelty Roolit-excelissä
          (map :id (mapcat :urakat (hae-urakat-tilannekuvaan (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia)))]

      ;; Lisäoikeuden kanssa pitäisi asioita löytyä aina enemmän, koska haku useammasta urakasta

      (is (> (count urakat-lisaoikeudella) (count urakat-ilman-lisaoikeutta))
          "Lisäoikeudella pitää löytyä enemmän urakkavaihtoehtoja")

      (let [vastaus-ilman-lisaoikeutta
            ;; Ilman lisäoikeutta asiat tulee vain omasta urakasta
            (with-redefs [oikeudet/tilannekuva-historia {:roolien-oikeudet {"vastuuhenkilo" #{"R"}}}]
              (hae-tk (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia urakat-lisaoikeudella))
            vastaus-lisaoikeudella ;; Oman urakan ELY -lisäoikeus pitäisi olla määritelty Roolit-excelissä
            (hae-tk (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia urakat-lisaoikeudella)]

        (is (> (reduce + 0 (map (comp count val) vastaus-lisaoikeudella))
               (reduce + 0 (map (comp count val) vastaus-ilman-lisaoikeutta)))))))

(deftest hae-asiat-tilannekuvaan-urakan-vastuuhenkilo-liikaa-urakoita
  ;; Pyydetään hakemaan asiat tilannekuvaan kaikista urakoista, mutta saamme saman vastauksen kuin
  ;; haettaessa vain niistä urakoista, joihin käyttäjällä on hakuoikeus.
  ;; Tällä osoitetaan, että palvelu rajaa haettavat urakat vain niihin, joilla käyttäjällä on oikeus.
  (let [hyokkaus-vastaus (hae-tk (oulun-2014-urakan-urakoitsijan-urakkavastaava)
                                 hakuargumentit-laaja-historia
                                 (map :id (q-map "SELECT id FROM urakka")))
        normaali-vastaus (hae-tk (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia
                                 (map :id (mapcat :urakat (hae-urakat-tilannekuvaan (oulun-2014-urakan-urakoitsijan-urakkavastaava) hakuargumentit-laaja-historia))))]

    (is (= (reduce + 0 (map (comp count val) hyokkaus-vastaus))
           (reduce + 0 (map (comp count val) normaali-vastaus))))))

(deftest hae-tilaajan-laadunvalvonta
  (let [vastaus-tilaajalle
        (hae-tk (oulun-2014-urakan-tilaajan-urakanvalvoja) parametrit-laaja-nykytilanne)
        vastaus-urakoitsijalle
        (hae-tk (oulun-2014-urakan-urakoitsijan-urakkavastaava) parametrit-laaja-nykytilanne)]
    (is (contains? (get-in vastaus-tilaajalle [:tyokoneet :tehtavat])
                   #{"tilaajan laadunvalvonta"}))
    (is (not (contains? (get-in vastaus-urakoitsijalle [:tyokoneet :tehtavat])
                        #{"tilaajan laadunvalvonta"})))))


;; tuotannossa (ja singletonia vasten) tuotti bugin ERROR: Relate Operation called with a LWGEOMCOLLECTION type.  This is unsupported.
;                                     Hint: Change argument 2: 'GEOMETRYCOLLECTION(POINT(449110.781 6788879.145))'
#_(def haku-params-vhar-1815
  {:kuva [256.0 256.0], :extent [440848.0 6787072.0 454944.0 6882976.0], :resoluutio 16.0, :parametrit {:aikavalinta 504, "ind" "n1587995345871", :ilmoitukset {}, :yllapito #{17}, :urakat #{275 65 377 281 363 314 313 258 125 158 378 14 326 147 362}, :nykytilanne? true}})

;; Ei ole tarkoituskaan ajaa CI:ssä, vaan voi todeta ettei enää tule poikkeusta db-singletonia vasten
#_(deftest hae-paallystysten-reitit-kartalle-test
  (let [vastaus (<!! (hae-paallystysten-sijainnit-kartalle (:db harja.palvelin.main/harja-jarjestelma)
                                                           +kayttaja-jvh+
                                                           haku-params-vhar-1815))]))
