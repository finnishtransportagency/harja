(ns harja.palvelin.palvelut.turvallisuuspoikkeamat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.turvallisuuspoikkeamat :as tp]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-turvallisuuspoikkeamat (component/using
                                                      (tp/->Turvallisuuspoikkeamat)
                                                      [:http-palvelin :db])
                        :tallenna-turvallisuuspoikkeama (component/using
                                                          (tp/->Turvallisuuspoikkeamat)
                                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-turvallisuuspoikkeamat-test
  (let [urakka-id @oulun-alueurakan-2005-2010-id
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-turvallisuuspoikkeamat +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :alku (pvm/luo-pvm (+ 1900 105) 9 1)
                                 :loppu (pvm/luo-pvm (+ 1900 106) 8 30)})]
    (is (= (count vastaus) 1))
    (is (match vastaus [{:id _
                         :ilmoituksetlahetetty nil
                         :kasitelty nil
                         :kommentti {:tyyppi nil}
                         :korjaavattoimenpiteet []
                         :kuvaus "Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt."
                         :lahetetty nil
                         :lahetysonnistunut nil
                         :luotu _
                         :sairaalavuorokaudet 1
                         :sairauspoissaolopaivat 7
                         :sijainti {:coordinates [435847.0
                                                  7216217.0]
                                    :type :point}
                         :tapahtunut (_ :guard #(and (= (t/year (c/from-sql-date %)) 2005)
                                                     (= (t/month (c/from-sql-date %)) 9)
                                                     (= (t/day (c/from-sql-date %)) 30)))
                         :tila :avoin
                         :tr {:alkuetaisyys 6
                              :alkuosa 6
                              :loppuetaisyys 6
                              :loppuosa 6
                              :numero 6}
                         :tyontekijanammatti :porari
                         :tyontekijanammattimuu nil
                         :tyyppi #{:tyotapaturma}
                         :urakka urakka-id
                         :vaaralliset-aineet #{}
                         :vahingoittuneetruumiinosat nil
                         :vahinkoluokittelu #{}
                         :vammat nil}]
               true))))

(defn poista-tp-taulusta
  [kuvaus]
  (log/debug "Poistetaan testi-turpo")
  (let [id (ffirst (q (str "SELECT id FROM turvallisuuspoikkeama WHERE kuvaus='" kuvaus "'")))]
    (u (str "DELETE FROM korjaavatoimenpide WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama_kommentti WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama_liite WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama WHERE id=" id))))

(defn hae-uusin-turvallisuuspoikkeama []
  (as-> (first (q (str "SELECT
                        id,
                        urakka,
                        tapahtunut,
                        kasitelty,
                        sijainti,
                        kuvaus,
                        sairauspoissaolopaivat,
                        sairaalavuorokaudet,
                        tr_numero,
                        tr_alkuosa,
                        tr_alkuetaisyys,
                        tr_loppuosa,
                        tr_loppuetaisyys,
                        vahinkoluokittelu,
                        vakavuusaste,
                        tyyppi,
                        tyontekijanammatti,
                        tyontekijanammatti_muu,
                        aiheutuneet_seuraukset,
                        vammat,
                        vahingoittuneet_ruumiinosat,
                        sairauspoissaolo_jatkuu,
                        ilmoittaja_etunimi,
                        ilmoittaja_sukunimi,
                        vaylamuoto,
                        toteuttaja,
                        tilaaja,
                        turvallisuuskoordinaattori_etunimi,
                        turvallisuuskoordinaattori_sukunimi,
                        tapahtuman_otsikko,
                        paikan_kuvaus,
                        vaarallisten_aineiden_kuljetus,
                        vaarallisten_aineiden_vuoto
                        FROM turvallisuuspoikkeama
                        ORDER BY luotu DESC
                        LIMIT 1;")))
        turpo
        ;; Tapahtumapvm ja käsittely -> clj-time
        (assoc turpo 2 (c/from-sql-date (get turpo 2)))
        (assoc turpo 3 (c/from-sql-date (get turpo 3)))
        ;; Vahinkoluokittelu -> set
        (assoc turpo 13 (into #{} (when-let [arvo (get turpo 13)]
                                    (.getArray arvo))))
        ;; Tyyppi -> set
        (assoc turpo 15 (into #{} (when-let [arvo (get turpo 15)]
                                    (.getArray arvo))))))

(defn hae-korjaavat-toimenpiteet [turpo-id]
  (as-> (q (str "SELECT
                 kuvaus,
                 suoritettu,
                 otsikko,
                 vastuuhenkilo,
                 toteuttaja,
                 tila
                 FROM korjaavatoimenpide
                 WHERE turvallisuuspoikkeama = " turpo-id ";"))
        toimenpide
        (mapv #(assoc % 1 (c/from-sql-date (get % 1))) toimenpide)))

(deftest tallenna-turvallisuuspoikkeama-test
  (let [urakka-id @oulun-alueurakan-2005-2010-id
        tp {:urakka urakka-id
            :tapahtunut (pvm/luo-pvm (+ 1900 105) 6 1)
            :tyontekijanammatti :kuorma-autonkuljettaja
            :kuvaus "e2e taas punaisena"
            :vammat :luunmurtumat
            :sairauspoissaolopaivat 0
            :sairaalavuorokaudet 0
            :paikan-kuvaus "Metsätie"
            :vakavuusaste :lieva
            :vaylamuoto :tie
            :tyyppi #{:tyotapaturma}
            :otsikko "Kävi möhösti"
            :tila :suljettu
            :juurisyy1 :muu
            :vahinkoluokittelu #{:ymparistovahinko}
            :vaaralliset-aineet #{:vaarallisten-aineiden-kuljetus :vaarallisten-aineiden-vuoto}
            :sijainti {:type :point :coordinates [0 0]}
            :tr {:numero 1 :alkuetaisyys 2 :loppuetaisyys 3 :alkuosa 4 :loppuosa 5}}
        korjaavat-toimenpiteet [{:kuvaus "Ei ressata liikaa"
                                :otsikko "Ressi pois!"
                                :tila :avoin
                                :suoritettu nil
                                :vastaavahenkilo "Kaikki yhdessä"}]
        uusi-kommentti {:tekija "Teemu" :kommentti "Näin on!" :liite nil}
        hoitokausi [(pvm/luo-pvm (+ 1900 105) 9 1) (pvm/luo-pvm (+ 1900 106) 8 30)]
        hae-tp-maara (fn [] (ffirst (q "SELECT count(*) FROM turvallisuuspoikkeama;")))
        vanha-maara (hae-tp-maara)]

    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :tapahtunut :kasitelty :tyontekijanammatti :kuvaus
           :vammat :sairauspoissaolopaivat :sairaalavuorokaudet :sijainti :tyyppi
           [:tr :numero] [:tr :alkuetaisyys] [:tr :loppuetaisyys] [:tr :alkuosa] [:tr :loppuosa]]

          :tallenna-turvallisuuspoikkeama
          {:tp tp
           :korjaavattoimenpiteet korjaavat-toimenpiteet
           :uusi-kommentti uusi-kommentti
           :hoitokausi hoitokausi}))

    (is (= (hae-tp-maara) (+ 1 vanha-maara)))

    ;; Tarkistetaan, että data tallentui oikein
    (let [uusin-tp (hae-uusin-turvallisuuspoikkeama)
          turpo-id (first uusin-tp)
          turpon-korjaavat-toimenpiteet (hae-korjaavat-toimenpiteet turpo-id)]

      (is (not (empty? uusin-tp)))
      (is (= (nth uusin-tp 13) #{"ymparistovahinko"}))
      (is (= (nth uusin-tp 14) "lieva"))
      (is (= (nth uusin-tp 15) #{"tyotapaturma"}))
      (is (= (nth uusin-tp 16) "kuorma-autonkuljettaja"))
      (is (= (nth uusin-tp 29) "Kävi möhösti"))
      (is (= (count turpon-korjaavat-toimenpiteet) 1))
      (is (match (first turpon-korjaavat-toimenpiteet)
                 ["Ei ressata liikaa"
                  nil
                  "Ressi pois!"
                  nil
                  nil
                  "avoin"]
                 true)))

    ;; Turpon päivytys toimii myös
    (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-turvallisuuspoikkeama
                            +kayttaja-jvh+
                            {:tp (assoc tp :paikan-kuvaus "Luminen metsätie"
                                           :tapahtunut (pvm/luo-pvm (+ 1900 105) 9 1)
                                           :tyontekijanammatti :porari
                                           :kuvaus "e2e taas punaisena"
                                           :vammat :sokki
                                           :sairauspoissaolopaivat 0
                                           :sairaalavuorokaudet 0
                                           :vakavuusaste :vakava
                                           :vaylamuoto :tie
                                           :tyyppi #{:tyotapaturma}
                                           :otsikko "Kävi tosi möhösti"
                                           :tila :suljettu
                                           :vahinkoluokittelu #{:ymparistovahinko}
                                           :sijainti {:type :point :coordinates [0 0]}
                                           :vaaralliset-aineet #{}
                                           :tr {:numero 1 :alkuetaisyys 2 :loppuetaisyys 3 :alkuosa 4 :loppuosa 5})
                             :korjaavattoimenpiteet [{:kuvaus "Ei ressata yhtään"
                                                      :otsikko "Ressi pois vaan!"
                                                      :tila :avoin
                                                      :suoritettu nil
                                                      :vastaavahenkilo "Kaikki yhdessä"}]
                             :uusi-kommentti uusi-kommentti
                             :hoitokausi hoitokausi})
          uusin-tp (hae-uusin-turvallisuuspoikkeama)
          turpo-id (first uusin-tp)
          turpon-korjaavat-toimenpiteet (hae-korjaavat-toimenpiteet turpo-id)]

      (is (= (nth uusin-tp 29) "Kävi tosi möhösti"))
      (is (not (empty? uusin-tp)))
      (is (= (count turpon-korjaavat-toimenpiteet) 1))
      (is (match (first turpon-korjaavat-toimenpiteet)
                 ["Ei ressata yhtään"
                  nil
                  "Ressi pois vaan!"
                  nil
                  nil
                  "avoin"]
                 true)))

    (poista-tp-taulusta "e2e taas punaisena")))