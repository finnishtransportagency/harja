(ns harja.views.urakka.paallystysilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is async]]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.historia :as historia]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.tierekisteri :as tierekisteri-domain]
    [harja.ui.tierekisteri :as tierekisteri]
    [harja.testutils.shared-testutils :refer [render paivita sel sel1 grid-solu click change
                                              disabled? ilman-tavutusta komponentti-fixture]]
    [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                             jvh-fixture]]
    [harja.views.urakka.paallystysilmoitukset :as p]
    [harja.pvm :as pvm]
    [reagent.core :as r]
    [cljs.core.async :refer [<! >!]]
    [cljs-react-test.simulate :as sim]
    [schema.core :as s]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(test/use-fixtures :each komponentti-fixture fake-palvelut-fixture jvh-fixture)

(deftest tien-pituus-laskettu-oikein
  (let [tie1 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys 5}
        tie2 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 5 :tr-loppuetaisyys 5}
        tie3 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys -100}
        tie4 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1 :tr-loppuetaisyys 2}
        tie5 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 0 :tr-loppuetaisyys 1}
        tie6 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1}]
    (is (= (tierekisteri-domain/laske-tien-pituus tie1) 2))
    (is (= (tierekisteri-domain/laske-tien-pituus tie2) 0))
    (is (= (tierekisteri-domain/laske-tien-pituus tie3) 103))
    (is (= (tierekisteri-domain/laske-tien-pituus tie4) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie5) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie6) nil))))

(def paallystysilmoituslomake-alkutila
  {:tila :aloitettu
   :muutoshinta 666
   :kohdenimi nil
   :kohdenumero "666"
   :yllapitokohde-id "777"
   :kommentit []
   :tr-numero 20
   :tr-alkuosa 1
   :tr-alkuetaisyys 1
   :tr-loppuosa 5
   :tr-loppuetaisyys 100
   :valmispvm-kohde nil
   :sopimuksen-mukaiset-tyot nil

   :kaasuindeksi nil
   :aloituspvm nil
   :paallystyskohde-id 20
   :bitumi-indeksi nil
   :id 8
   :takuupvm nil
   :ilmoitustiedot {:osoitteet [{;; Alikohteen tiedot
                                 :tr-numero 20
                                 :tr-alkuosa 1 :tr-alkuetaisyys 1
                                 :tr-loppuosa 3 :tr-loppuetaisyys 42
                                 :kohdeosa-id 30 :tr-kaista 1 :tr-ajorata 0
                                 :tunnus "p" :nimi "piru 1"
                                 :toimenpide "eka?"
                                 ;; Päällystetoimenpiteen tiedot
                                 :toimenpide-tyomenetelma 12}
                                {;; Alikohteen tiedot
                                 :tr-numero 20
                                 :tr-alkuosa 3 :tr-alkuetaisyys 42
                                 :tr-loppuosa 3 :tr-loppuetaisyys 123
                                 :kohdeosa-id 32 :tr-kaista nil
                                 :tr-ajorata nil
                                 :toimenpide "toka!"
                                 :tunnus "y" :nimi "piru 1.5"
                                 ;; Päällystetoimenpiteen tiedot
                                 :toimenpide-tyomenetelma 12
                                 ; Kiviaines- ja sideainetiedot
                                 :lisaaineet "huono tyyri"
                                 :sideainetyyppi 13 :muotoarvo "1"
                                 :esiintyma "kovasti kovestä" :pitoisuus 44
                                 :km-arvo "9"}
                                {;; Alikohteen tiedot
                                 :tr-alkuosa 3 :tr-alkuetaisyys 123 :tr-numero 20
                                 :tr-loppuosa 5 :tr-loppuetaisyys 100
                                 :kohdeosa-id 31 :tr-kaista 26 :tr-ajorata 2
                                 :tunnus "k"
                                 :nimi "piru 2"
                                 :raekoko 6
                                 :paallystetyyppi 1
                                 :toimenpide "ja kolomas"
                                 :tyomenetelma 12
                                 ;; Päällystetoimenpiteen tiedot
                                 :massamenekki 42 :toimenpide-raekoko 66
                                 :toimenpide-paallystetyyppi 1 :toimenpide-tyomenetelma 12}]
                    :alustatoimet []}
   :asiatarkastus {:lisatiedot nil :tarkastusaika nil :tarkastaja nil}
   :arvonvahennykset nil
   :tekninen-osa {:paatos nil :asiatarkastus nil :kasittelyaika nil :perustelu nil}
   :valmispvm-paallystys nil
   :kokonaishinta 0})

(defn tarkista-asiatarkastus [lomake]
  (go

    (is (not (sel1 :.pot-asiatarkastus)) "Asiatarkastus ei näkyvissä vielä")

    (swap! lomake assoc
           :tila :valmis
           :valmispvm-kohde (pvm/nyt))

    (<! (paivita))

    (is (some? (sel1 :.pot-asiatarkastus)) "Asiatarkastus näkyvissä")))

(defn tarkista-kopioidun-rivin-virhe [lomake]
  (go
    (let [tila @lomake]

      ;; Muokataan toiseksi viimeisen osoiterivin loppua siten, että se menee viimeisin
      ;; rivin loppukohda yli. Tämän pitäisi tehdä virhe viimeiselle riville.
      (change (grid-solu "yllapitokohdeosat" 1 8 "input") "5") ;; losa
      (<! (paivita))

      (change (grid-solu "yllapitokohdeosat" 1 9 "input") "123") ;; let
      (<! (paivita))

      (is (= (ilman-tavutusta (first (get-in @lomake [:virheet :alikohteet 3 :tr-alkuetaisyys])))
             "Alkuetäisyys ei voi olla loppuetäisyyden jälkeen"))

      (is (disabled? :#tallenna-paallystysilmoitus))

      (reset! lomake tila)
      (<! (paivita))

      (is (nil? (get-in @lomake [:virheet :alikohteet 3 :tr-alkuetaisyys]))))))

(deftest paallystysilmoituslomake
  (let [urakka {:id 1}
        lukko nil
        lomake (r/atom paallystysilmoituslomake-alkutila)
        historia (historia/historia lomake)
        _ (historia/kuuntele! historia)
        comp (fn []
               [p/paallystysilmoituslomake urakka @lomake lukko (partial swap! lomake) historia])
        pituudet-haettu (fake-palvelukutsu :hae-tr-osien-pituudet (constantly {1 1000
                                                                               2 2000
                                                                               3 3000
                                                                               4 3900
                                                                               5 400}))
        _ (fake-palvelukutsu :hae-lukko-idlla (constantly :ei-lukittu))
        _ (fake-palvelukutsu :lukitse (constantly {:id "paallystysilmoitus_777",
                                                   :kayttaja 2, :aikaleima (pvm/nyt)}))
        tallennus (fake-palvelukutsu :tallenna-paallystysilmoitus identity)]
    (async
      done
      (go
        (render [comp])

        ;; Tarkista, että tieosoite näkyy oikein
        (is (= "piru 1"
               (some-> (grid-solu "yllapitokohdeosat" 0 1)
                       .-value)))

        (<! pituudet-haettu)

        (<! (paivita))

        (is (= "Tierekisterikohteiden pituus yhteensä: 10,00 km"
               (some-> (sel1 :#kohdeosien-pituus-yht) .-innerText)))


        ;; Tallennus nappi enabled
        (is (some-> (sel1 :#tallenna-paallystysilmoitus) .-disabled not))

        (let [tila-ok @lomake]

          ;; Muutetaan päällystystoimenpiteen RC% arvoksi ei-validi
          (sim/change (grid-solu "paallystysilmoitus-paallystystoimenpiteet" 0 4)
                      {:target {:value "888"}})

          (<! (paivita))

          (is (= 888 (get-in @lomake [:ilmoitustiedot :osoitteet 0 :rc%])))

          (is (= (get-in @lomake [:virheet :paallystystoimenpide 1 :rc%])
                 '("Anna arvo välillä 0 - 100")))

          ;; Tallennus nappi disabled
          (is (some-> (sel1 :#tallenna-paallystysilmoitus) .-disabled))

          (<! (tarkista-asiatarkastus lomake))
          (reset! lomake tila-ok))

        (<! (paivita))

        ;; Muutetaan takaisin validiksi ja yritetään tallentaa
        (sim/change (grid-solu "paallystysilmoitus-paallystystoimenpiteet" 0 4)
                    {:target {:value "8"}})

        (<! (paivita))

        (<! (tarkista-kopioidun-rivin-virhe lomake))

        (click :#tallenna-paallystysilmoitus)

        ;; Tallennusta kutsuttu
        (let [tulos (<! tallennus)]
          (is tulos))
        (done)))))
