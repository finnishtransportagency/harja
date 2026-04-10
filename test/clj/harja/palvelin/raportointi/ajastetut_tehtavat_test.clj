(ns harja.palvelin.raportointi.ajastetut-tehtavat-test
  "Testaa ajastetut_tehtavat-taulun lokituksen raportointitehtäville.
   Käyttää with-redefsiä ajasta-paivittain- ja yrita-ajaa-lukon-kanssa-funktioiden
   korvaamiseen, jotta funktion runko suoritetaan heti testissä ilman ajastusta tai lukkoja."
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.kyselyt.raportit :as raportit-q]
            [harja.kyselyt.pohjavesialueet :as pohjavesialueet-q]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn- suorita-heti
  "Korvaa ajasta-paivittain niin, että tehtävä suoritetaan välittömästi."
  [_ tehtava]
  (tehtava nil)
  (fn [] nil))

(defn- ohita-lukko
  "Ohittaa lukon tarkistuksen ja ajaa toiminnon suoraan. Lukot eivät kuulu näiden testien scopeen."
  [_db _tunniste toiminto-fn & _]
  (toiminto-fn))

(defn- tarkista-onnistuminen
  "Tarkistaa, että ajastetut_tehtavat-tauluun on kirjattu onnistunut suoritus."
  [tyyppi]
  (let [rivit (q-map (str "SELECT * FROM ajastetut_tehtavat WHERE tyyppi = '" tyyppi "'"))]
    (is (= 1 (count rivit)) "Onnistumisesta kirjataan yksi rivi.")
    (is (= true (:onnistunut (first rivit))) "Rivi merkitty onnistuneeksi.")
    (is (nil? (:virhe (first rivit))) "Onnistuneessa suorituksessa ei ole virhettä.")
    (is (not (nil? (:loppuaika_valilta (first rivit)))) "Loppuaika on tallentunut.")))

(defn- tarkista-epaonnistuminen
  "Tarkistaa, että ajastetut_tehtavat-tauluun on kirjattu epäonnistunut suoritus."
  [tyyppi]
  (let [rivit (q-map (str "SELECT * FROM ajastetut_tehtavat WHERE tyyppi = '" tyyppi "'"))]
    (is (= 1 (count rivit)) "Epäonnistumisesta kirjataan yksi rivi.")
    (is (= false (:onnistunut (first rivit))) "Rivi merkitty epäonnistuneeksi.")
    (is (not (nil? (:virhe (first rivit)))) "Virheviesti on tallentunut.")))

;; ---- paivita_raportti_toteutuneet_materiaalit ----

(deftest paivita-raportti-toteutuneet-materiaalit-lokittaa-onnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_toteutuneet_materiaalit'")
    (with-redefs [ajastettu-tehtava/ajasta-paivittain suorita-heti
                  lukot/yrita-ajaa-lukon-kanssa ohita-lukko]
      (raportointi/paivita_raportti_toteutuneet_materiaalit! db))
    (tarkista-onnistuminen "paivita_raportti_toteutuneet_materiaalit")))

(deftest paivita-raportti-toteutuneet-materiaalit-lokittaa-epaonnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_toteutuneet_materiaalit'")
    (with-redefs-fn
      {#'ajastettu-tehtava/ajasta-paivittain suorita-heti
       #'lukot/yrita-ajaa-lukon-kanssa ohita-lukko
       (ns-resolve 'harja.palvelin.raportointi
                   'paivita-kaynnissolevien-hoitourakoiden-materiaalicachet-eiliselta)
       (fn [_] :ohitettu)
       #'raportit-q/paivita_raportti_toteutuneet_materiaalit
       (fn [_] (throw (Exception. "Testi-virhe")))}
      #(raportointi/paivita_raportti_toteutuneet_materiaalit! db))
    (tarkista-epaonnistuminen "paivita_raportti_toteutuneet_materiaalit")))

;; ---- paivita_raportti_pohjavesialueiden_suolatoteumat ----

(deftest paivita-raportti-pohjavesialueiden-suolatoteumat-lokittaa-onnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_pohjavesialueiden_suolatoteumat'")
    (with-redefs [ajastettu-tehtava/ajasta-paivittain suorita-heti
                  lukot/yrita-ajaa-lukon-kanssa ohita-lukko]
      (raportointi/paivita_raportti_pohjavesialueiden_suolatoteumat! db))
    (tarkista-onnistuminen "paivita_raportti_pohjavesialueiden_suolatoteumat")))

(deftest paivita-raportti-pohjavesialueiden-suolatoteumat-lokittaa-epaonnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_pohjavesialueiden_suolatoteumat'")
    (with-redefs [ajastettu-tehtava/ajasta-paivittain suorita-heti
                  lukot/yrita-ajaa-lukon-kanssa ohita-lukko
                  pohjavesialueet-q/paivita-pohjavesialue-kooste (fn [_] :ohitettu)
                  raportit-q/paivita_raportti_pohjavesialueiden_suolatoteumat
                  (fn [_] (throw (Exception. "Testi-virhe")))]
      (raportointi/paivita_raportti_pohjavesialueiden_suolatoteumat! db))
    (tarkista-epaonnistuminen "paivita_raportti_pohjavesialueiden_suolatoteumat")))

;; ---- paivita_raportti_toteuma_maarat ----

(deftest paivita-raportti-toteuma-maarat-lokittaa-onnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_toteuma_maarat'")
    (with-redefs [ajastettu-tehtava/ajasta-paivittain suorita-heti
                  lukot/yrita-ajaa-lukon-kanssa ohita-lukko]
      (raportointi/paivita_raportti_toteuma_maarat! db))
    (tarkista-onnistuminen "paivita_raportti_toteuma_maarat")))

(deftest paivita-raportti-toteuma-maarat-lokittaa-epaonnistumisen
  (let [db (:db jarjestelma)]
    (u "DELETE FROM ajastetut_tehtavat WHERE tyyppi = 'paivita_raportti_toteuma_maarat'")
    (with-redefs [ajastettu-tehtava/ajasta-paivittain suorita-heti
                  lukot/yrita-ajaa-lukon-kanssa ohita-lukko
                  raportit-q/paivita_raportti_toteuma_maarat
                  (fn [_] (throw (Exception. "Testi-virhe")))]
      (raportointi/paivita_raportti_toteuma_maarat! db))
    (tarkista-epaonnistuminen "paivita_raportti_toteuma_maarat")))
