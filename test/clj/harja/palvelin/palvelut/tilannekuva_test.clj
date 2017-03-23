(ns harja.palvelin.palvelut.tilannekuva-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tilannekuva :refer :all]
            [harja.palvelin.palvelut.interpolointi :as interpolointi]
            [harja.testi :refer :all]
            [harja.paneeliapurit :as paneeli]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.tilannekuva :as tk]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :karttakuvat (component/using
                        (karttakuvat/luo-karttakuvat)
                        [:http-palvelin :db])
          :toteumat (component/using
                     (->Tilannekuva)
                      [:http-palvelin :db :karttakuvat])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def alku (c/to-date (t/local-date 2000 1 1)))
(def loppu (c/to-date (t/local-date 2030 1 1)))
(def urakoitsija nil)
(def urakkatyyppi :hoito)

(def parametrit-laaja-historia
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
                  tk/tarkastus-laatu true}
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

(def parametrit-laaja-nykytilanne (assoc parametrit-laaja-historia :nykytilanne? true))

(defn aseta-filtterit-falseksi [parametrit ryhma]
  (assoc parametrit ryhma (reduce
                            (fn [eka toka]
                              (assoc eka toka false))
                            (ryhma parametrit)
                            (keys (ryhma parametrit)))))

(defn hae-tk
  ([parametrit] (hae-tk +kayttaja-jvh+ parametrit))
  ([kayttaja parametrit]
   (let [urakat (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakat-tilannekuvaan kayttaja
                                {:nykytilanne? (:nykytilanne? parametrit)
                                 :alku         (:alku parametrit)
                                 :loppu        (:loppu parametrit)
                                 :urakoitsija  (:urakoitsija parametrit)
                                 :urakkatyyppi (:urakkatyyppi parametrit)})
         urakat (into #{} (mapcat
                           (fn [aluekokonaisuus]
                             (map :id (:urakat aluekokonaisuus)))
                           urakat))]
     (kutsu-palvelua (:http-palvelin jarjestelma)
                     :hae-tilannekuvaan kayttaja
                     (tk/valitut-suodattimet (assoc parametrit
                                                    :urakat urakat))))))

(deftest hae-asioita-tilannekuvaan
  (let [vastaus (hae-tk parametrit-laaja-historia)]
    (is (>= (count (:toteumat vastaus)) 1))
    ;; Testaa, että toteuma selitteissä on enemmän kuin 1 toimenpidekoodi
    (is (> (count (distinct (map :toimenpidekoodi (:toteumat vastaus)))) 1))
    (is (>= (count (:turvallisuuspoikkeamat vastaus)) 1))
    (is (not (contains? vastaus :tarkastus)))
    (is (>= (count (:laatupoikkeamat vastaus)) 1))
    (is (>= (count (:paikkaus vastaus)) 1))
    (is (>= (count (:paallystys vastaus)) 1))
    (is (>= (count (:ilmoitukset vastaus)) 1))))

(deftest ala-hae-laatupoikkeamia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :laatupoikkeamat)
        vastaus (hae-tk parametrit)]
    (is (= (count (:laatupoikkeamat vastaus)) 0))))

(deftest ala-hae-toteumia
  (let [parametrit (-> parametrit-laaja-historia
                       (aseta-filtterit-falseksi :kesa)
                       (aseta-filtterit-falseksi :talvi))
        vastaus (hae-tk parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

;; Urakkatyyppi ei vaikuta enää hakutuloksiin
(deftest urakkatyyppi-filter-toimii
  (let [parametrit (assoc parametrit-laaja-historia :urakkatyyppi :paallystys)
        vastaus (hae-tk parametrit)]
    (is (= (count (:toteumat vastaus)) 3))))

(deftest ala-hae-tarkastuksia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :tarkastukset)
        vastaus (hae-tk parametrit)]
    (is (= (count (:tarkastukset vastaus)) 0))))

(deftest ala-hae-turvallisuuspoikkeamia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :turvallisuus)
        vastaus (hae-tk parametrit)]
    (is (= (count (:turvallisuus vastaus)) 0))))

(deftest ala-hae-ilmoituksia
  (let [parametrit (assoc parametrit-laaja-historia :ilmoitukset {:tyypit {:toimenpidepyynto false
                                                                           :kysely           false
                                                                           :tiedoitus        false}
                                                                  :tilat  #{:avoimet :suljetut}})
        vastaus (hae-tk parametrit)]
    (is (= (count (:ilmoitukset vastaus)) 0))))

(deftest ala-hae-tyokoneita-historianakymaan
  (let [vastaus (hae-tk parametrit-laaja-historia)]
    (is (= (count (:tyokoneet vastaus)) 0))))

(deftest loyda-vahemman-asioita-tiukalla-aikavalilla
  (let [vastaus-pitka-aikavali (hae-tk parametrit-laaja-historia)
        parametrit (-> parametrit-laaja-historia
                       (assoc :alku (c/to-date (t/local-date 2005 1 1)))
                       (assoc :loppu (c/to-date (t/local-date 2010 1 1))))
        vastaus-lyhyt-aikavali (hae-tk parametrit)]
    (is (< (count (:toteumat vastaus-lyhyt-aikavali))
           (count (:toteumat vastaus-pitka-aikavali))))))

(deftest hae-tyokoneet-nykytilaan
  (let [parametrit (assoc parametrit-laaja-historia :nykytilanne? true)
        vastaus (hae-tk parametrit)]
    ;; Työkonetehtäviä löytyi
    (is (not (empty? (:tehtavat (:tyokoneet vastaus)))))))

(deftest ala-hae-toteumia-liian-lahelle-zoomatussa-historianakymassa
  (let [parametrit (assoc parametrit-laaja-historia :alue {:xmin 0,
                                                           :ymin 0,
                                                           :xmax 1,
                                                           :ymax 1})
        vastaus (hae-tk parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

(deftest ala-hae-tyokoneita-liian-lahelle-zoomatussa-nykytilannenakymassa
  (let [parametrit (-> parametrit-laaja-historia
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
                 "ST_MakePoint(" x ", " y ")::POINT, "
                 (if urakka urakka "NULL") ", '{harjaus}')")]
    (u sql)))


(deftest vain-tilaaja-ja-urakoitsija-itse-nakee-urakattomat-tyokoneet
  (let [parametrit (assoc parametrit-laaja-historia :nykytilanne? true)
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
    (let [vastaus (hae-tk parametrit-laaja-historia)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tietyomaa (:tietyomaat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? (map
                                                       #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))
                                                       (:ilmoitukset vastaus))))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille?
            :paallystys
            (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf (:paallystys vastaus))))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille?
            :paikkaus
            (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf (:paikkaus vastaus))))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :laatupoikkeama (:laatupoikkeamat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :turvallisuuspoikkeama (:turvallisuuspoikkeamat vastaus)))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille? :tietyoilmoitus (:tietyoilmoitukset vastaus)))))

  (testing "Päällystys / paikkaus haku nykytilanteeseen"
    ;; Käyttää eri SQL-kyselyä historian ja nykytilanteen hakuun, joten hyvä testata erikseen vielä nykytilanne
    (let [vastaus (hae-tk parametrit-laaja-nykytilanne)]
      (is (paneeli/skeeman-luonti-onnistuu-kaikille?
            :paallystys
            (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf (:paallystys vastaus))))
      (is (paneeli/skeeman-luonti-onnistuu-kaikille?
            :paikkaus
            (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf (:paikkaus vastaus))))))

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
